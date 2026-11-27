package dev.blockwright.agent.trace

/**
 * A fixed, per-tick sequence of held movement keys, identical for every client that runs it. The built-in
 * `walk-jump-strafe` settles, walks, jumps, strafes, sprints and stops within 110 ticks and stays well inside
 * 20 m of the start, so it never leaves the chunks loaded at spawn.
 */
class InputScript(val name: String, private val ticks: List<ScriptedInput>) {
    val length: Int get() = ticks.size

    fun at(tick: Int): ScriptedInput? = ticks.getOrNull(tick)

    companion object {
        const val PROPERTY = "blockwright.trace.script"

        val WALK_JUMP_STRAFE = InputScript(
            "walk-jump-strafe",
            buildList {
                repeat(20) { add(ScriptedInput()) }
                repeat(30) { add(ScriptedInput(forward = true)) }
                repeat(10) { add(ScriptedInput(forward = true, jump = true)) }
                repeat(20) { add(ScriptedInput(left = true)) }
                repeat(10) { add(ScriptedInput(forward = true, sprint = true)) }
                repeat(10) { add(ScriptedInput(right = true, jump = true)) }
                repeat(10) { add(ScriptedInput()) }
            },
        )

        private val BUILT_IN = listOf(WALK_JUMP_STRAFE).associateBy { it.name }

        fun named(name: String?): InputScript =
            if (name.isNullOrBlank()) WALK_JUMP_STRAFE
            else BUILT_IN[name] ?: throw IllegalArgumentException("unknown $PROPERTY '$name', known: ${BUILT_IN.keys}")
    }
}
