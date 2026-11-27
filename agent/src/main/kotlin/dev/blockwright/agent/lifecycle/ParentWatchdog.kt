package dev.blockwright.agent.lifecycle

/**
 * Calls [onExit] when the process [parentPid] ends, or at once when it is already gone. Covers a parent killed
 * without hooks whose stdin pipe is inherited by another process and therefore never reaches EOF.
 */
class ParentWatchdog(private val parentPid: Long, private val onExit: () -> Unit) {
    fun start() {
        // A process cannot await its own exit (onExit throws); that pid can only be a misconfiguration.
        if (parentPid == ProcessHandle.current().pid()) return
        val parent = ProcessHandle.of(parentPid).orElse(null)
        if (parent == null || !parent.isAlive) {
            onExit()
            return
        }
        parent.onExit().thenRun(onExit)
    }

    companion object {
        const val PROPERTY = "blockwright.parentPid"

        fun parsePid(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it > 0 }
    }
}
