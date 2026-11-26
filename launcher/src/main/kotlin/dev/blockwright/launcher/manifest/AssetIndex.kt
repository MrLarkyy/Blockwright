package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

/** Asset index: logical resource path to the content-addressed object. */
@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
)
