package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.backend.common.BaseGpuBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A buffer without GPU memory. A mapping hands out fresh direct memory of the mapped length (callers may take its
 * native address); what is written there is dropped on close, nothing ever reads it back.
 */
class NoopBuffer(usage: Int, size: Long) : BaseGpuBuffer(usage, size) {
    @Volatile private var closed = false

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }

    override fun map(offset: Long, length: Long, read: Boolean, write: Boolean): GpuBufferSlice.MappedView =
        GpuBufferSlice.MappedView(GpuBufferSlice(this, offset, length), allocate(length)) {}

    companion object {
        fun allocate(length: Long): ByteBuffer =
            ByteBuffer.allocateDirect(Math.toIntExact(length)).order(ByteOrder.nativeOrder())
    }
}
