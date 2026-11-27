package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.commands.GpuQueryPool
import java.util.OptionalLong

/** Timestamp queries that never produce a value. */
class NoopQueryPool(private val size: Int) : GpuQueryPool {
    override fun size(): Int = size

    override fun getValue(index: Int): OptionalLong = OptionalLong.empty()

    override fun getValues(index: Int, count: Int): Array<OptionalLong> = Array(count) { OptionalLong.empty() }

    override fun close() {
        // Nothing to release.
    }
}
