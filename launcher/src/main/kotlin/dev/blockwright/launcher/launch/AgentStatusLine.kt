package dev.blockwright.launcher.launch

/**
 * One status line of the agent (`BLOCKWRIGHT_AGENT state=joined key=value ...`), found anywhere in a console line
 * so a logger prefix does not hide it. Values never contain whitespace (the agent replaces it with `_`).
 */
data class AgentStatusLine(val state: String, val fields: Map<String, String>) {
    operator fun get(key: String): String? = fields[key]

    companion object {
        const val PREFIX = "BLOCKWRIGHT_AGENT "
        const val JOINED = "joined"
        const val FAILED = "failed"
        const val DISCONNECTED = "disconnected"
        const val TRACED = "traced"

        fun parse(line: String): AgentStatusLine? {
            val start = line.indexOf(PREFIX)
            if (start < 0) return null
            val fields = line.substring(start + PREFIX.length).trim().split(' ')
                .mapNotNull { token -> token.indexOf('=').takeIf { it > 0 }?.let { token.substring(0, it) to token.substring(it + 1) } }
                .toMap()
            val state = fields["state"] ?: return null
            return AgentStatusLine(state, fields - "state")
        }
    }
}
