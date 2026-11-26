package dev.blockwright.launcher.download

import dev.blockwright.launcher.FakeTransport
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DownloaderTest {
    @TempDir
    lateinit var dir: Path

    private val transport = FakeTransport()
    private val downloader = Downloader(transport)
    private val payload = ByteArray(200_000) { (it * 31 % 251).toByte() }
    private val url = transport.put("https://libraries.minecraft.net/test/lib/1.0/lib-1.0.jar", payload)

    private val target by lazy { dir.resolve("libraries/test/lib-1.0.jar") }
    private val part by lazy { target.resolveSibling("lib-1.0.jar.part") }

    private fun spec(uri: URI = url, size: Long? = payload.size.toLong(), sha1: String? = Sha1.of(payload)) =
        DownloadSpec(uri, target, sha1, size, size ?: 1_000_000)

    private fun assertVerified() {
        assertArrayEquals(payload, Files.readAllBytes(target))
        assertFalse(Files.exists(part))
    }

    @Test
    fun downloadsAndVerifiesAFreshFile() {
        assertEquals(target, downloader.ensure(spec()))
        assertVerified()
        assertEquals(listOf(FakeTransport.Call(url, 0)), transport.calls)
    }

    @Test
    fun validCachedFileMakesNoRequest() {
        downloader.ensure(spec())
        transport.calls.clear()
        transport.offline = true
        downloader.ensure(spec())
        assertEquals(0, transport.calls.size)
        assertVerified()
    }

    @Test
    fun corruptCachedFileIsDownloadedAgain() {
        downloader.ensure(spec())
        val corrupt = payload.copyOf().also { it[1234] = (it[1234] + 1).toByte() }
        Files.write(target, corrupt)
        transport.calls.clear()
        downloader.ensure(spec())
        assertVerified()
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun truncatedCachedFileIsDownloadedAgain() {
        Files.createDirectories(target.parent)
        Files.write(target, payload.copyOf(1000))
        downloader.ensure(spec())
        assertVerified()
    }

    @Test
    fun hashMismatchOnEveryAttemptFailsWithoutLeavingFiles() {
        val wrong = payload.copyOf().also { it[0] = (it[0] + 1).toByte() }
        transport.put(url, wrong)
        val error = assertThrows<DownloadException> { downloader.ensure(spec()) }
        assertTrue(error.message!!.contains(Sha1.of(payload)), error.message)
        assertTrue(error.message!!.contains(Sha1.of(wrong)), error.message)
        assertFalse(Files.exists(target))
        assertFalse(Files.exists(part))
        assertEquals(DownloadPolicy().attempts, transport.calls.size)
    }

    @Test
    fun bodyLargerThanTheExpectedSizeIsAborted() {
        transport.put(url, payload + ByteArray(10))
        val error = assertThrows<DownloadException> { downloader.ensure(spec()) }
        assertTrue(error.message!!.contains("larger"), error.message)
        assertFalse(Files.exists(target))
        assertFalse(Files.exists(part))
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun fileWithoutKnownSizeIsCappedAtMaxBytes() {
        val error = assertThrows<DownloadException> {
            downloader.ensure(DownloadSpec(url, target, null, null, maxBytes = 1000))
        }
        assertTrue(error.message!!.contains("larger"), error.message)
        assertFalse(Files.exists(target))
    }

    @Test
    fun partFileIsResumedWithARangeRequest() {
        Files.createDirectories(target.parent)
        Files.write(part, payload.copyOf(5000))
        downloader.ensure(spec())
        assertVerified()
        assertEquals(listOf(FakeTransport.Call(url, 5000)), transport.calls)
    }

    @Test
    fun interruptedTransferResumesFromThePartOnTheNextAttempt() {
        var first = true
        transport.handler = { uri, rangeFrom ->
            if (first) {
                first = false
                TransportResponse(200, uri, null, failingAfter(payload, 70_000))
            } else {
                val rest = payload.copyOfRange(rangeFrom.toInt(), payload.size)
                FakeTransport.response(206, uri, rest, "bytes $rangeFrom-${payload.size - 1}/${payload.size}")
            }
        }
        downloader.ensure(spec())
        assertVerified()
        assertEquals(listOf(FakeTransport.Call(url, 0), FakeTransport.Call(url, 70_000)), transport.calls)
    }

    @Test
    fun serverIgnoringRangeRestartsFromZero() {
        Files.createDirectories(target.parent)
        Files.write(part, ByteArray(5000) { 7 })
        transport.honourRange = false
        downloader.ensure(spec())
        assertVerified()
        assertEquals(listOf(FakeTransport.Call(url, 5000)), transport.calls)
    }

    @Test
    fun unsatisfiableRangeDiscardsThePart() {
        Files.createDirectories(target.parent)
        Files.write(part, ByteArray(10))
        transport.handler = { uri, rangeFrom ->
            if (rangeFrom > 0) FakeTransport.response(416, uri, ByteArray(0)) else FakeTransport.response(200, uri, payload)
        }
        downloader.ensure(spec())
        assertVerified()
        assertEquals(listOf(FakeTransport.Call(url, 10), FakeTransport.Call(url, 0)), transport.calls)
    }

    @Test
    fun completePartIsVerifiedWithoutARequest() {
        Files.createDirectories(target.parent)
        Files.write(part, payload)
        downloader.ensure(spec())
        assertVerified()
        assertEquals(0, transport.calls.size)
    }

    @Test
    fun httpAndForeignHostsAreRejectedBeforeAnyRequest() {
        for (bad in listOf(
            "http://libraries.minecraft.net/x.jar",
            "https://evil.example/x.jar",
            "https://evilmojang.com/x.jar",
            "https://user@libraries.minecraft.net/x.jar",
            "file:///etc/passwd",
        )) {
            assertThrows<DownloadException>(bad) { downloader.ensure(spec(URI(bad))) }
        }
        assertEquals(0, transport.calls.size)
        assertFalse(Files.exists(target))
    }

    @Test
    fun redirectToAForeignHostIsRejected() {
        transport.handler = { _, _ -> FakeTransport.response(200, URI("https://evil.example/x.jar"), payload) }
        assertThrows<DownloadException> { downloader.ensure(spec()) }
        assertFalse(Files.exists(target))
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun clientErrorIsFinalAndServerErrorIsRetried() {
        transport.remove(url)
        val notFound = assertThrows<DownloadException> { downloader.ensure(spec()) }
        assertTrue(notFound.message!!.contains("404"), notFound.message)
        assertEquals(1, transport.calls.size)

        transport.calls.clear()
        transport.handler = { uri, _ -> FakeTransport.response(503, uri, ByteArray(0)) }
        assertThrows<DownloadException> { downloader.ensure(spec()) }
        assertEquals(DownloadPolicy().attempts, transport.calls.size)
    }

    @Test
    fun networkFailureNamesTheTargetFile() {
        transport.offline = true
        val error = assertThrows<DownloadException> { downloader.ensure(spec()) }
        assertTrue(error.message!!.contains(target.toString()), error.message)
    }

    @Test
    fun fileWithoutHashIsAlwaysFetchedAndReplacedAtomically() {
        Files.createDirectories(target.parent)
        Files.write(target, "old".toByteArray())
        Files.write(part, "stale".toByteArray())
        downloader.ensure(spec(size = null, sha1 = null))
        assertVerified()
        assertEquals(listOf(FakeTransport.Call(url, 0)), transport.calls)
    }

    @Test
    fun concurrentEnsuresOfOneTargetTransferOnce() {
        transport.latencyMillis = 200
        val threads = 4
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map {
                pool.submit<Path> {
                    barrier.await()
                    downloader.ensure(spec())
                }
            }
            results.forEach { assertEquals(target, it.get(30, TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
        }
        assertVerified()
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun ensureAllStopsAtTheFirstFailure() {
        val other = transport.put("https://libraries.minecraft.net/test/other.jar", "other".toByteArray())
        val missing = URI("https://libraries.minecraft.net/test/missing.jar")
        val specs = listOf(
            spec(),
            DownloadSpec(other, dir.resolve("other.jar"), Sha1.of("other".toByteArray()), 5, 5),
            DownloadSpec(missing, dir.resolve("missing.jar"), "0".repeat(40), 5, 5),
        )
        assertThrows<DownloadException> { downloader.ensureAll(specs) }
        assertFalse(Files.exists(dir.resolve("missing.jar")))
    }

    @Test
    fun ensureAllDownloadsEverySpec() {
        val bodies = (1..20).map { "file-$it".toByteArray() }
        val specs = bodies.mapIndexed { i, body ->
            val uri = transport.put("https://libraries.minecraft.net/test/f$i.jar", body)
            DownloadSpec(uri, dir.resolve("f$i.jar"), Sha1.of(body), body.size.toLong(), body.size.toLong())
        }
        Downloader(transport, DownloadPolicy(parallelism = 3)).ensureAll(specs)
        specs.forEachIndexed { i, s -> assertArrayEquals(bodies[i], Files.readAllBytes(s.target)) }
    }

    private fun failingAfter(bytes: ByteArray, limit: Int): InputStream =
        object : InputStream() {
            private val inner = ByteArrayInputStream(bytes, 0, limit)

            override fun read(): Int = inner.read().also { if (it < 0) throw IOException("connection reset") }

            override fun read(b: ByteArray, off: Int, len: Int): Int =
                inner.read(b, off, len).also { if (it < 0) throw IOException("connection reset") }
        }
}
