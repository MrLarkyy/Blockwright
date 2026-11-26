package dev.blockwright.orchestrator.bench

import java.util.concurrent.ConcurrentHashMap

/** Kills tracked child processes when the JVM exits, so an interrupted run leaves nothing behind. */
object ChildProcessReaper {
    private val tracked: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ tracked.forEach(::kill) }, "bench-reaper"))
    }

    fun track(process: Process) {
        tracked.add(process)
    }

    fun untrack(process: Process) {
        tracked.remove(process)
    }

    private fun kill(process: Process) {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }
}
