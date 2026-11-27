package dev.blockwright.agent.logic.gpu

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.buffers.TransientMemory
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.commands.GpuFence
import com.mojang.renderpearl.api.commands.GpuQueryPool
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.commands.RenderPassDescriptor
import com.mojang.renderpearl.api.textures.GpuTexture
import org.joml.Vector4fc
import java.nio.ByteBuffer

/** Accepts every command and executes none; read-back callbacks run at once because nothing is pending. */
class NoopCommandEncoder : CommandEncoder {
    private val memory = NoopTransientMemory()

    override fun submit() {
        // Nothing recorded.
    }

    override fun transientMemory(): TransientMemory = memory

    override fun createRenderPass(descriptor: RenderPassDescriptor): RenderPass = NoopRenderPass()

    override fun clearColorTexture(texture: GpuTexture, color: Vector4fc) {
        // No pixels.
    }

    override fun clearColorAndDepthTextures(colorTexture: GpuTexture, color: Vector4fc, depthTexture: GpuTexture, depth: Double) {
        // No pixels.
    }

    override fun clearColorAndDepthTextures(
        colorTexture: GpuTexture,
        color: Vector4fc,
        depthTexture: GpuTexture,
        depth: Double,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        mipLevel: Int,
    ) {
        // No pixels.
    }

    override fun clearDepthTexture(texture: GpuTexture, depth: Double) {
        // No pixels.
    }

    override fun writeToBuffer(slice: GpuBufferSlice, data: ByteBuffer) {
        // No GPU memory.
    }

    override fun copyToBuffer(source: GpuBufferSlice, target: GpuBufferSlice) {
        // No GPU memory.
    }

    override fun writeToTexture(texture: GpuTexture, image: NativeImage) {
        // No GPU memory.
    }

    override fun writeToTexture(texture: GpuTexture, image: NativeImage, mipLevel: Int, depthOrLayer: Int, x: Int, y: Int) {
        // No GPU memory.
    }

    override fun writeToTexture(
        texture: GpuTexture,
        data: ByteBuffer,
        mipLevel: Int,
        depthOrLayer: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        // No GPU memory.
    }

    override fun copyBufferToTexture(
        source: GpuBufferSlice,
        rowLength: Int,
        imageHeight: Int,
        sourceX: Int,
        sourceY: Int,
        texture: GpuTexture,
        mipLevel: Int,
        depthOrLayer: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        // No GPU memory.
    }

    override fun copyTextureToBuffer(texture: GpuTexture, buffer: GpuBuffer, offset: Long, callback: Runnable, mipLevel: Int) {
        callback.run()
    }

    override fun copyTextureToBuffer(
        texture: GpuTexture,
        buffer: GpuBuffer,
        offset: Long,
        callback: Runnable,
        mipLevel: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        callback.run()
    }

    override fun copyTextureToTexture(
        source: GpuTexture,
        target: GpuTexture,
        mipLevel: Int,
        targetX: Int,
        targetY: Int,
        sourceX: Int,
        sourceY: Int,
        width: Int,
        height: Int,
    ) {
        // No GPU memory.
    }

    override fun createFence(): GpuFence = NoopFence

    override fun writeTimestamp(pool: GpuQueryPool, index: Int) {
        // No GPU timeline.
    }
}
