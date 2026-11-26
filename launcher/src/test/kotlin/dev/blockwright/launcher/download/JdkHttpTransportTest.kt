package dev.blockwright.launcher.download

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class JdkHttpTransportTest {
    private val release = CountDownLatch(1)
    private val handlers: ExecutorService = Executors.newCachedThreadPool()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        executor = handlers
        start()
    }
    private val policy = DownloadPolicy(
        connectTimeout = Duration.ofSeconds(5),
        idleTimeout = Duration.ofMillis(IDLE_MS),
        allowedHosts = listOf("127.0.0.1"),
        httpsOnly = false,
    )
    private val transport = JdkHttpTransport(policy)
    private val payload = ByteArray(10_000) { it.toByte() }

    @AfterEach
    fun stop() {
        release.countDown()
        transport.close()
        server.stop(0)
        handlers.shutdownNow()
        handlers.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun uri(path: String) = URI("http://127.0.0.1:${server.address.port}$path")

    private fun route(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange -> exchange.use(handler) }
    }

    @Test
    fun deliversBodyAndSendsRangeAndUserAgent() {
        val range = AtomicReference<String?>()
        val agent = AtomicReference<String?>()
        route("/file") { exchange ->
            range.set(exchange.requestHeaders.getFirst("Range"))
            agent.set(exchange.requestHeaders.getFirst("User-Agent"))
            val from = range.get()?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            exchange.responseHeaders.add("Content-Range", "bytes $from-${payload.size - 1}/${payload.size}")
            exchange.sendResponseHeaders(if (from > 0) 206 else 200, (payload.size - from).toLong())
            exchange.responseBody.write(payload, from, payload.size - from)
        }
        transport.get(uri("/file"), 4000).use { response ->
            assertEquals(206, response.status)
            assertEquals("bytes 4000-9999/10000", response.contentRange)
            assertEquals(uri("/file"), response.finalUri)
            assertArrayEquals(payload.copyOfRange(4000, payload.size), response.body.readAllBytes())
        }
        assertEquals("bytes=4000-", range.get())
        assertTrue(agent.get()!!.startsWith("blockwright/"), agent.get())
        transport.get(uri("/file"), 0).use { assertArrayEquals(payload, it.body.readAllBytes()) }
        assertEquals(null, range.get())
    }

    @Test
    fun stalledBodyFailsAfterTheIdleTimeout() {
        route("/stall") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write(payload, 0, 100)
            exchange.responseBody.flush()
            release.await(30, TimeUnit.SECONDS)
        }
        val started = System.nanoTime()
        transport.get(uri("/stall"), 0).use { response ->
            assertThrows<IOException> { response.body.readAllBytes() }
        }
        val elapsed = Duration.ofNanos(System.nanoTime() - started)
        assertTrue(elapsed < Duration.ofSeconds(10), "took $elapsed")
        assertTrue(!Thread.currentThread().isInterrupted, "the idle timeout must not leave the caller interrupted")
    }

    @Test
    fun stalledHeadersFailAfterTheIdleTimeout() {
        route("/silent") { release.await(30, TimeUnit.SECONDS) }
        val started = System.nanoTime()
        assertThrows<IOException> { transport.get(uri("/silent"), 0) }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(10))
    }

    @Test
    fun followsAllowedRedirectsKeepingTheRange() {
        val range = AtomicReference<String?>()
        route("/old") { exchange ->
            exchange.responseHeaders.add("Location", "/new")
            exchange.sendResponseHeaders(302, -1)
        }
        route("/new") { exchange ->
            range.set(exchange.requestHeaders.getFirst("Range"))
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.write(payload)
        }
        transport.get(uri("/old"), 10).use { response ->
            assertEquals(200, response.status)
            assertEquals(uri("/new"), response.finalUri)
            assertArrayEquals(payload, response.body.readAllBytes())
        }
        assertEquals("bytes=10-", range.get())
    }

    @Test
    fun redirectToADisallowedUrlIsNeverRequested() {
        val reached = AtomicInteger()
        route("/target") { exchange ->
            reached.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
        }
        val port = server.address.port
        // localhost resolves to this server but is not an allowed host; user info is never allowed.
        val locations = listOf("http://localhost:$port/target", "http://user@127.0.0.1:$port/target")
        locations.forEachIndexed { i, location ->
            route("/away$i") { exchange ->
                exchange.responseHeaders.add("Location", location)
                exchange.sendResponseHeaders(301, -1)
            }
            val error = assertThrows<DownloadException>(location) { transport.get(uri("/away$i"), 0).close() }
            assertTrue(error.message!!.contains("redirect"), error.message)
        }
        assertEquals(0, reached.get())
    }

    @Test
    fun redirectLoopsStopAtTheLimit() {
        val hops = AtomicInteger()
        route("/loop") { exchange ->
            hops.incrementAndGet()
            exchange.responseHeaders.add("Location", "/loop")
            exchange.sendResponseHeaders(307, -1)
        }
        val error = assertThrows<DownloadException> { transport.get(uri("/loop"), 0) }
        assertTrue(error.message!!.contains("redirects"), error.message)
        assertEquals(policy.maxRedirects + 1, hops.get())
    }

    @Test
    fun disallowedUrlIsRefusedBeforeConnecting() {
        assertThrows<DownloadException> { transport.get(URI("http://localhost:${server.address.port}/x"), 0) }
    }

    @Test
    fun errorStatusIsReportedNotThrown() {
        route("/missing") { it.sendResponseHeaders(404, -1) }
        transport.get(uri("/missing"), 0).use { assertEquals(404, it.status) }
    }

    @Test
    fun downloaderOverTheRealTransportVerifiesAndCaches(@TempDir dir: Path) {
        val requests = AtomicInteger()
        route("/lib.jar") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.write(payload)
        }
        val spec = DownloadSpec(uri("/lib.jar"), dir.resolve("lib.jar"), Sha1.of(payload), payload.size.toLong(), payload.size.toLong())
        val downloader = Downloader(transport, policy)
        downloader.ensure(spec)
        downloader.ensure(spec)
        assertArrayEquals(payload, Files.readAllBytes(spec.target))
        assertEquals(1, requests.get())
    }

    @Test
    fun watchdogThreadNeverKeepsTheJvmAlive() {
        route("/one") { exchange ->
            exchange.sendResponseHeaders(200, 1)
            exchange.responseBody.write(1)
        }
        transport.get(uri("/one"), 0).use { it.body.readAllBytes() }
        val watchdogs = Thread.getAllStackTraces().keys.filter { it.name == "blockwright-download-watchdog" }
        assertTrue(watchdogs.isNotEmpty())
        assertTrue(watchdogs.all { it.isDaemon })
    }

    private companion object {
        // The idle timeout also bounds the headers, so it must outlast a loaded host delivering them; only the stall may trip it.
        const val IDLE_MS = 2_000L
    }
}
