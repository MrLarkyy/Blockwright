package dev.blockwright.agent.logic

import dev.blockwright.agent.mixin.LoadingOverlayAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.LoadingOverlay

/**
 * The frame of the logic profile: Minecraft's frame loop keeps its window update, input, picking and frame limiter,
 * but render-state extraction and rendering do not run. The one thing extraction does besides drawing is removing
 * a loading overlay whose reload finished (its completion callback already ran in the overlay's tick); that is
 * done here so the first screen and the quick-play join proceed.
 */
object HeadlessFrame {
    private const val NOT_FADING = -1L

    fun onSkippedExtract(minecraft: Minecraft) {
        val overlay = minecraft.gui.overlay()
        if (overlay is LoadingOverlay && (overlay as LoadingOverlayAccessor).blockwrightFadeOutStart() != NOT_FADING) {
            minecraft.gui.setOverlay(null)
        }
    }
}
