package dev.blockwright.agent

import com.mojang.blaze3d.systems.RenderSystem
import dev.blockwright.agent.logic.LogicCuts
import dev.blockwright.agent.mixin.DisconnectedScreenAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DisconnectedScreen
import org.lwjgl.sdl.SDLVideo

/**
 * Watches the end of every client tick and reports the join: `joined` on the first tick with a level, a player and
 * no screen; `failed` (before the join) or `disconnected` (after it) when the disconnected screen opens, with the
 * reason the client shows. Client thread only.
 */
object ClientTicker {
    private var joined = false
    private var ended = false

    fun onTickEnd(minecraft: Minecraft) {
        if (Agent.client == null) Agent.client = minecraft
        if (Agent.profile == AgentProfile.NONE) return
        val screen = minecraft.gui.screen()
        val player = minecraft.player
        if (!joined && minecraft.level != null && player != null && screen == null) {
            joined = true
            Agent.status.emit("joined", joinedFields(minecraft))
        }
        // A trace starts once the player stands on the ground: the first ticks after a join may still settle the
        // spawn position, and how many depends on when the loading screen closed, not on game logic.
        if (joined && player != null && player.onGround()) Agent.tracer?.begin(player)
        if (!ended && screen is DisconnectedScreen) {
            ended = true
            val reason = (screen as DisconnectedScreenAccessor).blockwrightDetails().reason().string
            Agent.status.emit(if (joined) "disconnected" else "failed", mapOf("reason" to reason))
        }
    }

    private fun joinedFields(minecraft: Minecraft): Map<String, Any?> = mapOf(
        "profile" to Agent.profile.property,
        "player" to minecraft.user.name,
        "video" to (SDLVideo.SDL_GetCurrentVideoDriver() ?: "none"),
        "gpu" to RenderSystem.getDevice().deviceInfo.backendName(),
        "sound" to if (Agent.profile.cutsActive) "off" else "on",
        "assetReads" to LogicCuts.assetReadCount,
        "soundLoadsBlocked" to LogicCuts.blockedSoundLoadCount,
    )
}
