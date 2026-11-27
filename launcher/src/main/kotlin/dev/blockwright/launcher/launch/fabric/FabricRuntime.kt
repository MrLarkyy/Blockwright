package dev.blockwright.launcher.launch.fabric

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.DownloadSpec
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.launch.LaunchException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * The Fabric loader and its runtime libraries, pinned by the launcher build (`fabric-runtime.json`, generated from
 * Gradle's resolved `fabricRuntime` configuration). At runtime each jar is downloaded into the shared cache's
 * `libraries/` and used only when its pinned SHA-1 and size match; the [Downloader]'s policy limits the hosts.
 */
@Serializable
data class FabricRuntime(val loaderVersion: String, val artifacts: List<FabricArtifact>) {
    init {
        require(artifacts.isNotEmpty()) { "the Fabric runtime lists no artifacts" }
        artifacts.forEach { require(SHA1.matches(it.sha1) && it.size > 0) { "artifact ${it.coordinates} has no valid pin" } }
    }

    fun specs(cache: CacheLayout): List<DownloadSpec> =
        artifacts.map {
            val path = cache.library(it.path)
            DownloadSpec(it.uri, path, it.sha1, it.size, it.size, cache.lockFile(path))
        }

    /** Ensures every jar and returns the classpath entries in the pinned order. */
    fun ensure(downloader: Downloader, cache: CacheLayout): List<Path> {
        val specs = specs(cache)
        downloader.ensureAll(specs)
        return specs.map { it.target }
    }

    companion object {
        const val RESOURCE = "/dev/blockwright/launcher/launch/fabric/fabric-runtime.json"
        const val MAIN_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient"
        private val SHA1 = Regex("[0-9a-f]{40}")
        private val json = Json { ignoreUnknownKeys = true }

        fun pinned(): FabricRuntime {
            val text = FabricRuntime::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw LaunchException("the launcher was built without $RESOURCE")
            return parse(text)
        }

        fun parse(text: String): FabricRuntime = try {
            json.decodeFromString<FabricRuntime>(text)
        } catch (e: SerializationException) {
            throw LaunchException("invalid Fabric runtime pin: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw LaunchException("invalid Fabric runtime pin: ${e.message}", e)
        }
    }
}
