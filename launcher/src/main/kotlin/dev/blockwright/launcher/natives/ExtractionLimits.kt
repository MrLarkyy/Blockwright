package dev.blockwright.launcher.natives

/** Bounds on uncompressed natives so a hostile jar cannot exhaust the disk (zip bomb). */
data class ExtractionLimits(
    val maxEntries: Int = 2000,
    val maxEntryBytes: Long = 64L * 1024 * 1024,
    val maxTotalBytes: Long = 256L * 1024 * 1024,
)
