package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.commands.GpuQueryPool
import com.mojang.renderpearl.api.device.DeviceFeatures
import com.mojang.renderpearl.api.device.DeviceInfo
import com.mojang.renderpearl.api.device.DeviceLimits
import com.mojang.renderpearl.api.device.DeviceType
import com.mojang.renderpearl.api.device.GpuDevice
import com.mojang.renderpearl.api.device.GpuSurface
import com.mojang.renderpearl.api.device.HintsAndWorkarounds
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.ShaderSource
import com.mojang.renderpearl.api.textures.AddressMode
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import java.nio.ByteBuffer
import java.util.OptionalDouble
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * A GPU device that keeps only the bookkeeping Minecraft reads back (sizes, formats, usage) and draws nothing:
 * no shader is compiled, no texture or buffer holds GPU memory.
 */
class NoopGpuDevice : GpuDevice {
    private val encoder = NoopCommandEncoder()

    override fun createSurface(window: Long, framebufferValid: BooleanSupplier): GpuSurface = NoopSurface()

    override fun createCommandEncoder(): CommandEncoder = encoder

    override fun createSampler(
        addressModeU: AddressMode,
        addressModeV: AddressMode,
        minFilter: FilterMode,
        magFilter: FilterMode,
        maxAnisotropy: Int,
        maxLod: OptionalDouble,
    ): GpuSampler = NoopSampler(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod)

    override fun createTexture(
        label: Supplier<String>?,
        usage: Int,
        format: GpuFormat,
        width: Int,
        height: Int,
        depthOrLayers: Int,
        mipLevels: Int,
    ): GpuTexture = NoopTexture(usage, label?.get() ?: "", format, width, height, depthOrLayers, mipLevels)

    override fun createTexture(
        label: String?,
        usage: Int,
        format: GpuFormat,
        width: Int,
        height: Int,
        depthOrLayers: Int,
        mipLevels: Int,
    ): GpuTexture = NoopTexture(usage, label ?: "", format, width, height, depthOrLayers, mipLevels)

    override fun createTextureView(texture: GpuTexture): GpuTextureView = NoopTextureView(texture, 0, texture.mipLevels)

    override fun createTextureView(texture: GpuTexture, baseMipLevel: Int, mipLevels: Int): GpuTextureView =
        NoopTextureView(texture, baseMipLevel, mipLevels)

    override fun createBuffer(label: Supplier<String>?, usage: Int, size: Long): GpuBuffer = NoopBuffer(usage, size)

    override fun createBuffer(label: Supplier<String>?, usage: Int, data: ByteBuffer): GpuBuffer =
        NoopBuffer(usage, data.remaining().toLong())

    override fun getLastDebugMessages(): List<String> = emptyList()

    override fun isDebuggingEnabled(): Boolean = false

    override fun compilePipeline(
        pipeline: RenderPipeline,
        source: ShaderSource,
        executor: Executor,
    ): CompletableFuture<CompiledRenderPipeline.Pending> =
        CompletableFuture.completedFuture(CompiledRenderPipeline.Pending { NoopPipeline() })

    override fun close() {
        // Nothing to release.
    }

    override fun createTimestampQueryPool(size: Int): GpuQueryPool = NoopQueryPool(size)

    override fun getDeviceInfo(): DeviceInfo = INFO

    companion object {
        private const val MAX_TEXTURE_SIZE = 16384
        private const val UNIFORM_ALIGNMENT = 256
        private const val COLOR_ATTACHMENTS = 8

        val INFO = DeviceInfo(
            "Blockwright no-op device",
            "Blockwright",
            "none",
            false,
            NoopGpuBackend.NAME,
            1f,
            DeviceLimits(1, UNIFORM_ALIGNMENT, MAX_TEXTURE_SIZE, Long.MAX_VALUE, 0, COLOR_ATTACHMENTS, 0),
            DeviceFeatures(false, false, false, false, false, false, false, false),
            emptySet(),
            HintsAndWorkarounds(false, false, false, false),
            DeviceType.CPU,
        )
    }
}
