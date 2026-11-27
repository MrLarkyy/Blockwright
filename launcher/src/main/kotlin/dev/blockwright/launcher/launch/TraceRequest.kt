package dev.blockwright.launcher.launch

import java.nio.file.Path

/** Asks the agent to play the built-in input [script] after the join and write the per-tick movement to [output]. */
data class TraceRequest(val output: Path, val script: String = WALK_JUMP_STRAFE) {
    init {
        require(SCRIPT.matches(script)) { "invalid trace script name" }
    }

    companion object {
        const val WALK_JUMP_STRAFE = "walk-jump-strafe"

        /** Ticks the agent's built-in `walk-jump-strafe` script plays (one trace line each). */
        const val WALK_JUMP_STRAFE_TICKS = 110
        private val SCRIPT = Regex("[a-z0-9-]{1,40}")
    }
}
