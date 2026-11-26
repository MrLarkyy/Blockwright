package dev.blockwright.launcher

import dev.blockwright.launcher.manifest.VersionDetails
import java.nio.file.Path

/**
 * A version whose files all sit verified in the cache. [classpath] follows the version JSON order and ends with
 * [clientJar]; [nativesDir] always exists. [assetsDir] and [assetIndexId] are set only when assets were requested.
 * The raw launch arguments stay in [details] (`arguments` or `minecraftArguments`).
 */
data class ResolvedVersion(
    val id: String,
    val mainClass: String,
    val clientJar: Path,
    val classpath: List<Path>,
    val nativesDir: Path,
    val assetsDir: Path?,
    val assetIndexId: String?,
    val javaMajor: Int?,
    val details: VersionDetails,
)
