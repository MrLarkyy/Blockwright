package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

@Serializable
data class LibraryDownloads(
    val artifact: Artifact? = null,
    val classifiers: Map<String, Artifact>? = null,
)
