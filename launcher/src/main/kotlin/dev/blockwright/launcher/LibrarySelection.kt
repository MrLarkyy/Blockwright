package dev.blockwright.launcher

import dev.blockwright.launcher.manifest.Artifact

/** The libraries one platform needs: [classpath] in version JSON order, [natives] to extract. */
data class LibrarySelection(
    val classpath: List<Artifact>,
    val natives: List<NativeArtifact>,
)
