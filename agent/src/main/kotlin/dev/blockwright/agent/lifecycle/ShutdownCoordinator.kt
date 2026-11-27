package dev.blockwright.agent.lifecycle

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The one way the agent ends the client, whoever asks (stdin EOF, parent death, later BW-14's disconnect): the first
 * [request] asks the client to stop gracefully and arms a fallback that halts the JVM if it is still alive after
 * [fallback]; later requests do nothing. Without a client to stop (still starting) the JVM halts at once.
 *
 * [stopClient] returns false when there is no client yet; [halt] must not return in production
 * (`Runtime.halt`), [onRequest] reports the reason before anything else happens.
 */
class ShutdownCoordinator(
    private val stopClient: () -> Boolean,
    private val halt: (Int) -> Unit = { Runtime.getRuntime().halt(it) },
    private val fallback: Duration = 10.seconds,
    private val onRequest: (String) -> Unit = {},
) {
    private val requested = AtomicBoolean(false)

    val isRequested: Boolean get() = requested.get()

    /** True for the request that started the shutdown. */
    fun request(reason: String): Boolean {
        if (!requested.compareAndSet(false, true)) return false
        runCatching { onRequest(reason) }
        val delivered = runCatching(stopClient).getOrDefault(false)
        if (!delivered) {
            halt(EXIT_WITHOUT_CLIENT)
            return true
        }
        Thread.ofPlatform().daemon().name("blockwright-shutdown-fallback").start {
            try {
                Thread.sleep(fallback.inWholeMilliseconds)
                halt(EXIT_FALLBACK)
            } catch (_: InterruptedException) {
                // Only a test interrupts the fallback; a real JVM exit simply ends this daemon thread.
            }
        }
        return true
    }

    companion object {
        /** No client existed yet: nothing to save, the stop was clean. */
        const val EXIT_WITHOUT_CLIENT = 0

        /** The client did not stop within the fallback and was halted. */
        const val EXIT_FALLBACK = 70
    }
}
