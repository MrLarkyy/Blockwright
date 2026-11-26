package dev.blockwright.launcher

import dev.blockwright.launcher.manifest.Artifact

/** A legacy natives classifier jar to extract, without the entries matching [excludes]. */
data class NativeArtifact(
    val artifact: Artifact,
    val excludes: List<String>,
)
