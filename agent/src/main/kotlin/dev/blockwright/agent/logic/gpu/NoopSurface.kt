package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.device.GpuSurface
import com.mojang.renderpearl.api.textures.GpuTextureView
import java.util.Optional

/** A swapchain that is never acquired, so Minecraft never blits to it or presents it. */
class NoopSurface : GpuSurface {
    @Volatile private var configuration: GpuSurface.Configuration? = null

    override fun configure(configuration: GpuSurface.Configuration) {
        this.configuration = configuration
    }

    override fun currentConfiguration(): Optional<GpuSurface.Configuration> = Optional.ofNullable(configuration)

    override fun supportedPresentModes(): Collection<GpuSurface.PresentMode> = GpuSurface.PresentMode.entries

    override fun isSuboptimal(): Boolean = false

    override fun isAcquired(): Boolean = false

    override fun acquireNextTexture() {
        // There is no image to acquire.
    }

    override fun blitFromTexture(encoder: CommandEncoder, view: GpuTextureView) {
        // Never called while not acquired.
    }

    override fun present() {
        // Never called while not acquired.
    }

    override fun close() {
        // Nothing to release.
    }
}
