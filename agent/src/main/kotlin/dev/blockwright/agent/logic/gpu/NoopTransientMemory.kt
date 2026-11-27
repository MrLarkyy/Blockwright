package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.buffers.TransientMemory
import java.nio.ByteBuffer

/** Per-frame scratch memory: CPU views are real direct memory, GPU slices point at buffers without memory. */
class NoopTransientMemory : TransientMemory {
    override fun allocateCpu(size: Long, alignment: Long, unused1: Long, unused2: Long): ByteBuffer = NoopBuffer.allocate(size)

    override fun allocateStaging(size: Long, alignment: Long, usage: Int, unused1: Long, unused2: Long): GpuBufferSlice.MappedView =
        NoopBuffer(usage or GpuBuffer.USAGE_MAP_WRITE, size).map(0, size, false, true)

    override fun allocateGpu(size: Long, alignment: Long, usage: Int, unused1: Long, unused2: Long): GpuBufferSlice =
        NoopBuffer(usage, size).slice()

    override fun allocateGpuMapped(size: Long, alignment: Long, usage: Int, unused1: Long, unused2: Long): GpuBufferSlice.MappedView =
        NoopBuffer(usage or GpuBuffer.USAGE_MAP_WRITE, size).map(0, size, false, true)

    override fun uploadStaging(data: List<ByteBuffer>, alignment: Long, usage: Int, unused1: Long, unused2: Long): GpuBufferSlice =
        NoopBuffer(usage, data.sumOf { it.remaining().toLong() }).slice()

    override fun uploadGpu(data: List<ByteBuffer>, alignment: Long, usage: Int, unused1: Long, unused2: Long): GpuBufferSlice =
        NoopBuffer(usage, data.sumOf { it.remaining().toLong() }).slice()

    override fun multiUploadStaging(data: List<ByteBuffer>, alignment: Long, usage: Int): List<GpuBufferSlice> =
        data.map { NoopBuffer(usage, it.remaining().toLong()).slice() }

    override fun multiUploadGpu(data: List<ByteBuffer>, alignment: Long, usage: Int): List<GpuBufferSlice> =
        data.map { NoopBuffer(usage, it.remaining().toLong()).slice() }
}
