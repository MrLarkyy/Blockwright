package dev.blockwright.serverrunner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.time.Duration.Companion.seconds

class BoundedHttpClientTest {
    private val server = FixtureHttpServer()

    @AfterEach
    fun close() = server.close()

    @Test
    fun fillPolicyAllowsOnlyHttpsOnFillHosts() {
        UrlPolicy.FILL.check(URI("https://fill.papermc.io/v3/projects/paper"))
        UrlPolicy.FILL.check(URI("https://fill-data.papermc.io/v1/objects/x/paper.jar"))
        listOf(
            "http://fill-data.papermc.io/v1/objects/x/paper.jar",
            "https://evil.example/paper.jar",
            "https://fill-data.papermc.io.evil.example/paper.jar",
            "https://user@fill-data.papermc.io/paper.jar",
            "file:///etc/passwd",
        ).forEach { url ->
            assertThrows(PaperDownloadException::class.java) { UrlPolicy.FILL.check(URI(url)) }
        }
    }

    @Test
    fun redirectToAnAllowedHostIsFollowed() {
        server.bytes("/target", byteArrayOf(1, 2, 3))
        server.route("/start") { exchange ->
            exchange.responseHeaders.add("Location", "/target")
            FixtureHttpServer.respond(exchange, 302, ByteArray(0))
        }
        val sink = ByteArrayOutputStream()

        server.http.download(server.uri("/start"), 3, 5.seconds, sink)

        assertArrayEquals(byteArrayOf(1, 2, 3), sink.toByteArray())
    }

    @Test
    fun redirectToAForeignHostIsRefusedWithoutRequestingIt() {
        server.route("/start") { exchange ->
            exchange.responseHeaders.add("Location", "https://evil.example/paper.jar")
            FixtureHttpServer.respond(exchange, 302, ByteArray(0))
        }

        val error = assertThrows(PaperDownloadException::class.java) {
            server.http.download(server.uri("/start"), 3, 5.seconds, ByteArrayOutputStream())
        }

        assertTrue(error.message!!.contains("evil.example"), error.message)
    }

    @Test
    fun redirectLoopIsBounded() {
        server.route("/loop") { exchange ->
            exchange.responseHeaders.add("Location", "/loop")
            FixtureHttpServer.respond(exchange, 302, ByteArray(0))
        }

        assertThrows(PaperDownloadException::class.java) {
            server.http.download(server.uri("/loop"), 3, 5.seconds, ByteArrayOutputStream())
        }
        assertEquals(4, server.requests("/loop"))
    }

    @Test
    fun errorStatusEchoesAShortBody() {
        server.text("/gone", "x".repeat(1000), status = 410)

        val error = assertThrows(PaperDownloadException::class.java) {
            server.http.download(server.uri("/gone"), 10, 5.seconds, ByteArrayOutputStream())
        }

        assertTrue(error.message!!.contains("410"), error.message)
        assertTrue(error.message!!.length < 400, error.message)
    }

    @Test
    fun chunkedBodyOverTheCapIsAborted() {
        server.route("/chunked") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out -> repeat(64) { out.write(ByteArray(1024)) } }
        }
        val sink = ByteArrayOutputStream()

        assertThrows(PaperDownloadException::class.java) { server.http.download(server.uri("/chunked"), 4096, 5.seconds, sink) }
        assertTrue(sink.size() <= 4096)
    }
}
