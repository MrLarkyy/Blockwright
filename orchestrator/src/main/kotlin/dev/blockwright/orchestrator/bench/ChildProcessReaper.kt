package dev.blockwright.orchestrator.bench

import java.util.concurrent.ConcurrentHashMap

/**
 * Kills tracked client processes and their children when the JVM exits. The hook leaves the bw-bench-* temp
 * dir alone: deleting it there would race with a concurrent close(). A hard kill of the bench JVM runs no hook;
 * real clients therefore also stop themselves when their parent dies (stdin EOF, parent-pid watchdog).
 */
object ChildProcessReaper {
    private val tracked: MutableSet<ProcessHandle> = ConcurrentHashMap.newKeySet()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ reapAll() }, "bench-reaper"))
    }

    fun track(process: Process) = track(process.toHandle())

    fun track(handle: ProcessHandle) {
        tracked.add(handle)
    }

    fun untrack(process: Process) = untrack(process.toHandle())

    fun untrack(handle: ProcessHandle) {
        tracked.remove(handle)
    }

    internal fun reapAll() {
        tracked.forEach { handle ->
            handle.descendants().forEach { it.destroyForcibly() }
            handle.destroyForcibly()
        }
    }
}
