package dev.blockwright.serverrunner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class PaperJarCacheTest {
    @TempDir
    lateinit var cacheRoot: Path

    private val server = FixtureHttpServer()
    private val jarBytes = ByteArray(10_000) { (it % 251).toByte() }
    private val resolves = AtomicInteger()

    @AfterEach
    fun close() = server.close()

    private fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun build(sha: String = sha256(jarBytes), size: Long = jarBytes.size.toLong()) =
        PaperBuild("26.3", 262, "STABLE", "paper-26.3-262.jar", sha, size, server.uri("/paper.jar").toString(), 25)

    private fun source(build: PaperBuild = build()) = PaperBuildSource { _, _ ->
        resolves.incrementAndGet()
        build
    }

    private fun cache() = PaperJarCache(cacheRoot, server.http)

    private fun buildDir(): Path = cacheRoot.resolve("paper").resolve("26.3").resolve("262")

    @Test
    fun verifiedJarIsStoredWithItsMetadata() {
        server.bytes("/paper.jar", jarBytes)

        val cached = cache().obtain(source(), "26.3", null)

        assertEquals(buildDir().resolve("paper-26.3-262.jar"), cached.jar)
        assertArrayEquals(jarBytes, Files.readAllBytes(cached.jar))
        assertTrue(Files.readString(buildDir().resolve("build.json")).contains(sha256(jarBytes)))
        assertTrue(buildDir().listDirectoryEntries().none { it.name.endsWith(".part") })
    }

    @Test
    fun checksumMismatchLeavesNothingInTheCache() {
        server.bytes("/paper.jar", jarBytes)

        val error = assertThrows(PaperDownloadException::class.java) { cache().obtain(source(build(sha = "00".repeat(32))), "26.3", 262) }

        assertTrue(error.message!!.contains("sha256"), error.message)
        assertEquals(listOf(".lock"), buildDir().listDirectoryEntries().map { it.name })
    }

    @Test
    fun bodyLongerThanTheDeclaredSizeIsAborted() {
        server.bytes("/paper.jar", jarBytes + ByteArray(100))

        assertThrows(PaperDownloadException::class.java) { cache().obtain(source(), "26.3", 262) }

        assertEquals(listOf(".lock"), buildDir().listDirectoryEntries().map { it.name })
    }

    @Test
    fun bodyShorterThanTheDeclaredSizeIsRejected() {
        server.bytes("/paper.jar", jarBytes.copyOf(5_000))

        assertThrows(PaperDownloadException::class.java) { cache().obtain(source(), "26.3", 262) }

        assertEquals(listOf(".lock"), buildDir().listDirectoryEntries().map { it.name })
    }

    @Test
    fun pinnedCachedBuildNeedsNoNetwork() {
        server.bytes("/paper.jar", jarBytes)
        cache().obtain(source(), "26.3", 262)
        val requestsBefore = server.totalRequests()
        resolves.set(0)

        val cached = cache().obtain(source(), "26.3", 262)

        assertEquals(requestsBefore, server.totalRequests())
        assertEquals(0, resolves.get())
        assertEquals(sha256(jarBytes), cached.build.sha256)
    }

    @Test
    fun corruptedCachedJarIsDownloadedAgain() {
        server.bytes("/paper.jar", jarBytes)
        val first = cache().obtain(source(), "26.3", 262)
        Files.write(first.jar, ByteArray(jarBytes.size))

        val second = cache().obtain(source(), "26.3", 262)

        assertEquals(2, server.requests("/paper.jar"))
        assertArrayEquals(jarBytes, Files.readAllBytes(second.jar))
    }

    @Test
    fun concurrentObtainsDownloadOnce() {
        val release = CountDownLatch(1)
        server.route("/paper.jar") { exchange ->
            release.await(5, TimeUnit.SECONDS)
            FixtureHttpServer.respond(exchange, 200, jarBytes)
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = List(2) { pool.submit<CachedPaperJar> { cache().obtain(source(), "26.3", 262) } }
            Thread.sleep(300)
            release.countDown()
            val jars = results.map { it.get(20, TimeUnit.SECONDS).jar }

            assertEquals(1, server.requests("/paper.jar"))
            assertEquals(jars[0], jars[1])
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun invalidVersionNeverReachesTheFilesystem() {
        assertThrows(IllegalArgumentException::class.java) { cache().obtain(source(), "..", 1) }

        assertFalse(Files.exists(cacheRoot.resolve("paper")))
        assertEquals(0, resolves.get())
    }
}
