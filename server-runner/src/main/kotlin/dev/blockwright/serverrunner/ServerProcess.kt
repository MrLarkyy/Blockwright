package dev.blockwright.serverrunner

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A running Paper server and the owner of its process tree: [stop] (or [close]) ends it gracefully, kills it with
 * its descendants after the stop timeout, keeps the logs in the artefact directory and removes the run directory.
 * A JVM shutdown hook kills the tree if the owner never stops it.
 */
class ServerProcess private constructor(
    private val process: Process,
    val port: Int,
    val runDir: Path,
    val artifactDir: Path,
    private val stopTimeout: Duration,
    private val keepRunDir: Boolean,
) : AutoCloseable {
    val pid: Long = process.pid()
    val consoleLog: Path = artifactDir.resolve("console.log")

    private val stdin = process.outputWriter(Charsets.UTF_8)
    private val stdinLock = Any()
    private val pump = ConsoleLogPump(process.inputStream, consoleLog)
    private val ready = CompletableFuture<Unit>()
    private val shutdownHook = Thread({ ProcessTree.kill(process, wait = 5) }, "blockwright-paper-shutdown")

    @Volatile private var stopping = false

    @Volatile private var hookInstalled = false
    private var result: ServerRunResult? = null

    internal val shutdownHookInstalled: Boolean get() = hookInstalled

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        hookInstalled = true
        pump.addListener { line -> if (ReadinessDetector.isReady(line)) ready.complete(Unit) }
        pump.start()
    }

    /** Server output from now on, line by line (console thread: keep the listener short). */
    fun onLine(listener: (String) -> Unit) = pump.addListener(listener)

    /** Sends one console command to the server's stdin. */
    fun console(command: String) {
        require(command.none { it == '\n' || it == '\r' }) { "a console command is a single line" }
        check(!stopping) { "the server is stopped" }
        try {
            writeLine(command)
        } catch (e: IOException) {
            throw ServerRunnerException("the server console is closed", e)
        }
    }

    @Synchronized
    fun stop(): ServerRunResult {
        result?.let { return it }
        stopping = true
        val descendants = runCatching { process.descendants().toList() }.getOrDefault(emptyList())
        var forced = false
        if (process.isAlive) {
            runCatching { writeLine("stop") }
            forced = !process.waitFor(stopTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
        ProcessTree.kill(process, descendants)
        runCatching { stdin.close() }
        pump.join(PUMP_DRAIN)
        val exitCode = if (process.isAlive) null else process.exitValue()
        val latestLog = copyLatestLog()
        val runDirKept = keepRunDir || !RunDirectory.delete(runDir)
        removeShutdownHook()
        return ServerRunResult(exitCode, forced, consoleLog, latestLog, runDir, runDirKept).also { result = it }
    }

    override fun close() {
        stop()
    }

    private fun awaitReady(timeout: Duration) {
        try {
            CompletableFuture.anyOf(ready, process.onExit()).get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw ServerStartException("the Paper server did not report ready within $timeout; last lines:\n${tailText()}")
        }
        if (ready.isDone) return
        pump.join(PUMP_DRAIN)
        throw ServerStartException("the Paper server exited with code ${process.exitValue()} before it was ready; last lines:\n${tailText()}")
    }

    private fun writeLine(line: String) = synchronized(stdinLock) {
        stdin.write(line)
        stdin.newLine()
        stdin.flush()
    }

    private fun tailText(): String = pump.tail().joinToString("\n").ifEmpty { "(no output)" }

    private fun copyLatestLog(): Path? {
        val latest = runDir.resolve("logs").resolve("latest.log")
        if (!Files.isRegularFile(latest)) return null
        return try {
            Files.copy(latest, artifactDir.resolve("latest.log"), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            null
        }
    }

    private fun removeShutdownHook() {
        // Fails only while the JVM is already shutting down, when the hook runs anyway.
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
        hookInstalled = false
    }

    companion object {
        private val PUMP_DRAIN = 5.seconds

        /** Launches [command] in [runDir] and waits for readiness; on any failure nothing is left running. */
        internal fun start(
            launcher: ProcessLauncher,
            command: List<String>,
            runDir: Path,
            artifactDir: Path,
            port: Int,
            readyTimeout: Duration,
            stopTimeout: Duration,
            keepRunDir: Boolean,
        ): ServerProcess {
            Files.createDirectories(artifactDir)
            val process = launcher.launch(command, runDir)
            val server = try {
                ServerProcess(process, port, runDir, artifactDir, stopTimeout, keepRunDir)
            } catch (e: Throwable) {
                ProcessTree.kill(process)
                throw e
            }
            try {
                server.awaitReady(readyTimeout)
            } catch (e: Throwable) {
                server.stop()
                throw e
            }
            return server
        }
    }
}
