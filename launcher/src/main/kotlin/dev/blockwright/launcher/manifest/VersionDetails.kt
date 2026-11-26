package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One version JSON. [arguments] (modern) and [minecraftArguments] (legacy) stay raw: building the command line
 * belongs to the launch step, not to the resolver.
 */
@Serializable
data class VersionDetails(
    val id: String,
    val mainClass: String,
    val downloads: VersionDownloads,
    val libraries: List<Library> = emptyList(),
    val assetIndex: AssetIndexRef? = null,
    val javaVersion: JavaVersionInfo? = null,
    val arguments: JsonElement? = null,
    val minecraftArguments: String? = null,
)
