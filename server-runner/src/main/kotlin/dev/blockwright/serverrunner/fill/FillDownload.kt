package dev.blockwright.serverrunner.fill

import kotlinx.serialization.Serializable

@Serializable
data class FillDownload(val name: String, val checksums: Checksums, val size: Long, val url: String) {
    @Serializable
    data class Checksums(val sha256: String)
}
