package dev.blockwright.agent

import java.io.PrintStream

/**
 * The status lines the launcher reads from the client's stdout: `BLOCKWRIGHT_AGENT state=<state> key=value ...`.
 * Values never contain whitespace or `=` (replaced by `_`) and are capped, so a server-provided disconnect reason
 * cannot forge another field or line. [out] is the process stdout captured before Minecraft redirects System.out
 * into its logger.
 */
class AgentStatus(private val out: PrintStream) {
    fun emit(state: String, fields: Map<String, Any?> = emptyMap()) {
        val line = format(state, fields)
        synchronized(out) {
            out.println(line)
            out.flush()
        }
    }

    companion object {
        const val PREFIX = "BLOCKWRIGHT_AGENT"
        private const val MAX_VALUE = 200
        private val UNSAFE = Regex("[\\s=]+")

        fun format(state: String, fields: Map<String, Any?> = emptyMap()): String = buildString {
            append(PREFIX).append(" state=").append(clean(state))
            fields.forEach { (key, value) -> append(' ').append(clean(key)).append('=').append(clean(value.toString())) }
        }

        private fun clean(value: String): String =
            value.replace(UNSAFE, "_").filterNot { it.isISOControl() }.take(MAX_VALUE).ifEmpty { "_" }
    }
}
