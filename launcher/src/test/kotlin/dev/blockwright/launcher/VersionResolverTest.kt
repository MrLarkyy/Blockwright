package dev.blockwright.launcher

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.cache.RefreshPolicy
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
import java.nio.file.attribute.FileTime
import java.time.Instant
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

    private fun resolver(refresh: RefreshPolicy = RefreshPolicy.DEFAULT) =
        VersionResolver(cache, transport, DownloadPolicy(retryDelay = Duration.ZERO), platform = linux, refresh = refresh)

    private fun resolve(includeAssets: Boolean = false, refresh: RefreshPolicy = RefreshPolicy.DEFAULT) =
        resolver(refresh).resolve(ResolveRequest(FakeMojang.VERSION_ID, includeAssets))

    private val sidecar by lazy { cache.versionJsonSha1(FakeMojang.VERSION_ID) }

    private fun ageTheCachedJson(by: Duration) {
        Files.setLastModifiedTime(sidecar, FileTime.from(Instant.now().minus(by)))
    }

    /** The version JSON is re-published under the same URL with another main class; the manifest lists its new hash. */
    private fun republish(): String {
        val body = String(mojang.payloads.getValue("version")).replace("test.client.Main", "test.client.Republished").toByteArray()
        val manifest = Files.readString(cache.manifest()).replace(mojang.sha1("version"), Sha1.of(body))
        transport.put(FakeMojang.VERSION_URL, body)
        transport.put(VersionResolver.MANIFEST_URI, manifest.toByteArray())
        return Sha1.of(body)
    }

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
    fun noLockFileLiesBesideACachedFile() {
        resolve(includeAssets = true)
        val locks = cache.root.resolve("locks")
        val stray = Files.walk(cache.root).use { s ->
            s.filter { it.fileName.toString().endsWith(".lock") && !it.startsWith(locks) }.toList()
        }
        assertEquals(emptyList<Path>(), stray)
        assertTrue(Files.list(locks).use { it.count() } > 0)
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
    fun staleJsonRepublishedUnderANewHashIsDownloadedAgain() {
        resolve()
        val newHash = republish()
        ageTheCachedJson(Duration.ofDays(2))
        transport.calls.clear()

        val resolved = resolve()

        assertEquals("test.client.Republished", resolved.mainClass)
        assertEquals(newHash, Files.readString(sidecar))
        assertEquals(newHash, Sha1.of(cache.versionJson(FakeMojang.VERSION_ID)))
        assertEquals(1, transport.callsTo(VersionResolver.MANIFEST_URI))
        assertEquals(1, transport.callsTo(URI(FakeMojang.VERSION_URL)))
        assertFalse(Files.exists(cache.versionJson(FakeMojang.VERSION_ID).resolveSibling("1.0-test.json.refresh")))
        transport.calls.clear()
        resolve()
        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
    }

    @Test
    fun staleJsonWithTheSameHashChecksTheManifestOnceAndKeepsTheJson() {
        resolve()
        ageTheCachedJson(Duration.ofDays(2))
        transport.calls.clear()

        resolve()

        assertEquals(listOf(FakeTransport.Call(VersionResolver.MANIFEST_URI, 0)), transport.calls)
        assertTrue(Duration.between(Files.getLastModifiedTime(sidecar).toInstant(), Instant.now()) < Duration.ofMinutes(1))
        transport.calls.clear()
        resolve()
        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
    }

    @Test
    fun staleJsonOfflineResolvesFromTheCacheAndRetriesOnlyAfterTheNextPeriod() {
        val first = resolve()
        ageTheCachedJson(Duration.ofDays(2))
        transport.offline = true
        transport.calls.clear()

        val second = resolve()

        assertEquals(first, second)
        assertEquals(listOf(FakeTransport.Call(VersionResolver.MANIFEST_URI, 0)), transport.calls)
        assertTrue(Duration.between(Files.getLastModifiedTime(sidecar).toInstant(), Instant.now()) < Duration.ofMinutes(1))
        transport.calls.clear()
        resolve()
        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
    }

    @Test
    fun failedReplacementDownloadKeepsTheCachedJson() {
        val first = resolve()
        republish()
        transport.remove(URI(FakeMojang.VERSION_URL))
        ageTheCachedJson(Duration.ofDays(2))

        val second = resolve()

        assertEquals(first, second)
        assertEquals(mojang.sha1("version"), Sha1.of(cache.versionJson(FakeMojang.VERSION_ID)))
        assertEquals(mojang.sha1("version"), Files.readString(sidecar))
    }

    @Test
    fun staleJsonIsNotCheckedWhenTheOnlyManifestIsTheCachedOne() {
        resolve()
        republish()
        transport.remove(VersionResolver.MANIFEST_URI)
        ageTheCachedJson(Duration.ofDays(2))

        assertEquals("test.client.Main", resolve().mainClass)
    }

    @Test
    fun neverTrustsTheCachedJsonWhateverItsAge() {
        resolve()
        republish()
        ageTheCachedJson(Duration.ofDays(3650))
        transport.calls.clear()

        assertEquals("test.client.Main", resolve(refresh = RefreshPolicy.NEVER).mainClass)
        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
    }

    @Test
    fun alwaysChecksTheManifestOnEveryResolve() {
        resolve()
        transport.calls.clear()

        resolve(refresh = RefreshPolicy.ALWAYS)
        resolve(refresh = RefreshPolicy.ALWAYS)

        assertEquals(2, transport.callsTo(VersionResolver.MANIFEST_URI))
    }

    @Test
    fun freshJsonWithinTheMaxAgeMakesNoCall() {
        resolve()
        ageTheCachedJson(Duration.ofHours(1))
        transport.calls.clear()

        resolve(refresh = RefreshPolicy.after(Duration.ofHours(2)))

        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
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
    fun versionIdWithSpacesResolvesThroughItsEncodedUrl() {
        val id = FakeMojang.SPACED_VERSION_ID

        val resolved = resolver().resolve(ResolveRequest(id, includeAssets = true))

        assertEquals(1, transport.callsTo(URI(FakeMojang.SPACED_VERSION_URL)))
        assertEquals(0, transport.callsTo(URI(FakeMojang.VERSION_URL)))
        assertEquals(root.resolve("versions").resolve(id).resolve("$id.jar"), resolved.clientJar)
        assertEquals(mojang.sha1("client"), Sha1.of(resolved.clientJar))
        assertEquals(root.resolve("natives").resolve(id), resolved.nativesDir)
        assertArrayEquals(FakeMojang.LEGACY_NATIVE, Files.readAllBytes(resolved.nativesDir.resolve("liblegacy.so")))
        transport.calls.clear()
        transport.offline = true

        assertEquals(resolved, resolver().resolve(ResolveRequest(id, includeAssets = true)))
        assertEquals(emptyList<FakeTransport.Call>(), transport.calls)
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
