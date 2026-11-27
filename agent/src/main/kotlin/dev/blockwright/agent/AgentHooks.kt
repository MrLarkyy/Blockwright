package dev.blockwright.agent

import com.mojang.renderpearl.api.device.GpuBackend
import dev.blockwright.agent.logic.HeadlessDisplay
import dev.blockwright.agent.logic.HeadlessFrame
import dev.blockwright.agent.logic.LogicCuts
import dev.blockwright.agent.logic.gpu.NoopGpuBackend
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer

/**
 * The only entry points of the mixins: each mixin hooks one place in the client and calls one function here,
 * which decides from the profile. Nothing is cut unless the profile is logic; error boxes are replaced by status
 * lines in both launcher profiles.
 */
object AgentHooks {
    private const val CHUNK_SHIFT = 4
    private val cutsActive: Boolean get() = Agent.profile.cutsActive

    @JvmStatic
    fun beforeSdlInit() {
        if (cutsActive) HeadlessDisplay.configure()
    }

    /** Null keeps Minecraft's own backends (OpenGL, Vulkan). */
    @JvmStatic
    fun replacementBackends(): Array<GpuBackend>? = if (cutsActive) arrayOf(NoopGpuBackend()) else null

    @JvmStatic
    fun skipsRender(): Boolean = cutsActive

    @JvmStatic
    fun onSkippedExtract() = HeadlessFrame.onSkippedExtract(Minecraft.getInstance())

    @JvmStatic
    fun keepsReloadListener(listener: Any): Boolean = !cutsActive || LogicCuts.keepsListener(listener.javaClass.name)

    @JvmStatic
    fun recordResourceLookup(path: String) = LogicCuts.recordLookup(path)

    /** True when the sound library must not load. */
    @JvmStatic
    fun blocksSoundLibrary(): Boolean {
        if (!cutsActive) return false
        LogicCuts.recordBlockedSoundLoad()
        return true
    }

    @JvmStatic
    fun cutsParticles(): Boolean = cutsActive

    /** In logic the level is ready once the chunk under the player arrived; there is no section to compile. */
    @JvmStatic
    fun playerChunkReceived(level: ClientLevel, player: LocalPlayer): Boolean =
        cutsActive && level.hasChunk(player.blockX shr CHUNK_SHIFT, player.blockZ shr CHUNK_SHIFT)

    @JvmStatic
    fun onTickEnd(minecraft: Minecraft) = ClientTicker.onTickEnd(minecraft)

    @JvmStatic
    fun beforeKeyboardInput() {
        Agent.tracer?.takeIf { it.isActive }?.beforeInput(Minecraft.getInstance().options)
    }

    /** True when Minecraft's blocking error box must not be shown (a launcher-started client has nobody to click it). */
    @JvmStatic
    fun replacesErrorBox(message: String?): Boolean = Agent.startupFailures.replacesErrorBox(message)

    @JvmStatic
    fun onWindowInitFailed(message: String?) = Agent.startupFailures.onWindowInitFailed(message)

    @JvmStatic
    fun afterPlayerTick(player: LocalPlayer) {
        Agent.tracer?.takeIf { it.isActive }?.afterPlayerTick(player, Minecraft.getInstance().options)
    }
}
