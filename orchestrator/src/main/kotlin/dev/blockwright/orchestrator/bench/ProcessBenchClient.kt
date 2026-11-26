package dev.blockwright.orchestrator.bench

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Speaks the line protocol: READY, SHOT -> "SHOT ok", LOAD on|off, QUIT. */
class ProcessBenchClient(private val process: Process) : BenchClient {
    private val lines = LinkedBlockingQueue<String>()
    private val writer = process.outputStream.bufferedWriter()
    private val closed = AtomicBoolean(false)

    override val pid: Long = process.pid()

    init {
        ChildProcessReaper.track(process)
        // Stdout is drained continuously so the child never blocks on a full pipe.
        thread(isDaemon = true, name = "bench-stdout-$pid") {
            runCatching { process.inputStream.bufferedReader().forEachLine(lines::put) }
            lines.put(EOF)
        }
    }

    override fun awaitReady(timeout: Duration) = await("READY", timeout)

    override fun screenshot(timeout: Duration) {
        send("SHOT")
        await("SHOT ok", timeout)
    }

    override fun startLoad() = send("LOAD on")

    override fun stopLoad() = send("LOAD off")

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val descendants = process.descendants().toList()
        try {
            runCatching { send("QUIT") }
            runCatching { writer.close() }
            if (!process.waitFor(GRACE.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                process.destroy()
                process.waitFor(GRACE.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }
        } finally {
            descendants.forEach { it.destroyForcibly() }
            process.destroyForcibly()
            process.waitFor(GRACE.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            ChildProcessReaper.untrack(process)
        }
    }

    @Synchronized
    private fun send(command: String) {
        try {
            writer.write(command)
            writer.newLine()
            writer.flush()
        } catch (e: IOException) {
            throw BenchException("client $pid is not accepting '$command'", e)
        }
    }

    private fun await(expected: String, timeout: Duration) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            val left = timeout - mark.elapsedNow()
            if (left <= Duration.ZERO) throw BenchException("client $pid: timed out after $timeout waiting for '$expected'")
            val line = lines.poll(left.inWholeMilliseconds.coerceAtLeast(1), TimeUnit.MILLISECONDS) ?: continue
            if (line == EOF) {
                lines.put(EOF)
                throw BenchException("client $pid exited before '$expected'")
            }
            if (line == expected) return
        }
    }

    private companion object {
        const val EOF = "\u0000EOF"
        val GRACE = 3.seconds
    }
}
