package dev.blockwright.serverrunner

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A running Paper server and the owner of its process tree: [stop] (or [close]) ends it gracefully, kills it with
 * its descendants after the stop timeout, keeps the logs in the artefact directory and removes the run directory.
 * If the owner never stops it, a JVM shutdown hook kills the tree, keeps latest.log and removes the run directory;
 * a hard kill of the owning JVM runs no hook and can leave both behind.
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
    private val shutdownHook = Thread(::onShutdown, "blockwright-paper-shutdown")
    private val cleanupLock = Any()
    private var cleanup: Cleanup? = null

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
        val interrupted = AtomicBoolean(false)
        val descendants = runCatching { process.descendants().toList() }.getOrDefault(emptyList())
        var forced = false
        if (process.isAlive) {
            runCatching { writeLine("stop") }
            forced = try {
                !process.waitFor(stopTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                // An interrupted owner still gets a clean end: no graceful wait, straight to the kill.
                interrupted.set(true)
                true
            }
        }
        val done = finish(descendants, PUMP_DRAIN, interrupted)
        val exitCode = if (process.isAlive) null else process.exitValue()
        removeShutdownHook()
        if (interrupted.get()) Thread.currentThread().interrupt()
        return ServerRunResult(exitCode, forced, consoleLog, done.latestLog, runDir, done.runDirKept).also { result = it }
    }

    /**
     * What the JVM shutdown hook runs when the owner never called [stop]: the same kill and cleanup without the
     * graceful wait. Not synchronized: a [stop] blocked in its graceful wait must not stall the JVM exit.
     */
    internal fun onShutdown() {
        stopping = true
        val descendants = runCatching { process.descendants().toList() }.getOrDefault(emptyList())
        finish(descendants, SHUTDOWN_DRAIN, AtomicBoolean(false))
    }

    /** Kills the tree, drains the console and, once, keeps latest.log and removes the run directory. */
    private fun finish(descendants: List<ProcessHandle>, drain: Duration, interrupted: AtomicBoolean): Cleanup {
        try {
            ProcessTree.kill(process, descendants)
        } catch (_: InterruptedException) {
            interrupted.set(true)
        }
        runCatching { stdin.close() }
        try {
            pump.join(drain)
        } catch (_: InterruptedException) {
            interrupted.set(true)
        }
        synchronized(cleanupLock) {
            cleanup?.let { return it }
            val latestLog = copyLatestLog()
            val runDirKept = keepRunDir || try {
                !RunDirectory.delete(runDir)
            } catch (_: InterruptedException) {
                interrupted.set(true)
                Files.exists(runDir)
            }
            return Cleanup(latestLog, runDirKept).also { cleanup = it }
        }
    }

    private class Cleanup(val latestLog: Path?, val runDirKept: Boolean)

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
        private val SHUTDOWN_DRAIN = 2.seconds

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
