package dev.blockwright.agent.display

import dev.blockwright.agent.AgentStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minecraft's OpenGL backend asks SDL for an sRGB-capable framebuffer and gives up when the display has none: Mesa's
 * software GLX (Xvfb, Xorg dummy: the Linux CI reference) offers no sRGB-capable configuration at all. Only after
 * that window failed, in a client the launcher started, this creates it once more without the sRGB request, so the
 * reference still runs; colours may differ, nothing else does. Where Minecraft's own window works nothing changes.
 * The first fallback is reported as an `alert` status line. [status] is null for a client the launcher did not start.
 */
class SrgbFallback(
    private val srgbAttribute: Int,
    private val openGlWindowFlag: Long,
    private val setAttribute: (Int, Int) -> Boolean,
    private val createWindow: (String, Int, Int, Long) -> Long,
    private val status: () -> AgentStatus?,
) {
    private val reported = AtomicBoolean(false)

    /** The new window, or 0 to keep Minecraft's failure. */
    fun retryWithoutSrgb(title: String, width: Int, height: Int, flags: Long): Long {
        val out = status() ?: return NO_WINDOW
        setAttribute(srgbAttribute, 0)
        val window = createWindow(title, width, height, flags or openGlWindowFlag)
        if (window != NO_WINDOW && reported.compareAndSet(false, true)) {
            out.emit("alert", mapOf("reason" to "no sRGB-capable OpenGL framebuffer; the window renders without one"))
        }
        return window
    }

    companion object {
        const val NO_WINDOW = 0L
    }
}
