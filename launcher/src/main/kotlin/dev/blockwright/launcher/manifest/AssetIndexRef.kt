package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

@Serializable
data class AssetIndexRef(
    val id: String,
    val sha1: String,
    val size: Long,
    val url: String,
)
