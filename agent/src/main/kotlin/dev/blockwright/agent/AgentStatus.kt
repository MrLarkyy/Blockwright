package dev.blockwright.agent

import java.io.PrintStream

/**
 * The status lines the launcher reads from the client's stdout: `BLOCKWRIGHT_AGENT <token> state=<state> key=value ...`.
 * The token is the launch's secret from `-Dblockwright.statusToken`; the launcher accepts only lines that start with
 * the prefix and that token, so server text in the console (chat, a kick reason, also after an embedded newline)
 * cannot pose as a status. Values never contain whitespace or `=` (replaced by `_`) and are capped. [out] is the
 * process stdout captured before Minecraft redirects System.out into its logger.
 */
class AgentStatus(private val out: PrintStream, private val token: String? = null) {
    fun emit(state: String, fields: Map<String, Any?> = emptyMap()) {
        val line = format(state, fields, token)
        synchronized(out) {
            out.println(line)
            out.flush()
        }
    }

    companion object {
        const val PREFIX = "BLOCKWRIGHT_AGENT"
        const val TOKEN_PROPERTY = "blockwright.statusToken"
        private const val MAX_VALUE = 200
        private val UNSAFE = Regex("[\\s=]+")

        /** Without a token (a client not started by the launcher) the line carries `_`, which no launcher accepts. */
        fun format(state: String, fields: Map<String, Any?> = emptyMap(), token: String? = null): String = buildString {
            append(PREFIX).append(' ').append(clean(token.orEmpty())).append(" state=").append(clean(state))
            fields.forEach { (key, value) -> append(' ').append(clean(key)).append('=').append(clean(value.toString())) }
        }

        private fun clean(value: String): String =
            value.replace(UNSAFE, "_").filterNot { it.isISOControl() }.take(MAX_VALUE).ifEmpty { "_" }
    }
}
