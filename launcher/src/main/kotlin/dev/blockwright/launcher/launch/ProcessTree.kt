package dev.blockwright.launcher.launch

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Force-kills a process and its descendants; the snapshot catches children that outlive their parent. Every kill is
 * sent before the first wait, so an interrupted wait (InterruptedException) still leaves nothing alive.
 */
object ProcessTree {
    fun kill(process: Process, descendants: List<ProcessHandle> = emptyList(), wait: Long = 10, unit: TimeUnit = TimeUnit.SECONDS) {
        val all = (descendants + process.descendants().toList()).distinctBy { it.pid() }
        all.forEach { it.destroyForcibly() }
        process.destroyForcibly()
        process.waitFor(wait, unit)
        all.forEach { handle ->
            // Only a timeout or failure is ignored; an interrupt reaches the caller, every kill was already sent.
            try {
                handle.onExit().get(wait, unit)
            } catch (_: TimeoutException) {
            } catch (_: ExecutionException) {
            }
        }
    }
}
