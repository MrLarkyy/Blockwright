package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.GpuQueryPool
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import org.lwjgl.PointerBuffer
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.function.Supplier

/** A render pass whose draws go nowhere. */
class NoopRenderPass : RenderPass {
    override fun pushDebugGroup(label: Supplier<String>) {
        // No GPU debugger.
    }

    override fun popDebugGroup() {
        // No GPU debugger.
    }

    override fun writeTimestamp(pool: GpuQueryPool, index: Int) {
        // No GPU timeline.
    }

    override fun setPipeline(pipeline: CompiledRenderPipeline) {
        // Nothing is drawn.
    }

    override fun setUniform(name: String, view: GpuTextureView?, sampler: GpuSampler?) {
        // Nothing is drawn.
    }

    override fun setUniform(name: String, buffer: GpuBuffer) {
        // Nothing is drawn.
    }

    override fun setUniform(name: String, slice: GpuBufferSlice) {
        // Nothing is drawn.
    }

    override fun pushConstants(data: ByteBuffer) {
        // Nothing is drawn.
    }

    override fun enableScissor(x: Int, y: Int, width: Int, height: Int) {
        // Nothing is drawn.
    }

    override fun disableScissor() {
        // Nothing is drawn.
    }

    override fun setVertexBuffer(slot: Int, slice: GpuBufferSlice?) {
        // Nothing is drawn.
    }

    override fun setIndexBuffer(buffer: GpuBuffer, type: IndexType) {
        // Nothing is drawn.
    }

    override fun drawIndexed(baseVertex: Int, firstIndex: Int, indexCount: Int, instanceCount: Int, firstInstance: Int) {
        // Nothing is drawn.
    }

    override fun multiDrawIndexed(counts: IntBuffer, baseVertex: Int, drawCount: Int, stride: Int) {
        // Nothing is drawn.
    }

    override fun multiDrawIndexed(offsets: PointerBuffer, counts: IntBuffer, baseVertices: IntBuffer, drawCount: Int) {
        // Nothing is drawn.
    }

    override fun drawIndexedIndirect(slice: GpuBufferSlice, drawCount: Int) {
        // Nothing is drawn.
    }

    override fun <T : Any> drawMultipleIndexed(
        draws: Collection<RenderPass.Draw<T>>,
        indexBuffer: GpuBuffer?,
        indexType: IndexType?,
        uniformNames: Collection<String>,
        context: T,
    ) {
        // Nothing is drawn.
    }

    override fun draw(firstVertex: Int, vertexCount: Int, instanceCount: Int, firstInstance: Int) {
        // Nothing is drawn.
    }

    override fun multiDraw(counts: IntBuffer, firstVertex: Int, drawCount: Int, stride: Int) {
        // Nothing is drawn.
    }

    override fun multiDraw(firsts: IntBuffer, counts: IntBuffer, drawCount: Int) {
        // Nothing is drawn.
    }

    override fun drawIndirect(slice: GpuBufferSlice, drawCount: Int) {
        // Nothing is drawn.
    }

    override fun close() {
        // Nothing recorded.
    }
}
