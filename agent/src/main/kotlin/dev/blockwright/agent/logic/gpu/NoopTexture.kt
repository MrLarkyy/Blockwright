package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.backend.common.BaseGpuTexture

/** A texture that only remembers its shape. */
class NoopTexture(
    usage: Int,
    label: String,
    format: GpuFormat,
    width: Int,
    height: Int,
    depthOrLayers: Int,
    mipLevels: Int,
) : BaseGpuTexture(usage, label, format, width, height, depthOrLayers, mipLevels) {
    @Volatile private var closed = false

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }
}
