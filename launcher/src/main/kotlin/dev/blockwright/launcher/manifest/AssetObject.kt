package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

@Serializable
data class AssetObject(
    val hash: String,
    val size: Long,
)
