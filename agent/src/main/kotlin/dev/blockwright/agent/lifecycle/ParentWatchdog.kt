package dev.blockwright.agent.lifecycle

/**
 * Calls [onExit] on the watchdog's own daemon thread when the process [parentPid] ends, or at once when it is already
 * gone. Covers a parent killed without hooks whose stdin pipe is inherited by another process and therefore never
 * reaches EOF. Polls instead of `ProcessHandle.onExit()`, whose callbacks run on the JDK's process reaper thread: its
 * small stack overflows when the shutdown loads classes through Fabric's transformer.
 */
class ParentWatchdog(
    private val parentPid: Long,
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
    private val onExit: () -> Unit,
) {
    fun start() {
        // A process cannot be its own parent; that pid can only be a misconfiguration.
        if (parentPid == ProcessHandle.current().pid()) return
        val parent = ProcessHandle.of(parentPid).orElse(null)
        if (parent == null || !parent.isAlive) {
            onExit()
            return
        }
        Thread.ofPlatform().daemon().name(THREAD_NAME).start { watch(parent) }
    }

    private fun watch(parent: ProcessHandle) {
        try {
            while (parent.isAlive) Thread.sleep(pollMillis)
        } catch (_: InterruptedException) {
            return
        }
        onExit()
    }

    companion object {
        const val PROPERTY = "blockwright.parentPid"
        const val THREAD_NAME = "blockwright-parent-watchdog"
        private const val DEFAULT_POLL_MILLIS = 500L

        fun parsePid(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it > 0 }
    }
}
