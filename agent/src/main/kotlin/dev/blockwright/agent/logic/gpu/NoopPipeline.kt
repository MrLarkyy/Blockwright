package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline

class NoopPipeline : CompiledRenderPipeline {
    @Volatile private var closed = false

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }
}
