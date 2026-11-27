package dev.blockwright.agent.logic

import java.util.concurrent.atomic.AtomicLong

/**
 * What the logic profile leaves out of the resource reload, and the evidence that it did: reload listeners that
 * only feed rendering or audio are not registered, and every lookup of a render or sound asset is counted.
 * Game data (languages, registries, recipes, tags) and everything ticks or physics read stay untouched.
 */
object LogicCuts {
    /** Listener packages that hold only render or audio state. */
    private val DROPPED_PACKAGES = listOf(
        "net.minecraft.client.renderer.",
        "net.minecraft.client.sounds.",
        "net.minecraft.client.resources.model.",
        "net.minecraft.client.resources.palette.",
        "net.minecraft.client.gui.font.",
        // Particle sprites come from the atlases above; particle creation is cut as well.
        "net.minecraft.client.particle.",
    )

    /** Render listeners that live outside those packages (colour maps, GPU warn list). */
    private val DROPPED_CLASSES = setOf(
        "net.minecraft.client.resources.GrassColorReloadListener",
        "net.minecraft.client.resources.FoliageColorReloadListener",
        "net.minecraft.client.resources.DryFoliageColorReloadListener",
    )

    private val ASSET_DIRECTORIES = listOf("textures", "models", "blockstates", "items", "atlases", "sounds")
    private const val SOUNDS_INDEX = "sounds.json"

    private val assetReads = AtomicLong()
    private val blockedSoundLoads = AtomicLong()

    fun keepsListener(className: String): Boolean {
        val outer = className.substringBefore('$')
        return outer !in DROPPED_CLASSES && DROPPED_PACKAGES.none { outer.startsWith(it) }
    }

    /** A resource path (`textures/block/stone.png`) or listed directory (`textures`) of a render or sound asset. */
    fun isRenderAsset(path: String): Boolean =
        path == SOUNDS_INDEX || ASSET_DIRECTORIES.any { path == it || path.startsWith("$it/") }

    fun recordLookup(path: String) {
        if (isRenderAsset(path)) assetReads.incrementAndGet()
    }

    fun recordBlockedSoundLoad() {
        blockedSoundLoads.incrementAndGet()
    }

    val assetReadCount: Long get() = assetReads.get()

    val blockedSoundLoadCount: Long get() = blockedSoundLoads.get()
}
