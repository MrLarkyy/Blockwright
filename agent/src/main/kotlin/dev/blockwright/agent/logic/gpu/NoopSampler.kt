package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.textures.AddressMode
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import java.util.OptionalDouble

class NoopSampler(
    private val addressModeU: AddressMode,
    private val addressModeV: AddressMode,
    private val minFilter: FilterMode,
    private val magFilter: FilterMode,
    private val maxAnisotropy: Int,
    private val maxLod: OptionalDouble,
) : GpuSampler {
    @Volatile private var closed = false

    override fun getAddressModeU(): AddressMode = addressModeU

    override fun getAddressModeV(): AddressMode = addressModeV

    override fun getMinFilter(): FilterMode = minFilter

    override fun getMagFilter(): FilterMode = magFilter

    override fun getMaxAnisotropy(): Int = maxAnisotropy

    override fun getMaxLod(): OptionalDouble = maxLod

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }
}
