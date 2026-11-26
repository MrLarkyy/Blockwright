package dev.blockwright.orchestrator.bench

import java.util.concurrent.ConcurrentHashMap

/**
 * Kills tracked client processes and their children when the JVM exits. The hook leaves the bw-bench-* temp
 * dir alone: deleting it there would race with a concurrent close().
 */
object ChildProcessReaper {
    private val tracked: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ reapAll() }, "bench-reaper"))
    }

    fun track(process: Process) {
        tracked.add(process)
    }

    fun untrack(process: Process) {
        tracked.remove(process)
    }

    internal fun reapAll() {
        tracked.forEach { process ->
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }
}
