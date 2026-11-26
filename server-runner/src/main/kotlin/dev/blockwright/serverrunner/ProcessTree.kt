package dev.blockwright.serverrunner

import java.util.concurrent.TimeUnit

/** Force-kills a process and its descendants; the snapshot catches children that outlive their parent. */
object ProcessTree {
    fun kill(process: Process, descendants: List<ProcessHandle> = emptyList(), wait: Long = 10, unit: TimeUnit = TimeUnit.SECONDS) {
        val all = (descendants + process.descendants().toList()).distinctBy { it.pid() }
        all.forEach { it.destroyForcibly() }
        process.destroyForcibly()
        process.waitFor(wait, unit)
        all.forEach { handle -> runCatching { handle.onExit().get(wait, unit) } }
    }
}
