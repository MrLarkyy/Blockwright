package dev.blockwright.orchestrator.bench

import dev.blockwright.launcher.launch.ClientProcess
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * A real client under the bench: ready means joined. It has no screenshot and no load generation yet (BW-14 adds the
 * control channel), so those metrics are not run. Closing stops it gracefully and untracks it from the reaper.
 */
class ClientBenchClient(
    override val pid: Long,
    private val awaitJoined: (Duration) -> Unit,
    private val stop: () -> Unit,
) : BenchClient {
    private val closed = AtomicBoolean(false)

    // Kept, not looked up again: after the exit the pid no longer resolves and could even be reused.
    private val handle: ProcessHandle? = ProcessHandle.of(pid).orElse(null)

    override val supportsLoad: Boolean get() = false

    init {
        handle?.let(ChildProcessReaper::track)
    }

    override fun awaitReady(timeout: Duration) = awaitJoined(timeout)

    override fun screenshot(timeout: Duration) = throw BenchException("client $pid cannot take screenshots yet")

    override fun startLoad() = throw BenchException("client $pid cannot generate load yet")

    override fun stopLoad() {
        // No load was started.
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            stop()
        } finally {
            handle?.let(ChildProcessReaper::untrack)
        }
    }

    companion object {
        fun of(process: ClientProcess) = ClientBenchClient(process.pid, { process.awaitJoined(it) }, { process.stop() })
    }
}
