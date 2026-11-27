package dev.blockwright.agent

/**
 * What the agent cuts in this client, from the `blockwright.profile` system property the launcher sets.
 * [LOGIC] runs without display, GPU, audio and render assets; [REFERENCE] is the same agent with every cut off (the
 * parity baseline); [NONE] (no property) leaves the client untouched.
 */
enum class AgentProfile(val property: String?) {
    LOGIC("logic"),
    REFERENCE("reference"),
    NONE(null),
    ;

    val cutsActive: Boolean get() = this == LOGIC

    companion object {
        const val PROPERTY = "blockwright.profile"

        /** An unknown value fails: a typo must not silently run an unmodified client in a measured profile. */
        fun parse(value: String?): AgentProfile {
            if (value.isNullOrBlank()) return NONE
            return entries.firstOrNull { it.property == value.trim() }
                ?: throw IllegalArgumentException("unknown $PROPERTY '$value', expected logic or reference")
        }

        fun current(): AgentProfile = parse(System.getProperty(PROPERTY))
    }
}
