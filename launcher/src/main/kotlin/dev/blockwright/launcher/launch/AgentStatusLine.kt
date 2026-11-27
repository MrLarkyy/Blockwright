package dev.blockwright.launcher.launch

import java.security.SecureRandom
import java.util.HexFormat

/**
 * One status line of the agent: `BLOCKWRIGHT_AGENT <token> state=joined key=value ...` at the start of a console line.
 * The token is a random value of this launch only, passed to the agent as `-Dblockwright.statusToken`. Text a server
 * gets into the client's console (chat, a kick reason, also after an embedded newline) cannot know it, so it cannot
 * forge a state. Values never contain whitespace (the agent replaces it with `_`).
 */
data class AgentStatusLine(val state: String, val fields: Map<String, String>) {
    operator fun get(key: String): String? = fields[key]

    companion object {
        const val PREFIX = "BLOCKWRIGHT_AGENT "
        const val TOKEN_PROPERTY = "blockwright.statusToken"
        const val JOINED = "joined"
        const val FAILED = "failed"
        const val DISCONNECTED = "disconnected"
        const val TRACED = "traced"
        private const val TOKEN_BYTES = 16
        private val TOKEN = Regex("[0-9a-f]{${TOKEN_BYTES * 2}}")
        private val random = SecureRandom()

        fun newToken(): String = HexFormat.of().formatHex(ByteArray(TOKEN_BYTES).also(random::nextBytes))

        /** Null unless [line] starts with the prefix and exactly this launch's [token]. */
        fun parse(line: String, token: String): AgentStatusLine? {
            require(TOKEN.matches(token)) { "invalid status token" }
            val head = "$PREFIX$token "
            if (!line.startsWith(head)) return null
            val fields = line.substring(head.length).trim().split(' ')
                .mapNotNull { field -> field.indexOf('=').takeIf { it > 0 }?.let { field.substring(0, it) to field.substring(it + 1) } }
                .toMap()
            val state = fields["state"] ?: return null
            return AgentStatusLine(state, fields - "state")
        }
    }
}
