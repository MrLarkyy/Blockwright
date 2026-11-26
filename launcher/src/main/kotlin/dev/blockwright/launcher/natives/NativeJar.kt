package dev.blockwright.launcher.natives

import java.nio.file.Path

/** A verified legacy natives jar; entries whose names start with an [excludes] prefix are not extracted. */
data class NativeJar(
    val path: Path,
    val sha1: String,
    val excludes: List<String>,
)
