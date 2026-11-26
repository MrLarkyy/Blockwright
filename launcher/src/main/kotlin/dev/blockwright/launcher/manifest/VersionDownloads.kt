package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

@Serializable
data class VersionDownloads(
    val client: Artifact,
)
