package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

/** Mojang's version manifest: every published version with the URL and SHA-1 of its version JSON. */
@Serializable
data class VersionManifest(
    val versions: List<VersionEntry>,
)
