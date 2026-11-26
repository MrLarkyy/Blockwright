package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

/** A library entry; [natives] maps an OS name to a legacy classifier such as `natives-windows-${arch}`. */
@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    val rules: List<Rule>? = null,
    val natives: Map<String, String>? = null,
    val extract: ExtractRules? = null,
)
