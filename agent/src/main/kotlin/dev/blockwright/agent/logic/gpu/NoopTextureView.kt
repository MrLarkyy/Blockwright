package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.backend.common.BaseGpuTextureView

class NoopTextureView(texture: GpuTexture, baseMipLevel: Int, mipLevels: Int) :
    BaseGpuTextureView(texture, baseMipLevel, mipLevels) {
    @Volatile private var closed = false

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }
}
