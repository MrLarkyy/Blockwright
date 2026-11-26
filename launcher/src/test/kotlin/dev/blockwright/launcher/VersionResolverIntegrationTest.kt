package dev.blockwright.launcher

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.HttpTransport
import dev.blockwright.launcher.download.JdkHttpTransport
import dev.blockwright.launcher.download.Sha1
import dev.blockwright.launcher.platform.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Resolves a real release from Mojang (network). Opt-in: `./gradlew :launcher:integrationTest`. The cache lives
 * outside the repository: `-Dblockwright.it.cacheDir=<dir>` or `blockwright-it` next to the default cache.
 */
@Tag("integration")
class VersionResolverIntegrationTest {
    private val cacheRoot: Path = System.getProperty("blockwright.it.cacheDir")?.let(Path::of)
        ?: CacheLayout.defaultRoot().resolveSibling("blockwright-it")
    private val cache = CacheLayout(cacheRoot)
    private val policy = DownloadPolicy()
    private val platform = Platform.current()

    @Test
    fun resolvesARealReleaseAndReusesTheCache() {
        val requested = CopyOnWriteArrayList<URI>()
        JdkHttpTransport(policy).use { jdk ->
            val recording = HttpTransport { uri, from -> requested += uri; jdk.get(uri, from) }
            val resolved = VersionResolver(cache, recording, policy, platform).resolve(ResolveRequest(VERSION))

            assertEquals(resolved.details.downloads.client.sha1, Sha1.of(resolved.clientJar))
            val expected = expectedSha1s(resolved)
            assertEquals(expected.keys, resolved.classpath.dropLast(1).toSet())
            expected.forEach { (path, sha1) -> assertEquals(sha1, Sha1.of(path), path.toString()) }
            assertTrue(Files.isDirectory(resolved.nativesDir))
            assertHostNativesOnly(resolved)

            assertNull(resolved.assetsDir)
            val assetIndexUrl = resolved.details.assetIndex?.url
            assertTrue(requested.none { it.host == "resources.download.minecraft.net" || it.toString() == assetIndexUrl }, requested.toString())

            val offline = HttpTransport { uri, _ -> throw AssertionError("network used for $uri on a complete cache") }
            assertEquals(resolved, VersionResolver(cache, offline, policy, platform).resolve(ResolveRequest(VERSION)))

            val smallest = expected.keys.minBy { Files.size(it) }
            Files.writeString(smallest, "junk")
            requested.clear()
            VersionResolver(cache, recording, policy, platform).resolve(ResolveRequest(VERSION))
            assertEquals(expected.getValue(smallest), Sha1.of(smallest))
            assertEquals(1, requested.size, requested.toString())
        }
    }

    private fun expectedSha1s(resolved: ResolvedVersion): Map<Path, String> =
        LibrarySelector(platform).select(resolved.details.libraries).classpath
            .associate { cache.library(it.path!!) to it.sha1 }

    private fun assertHostNativesOnly(resolved: ResolvedVersion) {
        val hostTokens = when (platform.osName) {
            Platform.WINDOWS -> listOf("natives-windows")
            Platform.OSX -> listOf("natives-macos", "natives-osx")
            else -> listOf("natives-linux")
        }
        val natives = resolved.classpath.map { it.fileName.toString() }.filter { "natives-" in it }
        assertTrue(natives.isNotEmpty())
        assertTrue(natives.all { name -> hostTokens.any { it in name } }, natives.toString())
    }

    private companion object {
        /** Latest release when BW-12 was implemented; pinned so the test is deterministic. */
        const val VERSION = "26.3"
    }
}
