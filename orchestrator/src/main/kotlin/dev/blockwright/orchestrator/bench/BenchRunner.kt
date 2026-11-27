package dev.blockwright.orchestrator.bench

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Measures one profile: starts the configured number of clients, keeps them all up for the PSS, CPU and
 * screenshot samples, then relaunches for warm starts. Every client and the work dir are released on any exit.
 */
class BenchRunner(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val pss: (Long) -> Measurement = PssProbe()::measure,
    private val cpu: (Long) -> Duration? = CpuProbe()::totalCpu,
    private val pause: (Duration) -> Unit = { Thread.sleep(it.inWholeMilliseconds) },
) {
    fun run(name: String, config: ProfileBudget, target: BenchTarget): ProfileResult {
        val workDir = Files.createTempDirectory("bw-bench-")
        val clients = mutableListOf<BenchClient>()
        try {
            return ProfileResult(name, config, true, measure(config, target, workDir, clients))
        } finally {
            clients.forEach { runCatching { it.close() } }
            deleteQuietly(workDir)
        }
    }

    private fun measure(
        config: ProfileBudget,
        target: BenchTarget,
        workDir: Path,
        clients: MutableList<BenchClient>,
    ): Map<Metric, Measurement> {
        val timeout = config.startTimeoutSeconds.seconds
        val starts = mutableListOf<Double>()
        try {
            target.prepare()
            repeat(config.clients) { starts += start(target, workDir, timeout, clients) }
        } catch (e: Exception) {
            return startFailure(starts, e)
        }
        val warm = starts.drop(1).toMutableList()
        val running = metricsWhileRunning(config, clients, timeout)
        try {
            repeat(config.warmRuns) { warm += start(target, workDir, timeout, clients).also { clients.removeAt(clients.lastIndex).close() } }
        } catch (e: Exception) {
            return running + (Metric.WarmStart to failed(e)) + (Metric.ColdStart to value(starts.first()))
        }
        val warmStart = if (warm.isEmpty()) Measurement.NotRun("no warm sample") else value(median(warm), warm)
        return running + (Metric.ColdStart to value(starts.first())) + (Metric.WarmStart to warmStart)
    }

    private fun metricsWhileRunning(config: ProfileBudget, clients: List<BenchClient>, timeout: Duration) = mapOf(
        Metric.Pss to attempt { Measurement.combine(clients.map { pss(it.pid) }) { it.max() } },
        Metric.CpuIdle to attempt { cpuWindow(clients, config.idleSeconds.seconds, load = false) },
        Metric.CpuActive to
            if (clients.all { it.supportsLoad }) attempt { cpuWindow(clients, config.activeSeconds.seconds, load = true) }
            else Measurement.NotRun("the client cannot generate load yet"),
        Metric.ScreenshotP95 to
            if (config.screenshots == 0) Measurement.NotRun("screenshots: 0 in the budgets file")
            else attempt { screenshots(clients.first(), config.screenshots, timeout) },
    )

    private fun start(target: BenchTarget, workDir: Path, timeout: Duration, clients: MutableList<BenchClient>): Double {
        val mark = timeSource.markNow()
        val client = target.launch(workDir)
        clients += client
        client.awaitReady(timeout)
        return mark.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
    }

    private fun cpuWindow(clients: List<BenchClient>, length: Duration, load: Boolean): Measurement {
        if (load) clients.forEach { it.startLoad() }
        try {
            val before = clients.map { cpu(it.pid) }
            val mark = timeSource.markNow()
            pause(length)
            val wall = mark.elapsedNow()
            val after = clients.map { cpu(it.pid) }
            return Measurement.combine(before.indices.map { CpuProbe.window(before[it], after[it], wall) }) { it.max() }
        } finally {
            if (load) clients.forEach { runCatching { it.stopLoad() } }
        }
    }

    private fun screenshots(client: BenchClient, count: Int, timeout: Duration): Measurement {
        val samples = List(count) {
            val mark = timeSource.markNow()
            client.screenshot(timeout)
            mark.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
        }
        return value(percentile95(samples), samples)
    }

    private fun startFailure(starts: List<Double>, error: Exception): Map<Metric, Measurement> {
        val reason = Measurement.Failed("client did not start: ${error.message ?: error.javaClass.simpleName}")
        val cold = starts.firstOrNull()?.let(::value) ?: reason
        return Metric.entries.associateWith { reason } + (Metric.ColdStart to cold)
    }

    private fun attempt(block: () -> Measurement): Measurement = try {
        block()
    } catch (e: BenchException) {
        failed(e)
    } catch (e: IOException) {
        failed(e)
    }

    private fun failed(error: Exception) = Measurement.Failed(error.message ?: error.javaClass.simpleName)

    private fun value(single: Double) = Measurement.Value(listOf(single), single)

    private fun value(aggregate: Double, samples: List<Double>) = Measurement.Value(samples, aggregate)

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun deleteQuietly(dir: Path) {
        runCatching { dir.deleteRecursively() }
    }

    companion object {
        fun median(samples: List<Double>): Double {
            val sorted = samples.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }

        /** Nearest-rank percentile. */
        fun percentile95(samples: List<Double>): Double {
            require(samples.isNotEmpty()) { "no samples for a percentile" }
            return samples.sorted()[ceil(0.95 * samples.size).toInt() - 1]
        }
    }
}
