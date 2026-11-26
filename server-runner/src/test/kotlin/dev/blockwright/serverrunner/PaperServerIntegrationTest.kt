package dev.blockwright.serverrunner

import dev.blockwright.serverrunner.fill.FillApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds

/**
 * Downloads and starts a real Paper server. Runs only in `integrationTest` and only with the user's explicit
 * `-Pblockwright.acceptEula=true`; otherwise it is reported as skipped, never as passed.
 */
@Tag("integration")
class PaperServerIntegrationTest {
    private val cacheRoot: Path = System.getProperty("blockwright.it.cacheDir")?.let(Path::of) ?: Path.of("build", "blockwright-cache")
    private val artefacts: Path = Path.of("build", "blockwright-artifacts")

    @Test
    fun realPaperServerStartsReportsReadyAndStopsCleanly() {
        assumeTrue(System.getProperty("blockwright.acceptEula") == "true", "the Minecraft EULA was not accepted with -Pblockwright.acceptEula=true")
        val resolves = AtomicInteger()
        val fill = FillApiClient()
        val runner = PaperServerRunner(source = { version, build -> resolves.incrementAndGet(); fill.resolve(version, build) })
        val config = ServerRunConfig(
            version = VERSION,
            build = BUILD,
            cacheRoot = cacheRoot,
            artifactDir = artefacts.resolve("first"),
            acceptEula = true,
            readyTimeout = 180.seconds,
        )

        val first = runner.start(config)
        val pid = first.pid
        val descendants = ProcessHandle.of(pid).map { handle -> handle.descendants().map { it.pid() }.toList() }.orElse(emptyList())
        val result = try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", first.port), 5_000) }
            first.stop()
        } finally {
            first.stop()
        }

        assertFalse(FakeServers.isAlive(pid))
        descendants.forEach { assertFalse(FakeServers.isAlive(it)) }
        assertFalse(result.runDirKept)
        assertFalse(Files.exists(first.runDir))
        assertTrue(Files.readString(result.consoleLog).contains("Done ("))
        assertTrue(Files.readString(result.latestLog!!).contains("Done ("))

        val buildDir = cacheRoot.toAbsolutePath().normalize().resolve("paper").resolve(VERSION).resolve(BUILD.toString())
        assertTrue(Files.exists(buildDir.resolve("bundler").resolve(".prepared")))
        assertTrue(buildDir.listDirectoryEntries().none { it.name.endsWith(".part") })
        resolves.set(0)

        runner.start(config.copy(artifactDir = artefacts.resolve("second"))).use { it.stop() }

        assertEquals(0, resolves.get())
    }

    private companion object {
        const val VERSION = "26.3"
        const val BUILD = 262
    }
}
