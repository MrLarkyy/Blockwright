package dev.blockwright.agent.lifecycle

import dev.blockwright.agent.AgentStatus

/**
 * A client started by the launcher has nobody to answer Minecraft's blocking error boxes: one would hang it until the
 * launcher's timeout (seen when no GPU backend could be created). Each box's message becomes an `alert` status line
 * instead, and a window or GPU backend that cannot be created, fatal before the join, a `failed` line, so the launcher
 * fails at once with Minecraft's reason. [status] is null for a client the launcher did not start: nothing changes.
 */
class StartupFailures(private val status: () -> AgentStatus?) {
    /** True when the box must not be shown. */
    fun replacesErrorBox(message: String?): Boolean {
        val out = status() ?: return false
        out.emit(ALERT, mapOf("reason" to (message ?: NO_MESSAGE)))
        return true
    }

    fun onWindowInitFailed(message: String?) {
        status()?.emit(FAILED, mapOf("reason" to (message ?: NO_MESSAGE)))
    }

    companion object {
        const val ALERT = "alert"
        const val FAILED = "failed"
        private const val NO_MESSAGE = "no message"
    }
}
