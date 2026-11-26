package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable

/** [version] is a regular expression searched in `os.version`; [arch] `x86` means a 32-bit JVM. */
@Serializable
data class OsRule(
    val name: String? = null,
    val arch: String? = null,
    val version: String? = null,
)
