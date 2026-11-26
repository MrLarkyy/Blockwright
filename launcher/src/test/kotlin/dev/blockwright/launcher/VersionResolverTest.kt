package dev.blockwright.launcher

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.Sha1
import dev.blockwright.launcher.platform.Platform
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VersionResolverTest {
    @TempDir
    lateinit var root: Path

    private val mojang = FakeMojang()
    private val transport = mojang.transport
    private val cache by lazy { CacheLayout(root) }
    private val linux = Platform("linux", "x86_64", "6.8.0")

    private fun resolver() = VersionResolver(cache, transport, DownloadPolicy(retryDelay = Duration.ZERO), platform = linux)

    private fun resolve(includeAssets: Boolean = false) = resolver().resolve(ResolveRequest(FakeMojang.VERSION_ID, includeAssets))

    private fun library(path: String): Path = cache.library(path)

    @Test
    fun resolvesEveryFileVerifiedIntoTheCache() {
        val resolved = resolve()

        val expectedClasspath = listOf(
            library("test/core/1.0/core-1.0.jar") to "core",
            library("test/render/1.0/render-1.0-natives-linux.jar") to "renderLinux",
            library("test/util/1.0/util-1.0.jar") to "util",
            cache.clientJar(FakeMojang.VERSION_ID) to "client",
        )
        assertEquals(expectedClasspath.map { it.first }, resolved.classpath)
        for ((path, key) in expectedClasspath) {
            assertEquals(mojang.sha1(key), Sha1.of(path), key)
        }
        assertEquals(cache.clientJar(FakeMojang.VERSION_ID), resolved.clientJar)
        assertEquals("test.client.Main", resolved.mainClass)
        assertEquals(25, resolved.javaMajor)
        assertNotNull(resolved.details.arguments)
        assertArrayEquals(FakeMojang.LEGACY_NATIVE, Files.readAllBytes(resolved.nativesDir.resolve("liblegacy.so")))
        assertFalse(Files.exists(resolved.nativesDir.resolve("META-INF")))
        assertEquals(mojang.sha1("legacyLinux"), Sha1.of(library("test/legacy/2.9/legacy-2.9-natives-linux.jar")))
        assertFalse(Files.exists(library("test/bridge/1.0/bridge-1.0.jar")))
        assertFalse(Files.exists(library("test/render/1.0/render-1.0-natives-windows.jar")))
        assertEquals(mojang.sha1("version"), Files.readString(cache.versionJsonSha1(FakeMojang.VERSION_ID)))
    }

    @Test
    fun secondResolveUsesTheCacheWithoutNetwork() {
        val first = resolve()
        transport.calls.clear()
        transport.offline = true

        val second = resolve()

        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
        assertEquals(first, second)
    }

    @Test
    fun secondResolveKeepsWhatTheGameWroteIntoTheNativesDirectory() {
        val first = resolve()
        val unpacked = first.nativesDir.resolve("lwjgl/3.3.3/x64/liblwjgl.so")
        Files.createDirectories(unpacked.parent)
        Files.writeString(unpacked, "unpacked by the game")
        val marker = first.nativesDir.resolve(".extracted")
        val extractedAt = Files.getLastModifiedTime(marker)
        transport.calls.clear()
        transport.offline = true

        val second = resolve()

        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
        assertEquals(first, second)
        assertEquals("unpacked by the game", Files.readString(unpacked))
        assertEquals(extractedAt, Files.getLastModifiedTime(marker))
    }

    @Test
    fun corruptLibraryIsDetectedAndDownloadedAgain() {
        resolve()
        val core = library("test/core/1.0/core-1.0.jar")
        val bytes = Files.readAllBytes(core).also { it[0] = (it[0] + 1).toByte() }
        Files.write(core, bytes)
        transport.calls.clear()

        resolve()

        assertEquals(listOf(FakeTransport.Call(mojang.urls.getValue("core"), 0)), transport.calls)
        assertEquals(mojang.sha1("core"), Sha1.of(core))
    }

    @Test
    fun assetsAreSkippedUnlessRequested() {
        val withoutAssets = resolve()
        assertNull(withoutAssets.assetsDir)
        assertNull(withoutAssets.assetIndexId)
        val assetCalls = transport.calls.filter { it.uri.host == "resources.download.minecraft.net" || it.uri.toString() == FakeMojang.ASSET_INDEX_URL }
        assertEquals(emptyList<FakeTransport.Call>(), assetCalls)
        assertFalse(Files.exists(cache.assetsDir()))

        val withAssets = resolve(includeAssets = true)

        assertEquals(cache.assetsDir(), withAssets.assetsDir)
        assertEquals("test-1", withAssets.assetIndexId)
        assertEquals(mojang.sha1("assetIndex"), Sha1.of(cache.assetIndex("test-1")))
        for (key in listOf("assetA", "assetB")) {
            val hash = mojang.sha1(key)
            assertEquals(hash, Sha1.of(cache.assetObject(hash)))
            assertEquals(1, transport.callsTo(mojang.urls.getValue(key)), key)
        }
    }

    @Test
    fun unknownVersionIsAnError() {
        val error = assertThrows<DownloadException> { resolver().resolve(ResolveRequest("9.9-missing")) }
        assertTrue(error.message!!.contains("unknown version"), error.message)
    }

    @Test
    fun invalidVersionIdIsRejectedBeforeAnyRequest() {
        assertThrows<DownloadException> { resolver().resolve(ResolveRequest("../../escape")) }
        assertEquals(0, transport.calls.size)
    }

    @Test
    fun offlineWithACorruptFileFailsNamingTheFile() {
        resolve()
        val util = library("test/util/1.0/util-1.0.jar")
        Files.writeString(util, "junk")
        transport.offline = true

        val error = assertThrows<DownloadException> { resolve() }

        assertTrue(error.message!!.contains(util.toString()), error.message)
        assertFalse(Files.exists(util))
    }

    @Test
    fun tamperedCachedVersionJsonIsFetchedAgain() {
        resolve()
        val json = cache.versionJson(FakeMojang.VERSION_ID)
        Files.writeString(json, Files.readString(json).replace("test.client.Main", "evil.Main"))
        transport.calls.clear()

        val resolved = resolve()

        assertEquals("test.client.Main", resolved.mainClass)
        assertEquals(1, transport.callsTo(VersionResolver.MANIFEST_URI))
        assertEquals(1, transport.callsTo(URI(FakeMojang.VERSION_URL)))
    }

    @Test
    fun cachedManifestIsUsedWhenTheManifestCannotBeFetched() {
        resolve()
        Files.delete(cache.versionJsonSha1(FakeMojang.VERSION_ID))
        transport.remove(VersionResolver.MANIFEST_URI)
        transport.calls.clear()

        val resolved = resolve()

        assertEquals("test.client.Main", resolved.mainClass)
        assertEquals(listOf(FakeTransport.Call(VersionResolver.MANIFEST_URI, 0)), transport.calls)
    }

    @Test
    fun concurrentResolvesOfOneVersionTransferEachFileOnce() {
        transport.latencyMillis = 50
        val threads = 3
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        val results = try {
            (1..threads).map {
                pool.submit<ResolvedVersion> {
                    barrier.await()
                    resolve(includeAssets = true)
                }
            }.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, results.toSet().size)
        val perUri = transport.calls.groupingBy { it.uri }.eachCount()
        assertTrue(perUri.values.all { it == 1 }, perUri.toString())
    }
}
