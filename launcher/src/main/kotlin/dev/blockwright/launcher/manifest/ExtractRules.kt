package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

/** Entry-name prefixes left out when a legacy natives jar is extracted. */
@Serializable
data class ExtractRules(
    val exclude: List<String> = emptyList(),
)
