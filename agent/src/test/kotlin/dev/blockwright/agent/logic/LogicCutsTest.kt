package dev.blockwright.agent.logic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogicCutsTest {
    @Test
    fun keepsGameDataListeners() {
        for (name in listOf(
            "net.minecraft.client.resources.language.LanguageManager",
            "net.minecraft.client.resources.SplashManager",
            "net.minecraft.client.PeriodicNotificationManager",
        )) {
            assertTrue(LogicCuts.keepsListener(name), name)
        }
    }

    @Test
    fun dropsRenderSoundModelAndParticleListeners() {
        for (name in listOf(
            "net.minecraft.client.sounds.SoundManager",
            "net.minecraft.client.renderer.texture.TextureManager",
            "net.minecraft.client.renderer.GameRenderer",
            "net.minecraft.client.renderer.ShaderManager$1",
            "net.minecraft.client.resources.model.ModelManager",
            "net.minecraft.client.resources.model.sprite.AtlasManager",
            "net.minecraft.client.gui.font.FontManager",
            "net.minecraft.client.particle.ParticleResources",
            "net.minecraft.client.resources.GrassColorReloadListener",
        )) {
            assertFalse(LogicCuts.keepsListener(name), name)
        }
    }

    @Test
    fun countsOnlyRenderAndSoundAssetPaths() {
        for (path in listOf("textures/block/stone.png", "textures", "models/block/a.json", "blockstates/stone.json", "items/x.json", "atlases/blocks.json", "sounds/a.ogg", "sounds.json")) {
            assertTrue(LogicCuts.isRenderAsset(path), path)
        }
        for (path in listOf("lang/en_us.json", "texts/splashes.txt", "texturesque/a", "shaders/core/a.vsh", "regional_compliancies.json", "itemsx.json")) {
            assertFalse(LogicCuts.isRenderAsset(path), path)
        }
    }
}
