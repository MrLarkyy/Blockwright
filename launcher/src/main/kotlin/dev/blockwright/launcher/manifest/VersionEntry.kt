package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

@Serializable
data class VersionEntry(
    val id: String,
    val type: String,
    val url: String,
    val sha1: String,
)
