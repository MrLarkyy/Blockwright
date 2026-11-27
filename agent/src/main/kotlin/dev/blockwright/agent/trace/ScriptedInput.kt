package dev.blockwright.agent.trace

/** The movement keys held during one client tick of an [InputScript]. */
data class ScriptedInput(
    val forward: Boolean = false,
    val backward: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
    val shift: Boolean = false,
    val sprint: Boolean = false,
)
