package dev.blockwright.serverrunner

import kotlinx.serialization.Serializable

/** One Paper server jar as published by Fill, already validated. */
@Serializable
data class PaperBuild(
    val version: String,
    val build: Int,
    val channel: String,
    val fileName: String,
    val sha256: String,
    val size: Long,
    val url: String,
    val javaMinimum: Int,
) {
    companion object {
        const val MAX_JAR_BYTES: Long = 256L * 1024 * 1024

        private val VERSION = Regex("[0-9A-Za-z][0-9A-Za-z.-]{0,31}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val FILE_NAME = Regex("[A-Za-z0-9._-]+\\.jar")

        // The version and the file name become path segments of the cache and of Fill URLs.
        fun requireVersion(version: String) {
            if (!VERSION.matches(version) || version.contains("..")) {
                throw IllegalArgumentException("invalid Paper version \"${version.take(40)}\"")
            }
        }

        fun requireBuild(build: Int) {
            require(build > 0) { "invalid Paper build $build" }
        }

        fun isSha256(value: String): Boolean = SHA256.matches(value)

        fun isJarFileName(value: String): Boolean = FILE_NAME.matches(value)
    }
}
