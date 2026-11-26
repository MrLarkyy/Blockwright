package dev.blockwright.launcher.download

import java.net.URI
import java.nio.file.Path

/**
 * One file to place at [target]. With [sha1] the cached file is reused when it verifies; without it (the version
 * manifest) the file is always fetched and replaced. The transfer stops past [size] when known, else [maxBytes].
 */
data class DownloadSpec(
    val uri: URI,
    val target: Path,
    val sha1: String?,
    val size: Long?,
    val maxBytes: Long,
)
