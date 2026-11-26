package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

/** A downloadable file; [path] is the maven-layout path for libraries and absent for the client jar. */
@Serializable
data class Artifact(
    val path: String? = null,
    val sha1: String,
    val size: Long,
    val url: String,
)
