package dev.blockwright.launcher.launch.fabric

import dev.blockwright.launcher.FakeTransport
import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.download.Sha1
import dev.blockwright.launcher.launch.LaunchException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FabricRuntimeTest {
    @TempDir lateinit var root: Path

    private val loaderBytes = "loader jar".toByteArray()
    private val asmBytes = "asm jar".toByteArray()
    private val runtime = FabricRuntime(
        "0.20.1",
        listOf(
            FabricArtifact("net.fabricmc:fabric-loader:0.20.1", Sha1.of(loaderBytes), loaderBytes.size.toLong(), "https://maven.fabricmc.net/"),
            FabricArtifact("org.ow2.asm:asm:9.10.1", Sha1.of(asmBytes), asmBytes.size.toLong(), "https://repo.maven.apache.org/maven2/"),
        ),
    )

    @Test
    fun theLauncherShipsAPinForTheCatalogLoaderAndItsLibraries() {
        val pinned = FabricRuntime.pinned()
        assertEquals("0.20.1", pinned.loaderVersion)
        val names = pinned.artifacts.map { it.coordinates.substringBeforeLast(':') }.toSet()
        assertEquals(setOf("net.fabricmc:fabric-loader", "net.fabricmc:sponge-mixin", "org.ow2.asm:asm", "org.ow2.asm:asm-analysis", "org.ow2.asm:asm-commons", "org.ow2.asm:asm-tree", "org.ow2.asm:asm-util"), names)
        val policy = DownloadPolicy()
        pinned.artifacts.forEach { assertTrue(policy.permits(it.uri), it.uri.toString()) }
    }

    @Test
    fun specsCarryThePinAndLandInTheSharedLibraries() {
        val specs = runtime.specs(CacheLayout(root))
        assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar", specs[0].uri.toString())
        assertEquals("https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar", specs[1].uri.toString())
        assertEquals(CacheLayout(root).library("net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar"), specs[0].target)
        assertEquals(Sha1.of(loaderBytes), specs[0].sha1)
        assertEquals(loaderBytes.size.toLong(), specs[0].size)
        assertEquals(specs[0].size, specs[0].maxBytes)
    }

    @Test
    fun ensureDownloadsVerifiedJarsInPinnedOrder() {
        val transport = FakeTransport()
        val specs = runtime.specs(CacheLayout(root))
        transport.put(specs[0].uri, loaderBytes)
        transport.put(specs[1].uri, asmBytes)
        val classpath = runtime.ensure(Downloader(transport, DownloadPolicy()) {}, CacheLayout(root))
        assertEquals(specs.map { it.target }, classpath)
        assertEquals(Sha1.of(loaderBytes), Sha1.of(classpath[0]))
    }

    @Test
    fun aTamperedJarIsRejected() {
        val transport = FakeTransport()
        val specs = runtime.specs(CacheLayout(root))
        transport.put(specs[0].uri, "evil jar".toByteArray())
        transport.put(specs[1].uri, asmBytes)
        assertThrows<DownloadException> { runtime.ensure(Downloader(transport, DownloadPolicy()) {}, CacheLayout(root)) }
        assertFalse(Files.exists(specs[0].target))
    }

    @Test
    fun aRepositoryOutsideTheAllowedHostsIsNeverRequested() {
        val transport = FakeTransport()
        val rogue = FabricRuntime("0.20.1", listOf(runtime.artifacts[0].copy(repository = "https://maven.example.com/")))
        assertThrows<DownloadException> { rogue.ensure(Downloader(transport, DownloadPolicy()) {}, CacheLayout(root)) }
        assertEquals(0, transport.calls.size)
    }

    @Test
    fun malformedPinsAreRefused() {
        assertThrows<LaunchException> { FabricRuntime.parse("""{"loaderVersion": "1", "artifacts": []}""") }
        assertThrows<LaunchException> { FabricRuntime.parse("""{"loaderVersion": "1", "artifacts": [{"coordinates": "a:b:1", "sha1": "x", "size": 1, "repository": "https://maven.fabricmc.net/"}]}""") }
        assertThrows<LaunchException> { FabricRuntime.parse("nope") }
        assertThrows<IllegalArgumentException> { FabricArtifact("a:b", "0".repeat(40), 1, "https://maven.fabricmc.net/").path }
        assertThrows<IllegalArgumentException> { FabricArtifact("a:../b:1", "0".repeat(40), 1, "https://maven.fabricmc.net/").path }
    }
}
