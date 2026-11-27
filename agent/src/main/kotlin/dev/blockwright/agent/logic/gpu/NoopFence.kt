package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.commands.GpuFence

/** Always complete: no command ever runs on a GPU. */
object NoopFence : GpuFence {
    override fun close() {
        // Nothing to release.
    }

    override fun awaitCompletion(timeoutNanos: Long): Boolean = true
}
