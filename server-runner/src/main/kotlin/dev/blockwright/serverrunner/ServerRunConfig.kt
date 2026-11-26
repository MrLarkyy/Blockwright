package dev.blockwright.serverrunner

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One throwaway Paper test server. [acceptEula] must only ever be set from the user's explicit choice; nothing is
 * downloaded, written or started without it. [javaExecutable] null means the runner's own Java.
 */
data class ServerRunConfig(
    val version: String,
    val build: Int? = null,
    val plugins: List<Path> = emptyList(),
    val cacheRoot: Path,
    val artifactDir: Path,
    val acceptEula: Boolean = false,
    val javaExecutable: Path? = null,
    val maxHeap: String = "1G",
    val port: Int? = null,
    val propertyOverrides: Map<String, String> = emptyMap(),
    val readyTimeout: Duration = 180.seconds,
    val stopTimeout: Duration = 30.seconds,
    val keepRunDir: Boolean = false,
) {
    init {
        PaperBuild.requireVersion(version)
        build?.let(PaperBuild::requireBuild)
        require(HEAP.matches(maxHeap)) { "invalid maxHeap \"$maxHeap\" (e.g. 1G, 512M)" }
        port?.let { require(it in 1..65535) { "invalid port $it" } }
        require(readyTimeout.isPositive() && stopTimeout.isPositive()) { "timeouts must be positive" }
        ServerProperties.requireValidOverrides(propertyOverrides)
    }

    private companion object {
        val HEAP = Regex("[1-9][0-9]{0,5}[KkMmGg]?")
    }
}
