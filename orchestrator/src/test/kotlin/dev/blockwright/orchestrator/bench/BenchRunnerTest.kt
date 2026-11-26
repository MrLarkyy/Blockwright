package dev.blockwright.orchestrator.bench

import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BenchRunnerTest {
    private val time = TestTimeSource()
    private var cpuClock = Duration.ZERO

    private class FakeClient(
        override val pid: Long,
        private val time: TestTimeSource,
        private val readyCost: Duration,
        private val failReady: Boolean,
        private val shotCost: Duration,
    ) : BenchClient {
        var closed = false
        var loads = 0

        override fun awaitReady(timeout: Duration) {
            time += readyCost
            if (failReady) throw BenchException("never ready")
        }

        override fun screenshot(timeout: Duration) {
            time += shotCost
        }

        override fun startLoad() {
            loads++
        }

        override fun stopLoad() = Unit

        override fun close() {
            closed = true
        }
    }

    private inner class FakeTarget(private val failOn: Int = -1) : BenchTarget {
        override val profile = "fake"
        val clients = mutableListOf<FakeClient>()
        var workDir: Path? = null
        private var shot = 0

        override fun launch(workDir: Path): BenchClient {
            this.workDir = workDir
            val index = clients.size
            val cost = ((index + 1) * 100).milliseconds
            val client = FakeClient(index + 1L, time, cost, index == failOn, (++shot * 10).milliseconds)
            clients += client
            return client
        }
    }

    private fun runner(pss: (Long) -> Measurement = { Measurement.Value(listOf(it.toDouble()), it.toDouble()) }) =
        BenchRunner(time, pss, { cpuClock }, { time += it; cpuClock += it / 2 })

    private val config = ProfileBudget(clients = 2, warmRuns = 2, idleSeconds = 1, activeSeconds = 1, screenshots = 10)

    private fun value(result: ProfileResult, metric: Metric) =
        assertInstanceOf(Measurement.Value::class.java, result.metrics.getValue(metric))

    @Test
    fun aggregatesColdWarmPssCpuAndScreenshots() {
        val target = FakeTarget()
        val result = runner().run("fake", config, target)
        assertEquals(100.0, value(result, Metric.ColdStart).aggregate)
        assertEquals(listOf(200.0, 300.0, 400.0), value(result, Metric.WarmStart).samples)
        assertEquals(300.0, value(result, Metric.WarmStart).aggregate)
        assertEquals(2.0, value(result, Metric.Pss).aggregate)
        assertEquals(50.0, value(result, Metric.CpuIdle).aggregate)
        assertEquals(50.0, value(result, Metric.CpuActive).aggregate)
        assertEquals(2, target.clients.take(2).sumOf { it.loads })
        assertEquals(10, value(result, Metric.ScreenshotP95).samples.size)
    }

    @Test
    fun screenshotMetricIsTheNearestRankP95() {
        assertEquals(100.0, BenchRunner.percentile95((1..10).map { it * 10.0 }))
        assertEquals(20.0, BenchRunner.percentile95(listOf(20.0, 10.0)))
        assertEquals(2.0, BenchRunner.median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, BenchRunner.median(listOf(1.0, 2.0, 3.0, 4.0)))
    }

    @Test
    fun clientFailingToStartFailsItsMetricsAndEverythingIsReleased() {
        val target = FakeTarget(failOn = 1)
        val result = runner().run("fake", config, target)
        assertEquals(100.0, value(result, Metric.ColdStart).aggregate)
        assertInstanceOf(Measurement.Failed::class.java, result.metrics.getValue(Metric.Pss))
        assertInstanceOf(Measurement.Failed::class.java, result.metrics.getValue(Metric.WarmStart))
        assertEquals(2, target.clients.size)
        assertTrue(target.clients.all { it.closed })
        assertFalse(Files.exists(target.workDir!!))
    }

    @Test
    fun allClientsAreClosedAndTheWorkDirRemovedOnSuccess() {
        val target = FakeTarget()
        runner().run("fake", config, target)
        assertEquals(4, target.clients.size)
        assertTrue(target.clients.all { it.closed })
        assertFalse(Files.exists(target.workDir!!))
    }

    @Test
    fun allClientsAreClosedWhenAProbeThrows() {
        val target = FakeTarget()
        assertThrows<IllegalStateException> { runner(pss = { error("probe broke") }).run("fake", config, target) }
        assertTrue(target.clients.isNotEmpty())
        assertTrue(target.clients.all { it.closed })
        assertFalse(Files.exists(target.workDir!!))
    }

    @Test
    fun singleClientWithoutRelaunchesHasNoWarmSample() {
        val result = runner().run("fake", ProfileBudget(idleSeconds = 1, activeSeconds = 1, screenshots = 1), FakeTarget())
        assertInstanceOf(Measurement.NotRun::class.java, result.metrics.getValue(Metric.WarmStart))
    }
}
