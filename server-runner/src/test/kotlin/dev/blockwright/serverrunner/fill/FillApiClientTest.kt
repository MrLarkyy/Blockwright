package dev.blockwright.serverrunner.fill

import dev.blockwright.serverrunner.BoundedHttpClient
import dev.blockwright.serverrunner.FixtureHttpServer
import dev.blockwright.serverrunner.PaperDownloadException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class FillApiClientTest {
    private val server = FixtureHttpServer()
    private val base = "/v3/projects/paper/"
    private val sha = "ab".repeat(32)

    @AfterEach
    fun close() = server.close()

    private fun fixture(name: String, download: String = server.uri("/files/paper.jar").toString(), sha256: String = sha): String =
        javaClass.getResource("/fill/$name")!!.readText()
            .replace("{{build262}}", javaClass.getResource("/fill/build-262.json")!!.readText())
            .replace("{{download}}", download)
            .replace("{{sha256}}", sha256)

    private fun client(http: BoundedHttpClient = server.http, maxBodyBytes: Long = 4L * 1024 * 1024) =
        FillApiClient(http, server.uri(base), maxBodyBytes)

    private fun serveVersion() = server.text("${base}versions/26.3", fixture("version-26.3.json"))

    // The fixture lists builds oldest first: the choice must not depend on Fill's order.
    @Test
    fun newestStableBuildWinsOverANewerBetaAndAnOlderStable() {
        serveVersion()
        server.text("${base}versions/26.3/builds", fixture("builds-26.3.json"))

        val build = client().resolve("26.3", null)

        assertEquals(262, build.build)
        assertEquals("STABLE", build.channel)
        assertEquals("paper-26.3-262.jar", build.fileName)
        assertEquals(sha, build.sha256)
        assertEquals(55_300_000L, build.size)
        assertEquals(25, build.javaMinimum)
    }

    @Test
    fun explicitBuildIsFetchedDirectly() {
        serveVersion()
        server.text("${base}versions/26.3/builds/262", fixture("build-262.json"))

        val build = client().resolve("26.3", 262)

        assertEquals(262, build.build)
        assertEquals(0, server.requests("${base}versions/26.3/builds"))
    }

    @Test
    fun unknownBuildNamesFillsError() {
        serveVersion()
        server.text("${base}versions/26.3/builds/999", fixture("build-not-found.json"), status = 404)

        val error = assertThrows(PaperDownloadException::class.java) { client().resolve("26.3", 999) }

        assertTrue(error.message!!.contains("build_not_found"), error.message)
    }

    @Test
    fun unknownVersionIsReported() {
        server.text("${base}versions/9.9", fixture("version-not-found.json"), status = 404)

        val error = assertThrows(PaperDownloadException::class.java) { client().resolve("9.9", null) }

        assertTrue(error.message!!.contains("404") && error.message!!.contains("version_not_found"), error.message)
    }

    @Test
    fun invalidVersionSendsNoRequest() {
        listOf("../26.3", "..", "26.3/../x", "26 3", "").forEach { version ->
            assertThrows(IllegalArgumentException::class.java) { client().resolve(version, null) }
        }
        assertEquals(0, server.totalRequests())
    }

    @Test
    fun bodyOverTheCapIsAborted() {
        server.text("${base}versions/26.3", fixture("version-26.3.json") + " ".repeat(2048))

        val error = assertThrows(PaperDownloadException::class.java) { client(maxBodyBytes = 1024).resolve("26.3", null) }

        assertTrue(error.message!!.contains("exceeds"), error.message)
    }

    @Test
    fun slowServerTimesOut() {
        server.route("${base}versions/26.3") { exchange ->
            Thread.sleep(3_000)
            FixtureHttpServer.respond(exchange, 200, fixture("version-26.3.json").toByteArray())
        }
        val impatient = BoundedHttpClient(server.policy, readTimeout = 300.milliseconds)

        val error = assertThrows(PaperDownloadException::class.java) { client(impatient).resolve("26.3", null) }

        assertTrue(error.message!!.contains("timed out"), error.message)
    }

    @Test
    fun downloadUrlOnAForeignHostIsRejected() {
        serveVersion()
        server.text("${base}versions/26.3/builds/262", fixture("build-262.json", download = "https://evil.example/paper.jar"))

        val error = assertThrows(PaperDownloadException::class.java) { client().resolve("26.3", 262) }

        assertTrue(error.message!!.contains("evil.example"), error.message)
    }

    @Test
    fun badChecksumIsRejected() {
        serveVersion()
        server.text("${base}versions/26.3/builds/262", fixture("build-262.json", sha256 = "not-a-sha"))

        assertThrows(PaperDownloadException::class.java) { client().resolve("26.3", 262) }
    }

    @Test
    fun traversalFileNameIsRejected() {
        serveVersion()
        server.text("${base}versions/26.3/builds/262", fixture("build-262.json").replace("paper-26.3-262.jar", "../x.jar"))

        val error = assertThrows(PaperDownloadException::class.java) { client().resolve("26.3", 262) }

        assertTrue(error.message!!.contains("file name"), error.message)
    }

    @Test
    fun missingServerDownloadIsRejected() {
        serveVersion()
        server.text("${base}versions/26.3/builds/262", fixture("build-262.json").replace("server:default", "server:mojmap"))

        assertThrows(PaperDownloadException::class.java) { client().resolve("26.3", 262) }
    }
}
