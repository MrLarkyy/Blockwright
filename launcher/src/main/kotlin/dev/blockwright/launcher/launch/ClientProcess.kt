package dev.blockwright.launcher.launch

import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A running client and the owner of its process tree. [stop] (or [close]) closes the client's stdin, which the agent
 * turns into a graceful Minecraft stop, waits the stop timeout, then kills the tree, keeps the logs in the artefact
 * directory and removes the run directory. If the owner never stops it, a JVM shutdown hook kills the tree and
 * removes the run directory. If the owning JVM dies without hooks, the agent stops the client itself: stdin
 * reaches EOF and the parent-pid watchdog fires.
 */
class ClientProcess private constructor(
    private val process: Process,
    val runDir: Path,
    val artifactDir: Path,
    private val stopTimeout: Duration,
    private val keepRunDir: Boolean,
    private val statusToken: String,
) : AutoCloseable {
    val pid: Long = process.pid()
    val consoleLog: Path = artifactDir.resolve("console.log")

    private val stdin: OutputStream = process.outputStream
    private val pump = OutputPump(process.inputStream, consoleLog)
    private val states = ConcurrentHashMap<String, CompletableFuture<AgentStatusLine>>()
    private val shutdownHook = Thread(::onShutdown, "blockwright-client-shutdown")
    private val cleanupLock = Any()
    private var cleanup: Cleanup? = null
    private var result: ClientRunResult? = null

    @Volatile private var hookInstalled = false

    internal val shutdownHookInstalled: Boolean get() = hookInstalled

    val isAlive: Boolean get() = process.isAlive

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        hookInstalled = true
        pump.addListener { line -> AgentStatusLine.parse(line, statusToken)?.let { state(it.state).complete(it) } }
        pump.start()
    }

    /** Client output from now on, line by line (console thread: keep the listener short). */
    fun onLine(listener: (String) -> Unit) = pump.addListener(listener)

    /** The status line once the agent reported [state], if it did. */
    fun status(state: String): AgentStatusLine? = state(state).getNow(null)

    /**
     * Waits until the client joined the server. A refused join or a client that gave up starting (`failed`, e.g. no
     * usable GPU backend) or an exit fails at once with the client's reason; a timeout fails with the last console
     * lines. On failure the client is stopped and the result attached.
     */
    fun awaitJoined(timeout: Duration): AgentStatusLine =
        awaitState(AgentStatusLine.JOINED, timeout, failOn = setOf(AgentStatusLine.FAILED))

    /** Waits for any agent [state]; the states in [failOn] and an exit fail as in [awaitJoined]. */
    fun awaitState(state: String, timeout: Duration, failOn: Set<String> = setOf(AgentStatusLine.FAILED, AgentStatusLine.DISCONNECTED)): AgentStatusLine {
        val target = state(state)
        val failures = failOn.map(::state)
        try {
            CompletableFuture.anyOf(target, process.onExit(), *failures.toTypedArray()).get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw failure("the client did not report '$state' within $timeout; last lines:\n${tailText()}")
        } catch (e: ExecutionException) {
            throw failure("waiting for '$state' failed: ${e.cause?.message}")
        }
        target.getNow(null)?.let { return it }
        failures.firstNotNullOfOrNull { it.getNow(null) }?.let { line ->
            throw failure("the client reported '${line.state}' before '$state': ${line["reason"] ?: "no reason"}")
        }
        pump.join(PUMP_DRAIN)
        throw failure("the client exited with code ${process.exitValue()} before '$state'; last lines:\n${tailText()}")
    }

    @Synchronized
    fun stop(): ClientRunResult {
        result?.let { return it }
        val interrupted = AtomicBoolean(false)
        val descendants = runCatching { process.descendants().toList() }.getOrDefault(emptyList())
        var forced = false
        if (process.isAlive) {
            runCatching { stdin.close() }
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
        return ClientRunResult(exitCode, forced, consoleLog, done.latestLog, runDir, done.runDirKept).also { result = it }
    }

    override fun close() {
        stop()
    }

    /**
     * What the JVM shutdown hook runs when the owner never called [stop]: the same kill and cleanup without the
     * graceful wait. Not synchronized: a [stop] blocked in its graceful wait must not stall the JVM exit.
     */
    internal fun onShutdown() {
        val descendants = runCatching { process.descendants().toList() }.getOrDefault(emptyList())
        finish(descendants, SHUTDOWN_DRAIN, AtomicBoolean(false))
    }

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
            if (keepRunDir) runCatching { RunDirectory.markKept(runDir) }
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

    private fun failure(message: String): ClientStartException = ClientStartException(message, stop())

    private fun state(name: String): CompletableFuture<AgentStatusLine> = states.computeIfAbsent(name) { CompletableFuture() }

    private fun tailText(): String = pump.tail().joinToString("\n").ifEmpty { "(no output)" }

    private fun copyLatestLog(): Path? {
        val latest = runDir.resolve(GAME_DIR).resolve("logs").resolve("latest.log")
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
        const val GAME_DIR = "game"
        private val PUMP_DRAIN = 5.seconds
        private val SHUTDOWN_DRAIN = 2.seconds

        /**
         * Starts [command] in [runDir]; only status lines carrying [statusToken] count. If anything fails after the
         * start, nothing is left running.
         */
        internal fun start(
            starter: ClientProcessStarter,
            command: List<String>,
            runDir: Path,
            artifactDir: Path,
            stopTimeout: Duration,
            keepRunDir: Boolean,
            statusToken: String,
        ): ClientProcess {
            Files.createDirectories(artifactDir)
            val process = starter.start(command, runDir)
            return try {
                RunDirectory.recordClient(runDir, process.toHandle())
                ClientProcess(process, runDir, artifactDir, stopTimeout, keepRunDir, statusToken)
            } catch (e: Throwable) {
                ProcessTree.kill(process)
                throw e
            }
        }
    }
}
