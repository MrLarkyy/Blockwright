package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

@Serializable
data class JavaVersionInfo(
    val component: String? = null,
    val majorVersion: Int,
)
