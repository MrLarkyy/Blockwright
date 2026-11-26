package dev.blockwright.serverrunner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PaperServerRunnerTest {
    @TempDir
    lateinit var temp: Path

    private lateinit var fixture: RunnerFixture

    @BeforeEach
    fun setUp() {
        fixture = RunnerFixture(temp)
    }

    @AfterEach
    fun tearDown() = fixture.close()

    private fun plugin(): Path {
        val jar = temp.resolve("test-plugin.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            zip.putNextEntry(ZipEntry("plugin.yml"))
            zip.write("name: Test\nmain: test.Main\nversion: 1\n".toByteArray())
            zip.closeEntry()
        }
        return jar
    }

    @Test
    fun startsAnOfflineLocalhostServerWithThePlugins() {
        val server = fixture.start(fixture.config(acceptEula = true, plugins = listOf(plugin())))

        val properties = Properties().apply { load(StringReader(Files.readString(server.runDir.resolve("server.properties")))) }
        assertEquals("false", properties.getProperty("online-mode"))
        assertEquals("127.0.0.1", properties.getProperty("server-ip"))
        assertEquals(server.port.toString(), properties.getProperty("server-port"))
        assertTrue(Files.isRegularFile(server.runDir.resolve("plugins").resolve("test-plugin.jar")))

        val result = server.stop()

        assertEquals(0, result.exitCode)
        assertFalse(Files.exists(server.runDir))
        assertTrue(Files.isRegularFile(fixture.artifactDir.resolve("console.log")))
    }

    @Test
    fun launchCommandRunsThePatchedJarFromTheCache() {
        fixture.start(fixture.config(acceptEula = true)).stop()

        val command = fixture.launcher.commands.single()
        val buildDir = fixture.cacheRoot.toAbsolutePath().normalize().resolve("paper").resolve("26.3").resolve("262")
        assertTrue("-DbundlerRepoDir=${buildDir.resolve("bundler")}" in command, command.toString())
        assertTrue(buildDir.resolve("paper-26.3.jar").toString() in command, command.toString())
        assertEquals("--nogui", command.last())
        assertTrue(Files.exists(buildDir.resolve("bundler").resolve(".prepared")))
    }

    @Test
    fun secondStartOfAPinnedBuildUsesTheCache() {
        fixture.start(fixture.config(acceptEula = true)).stop()
        val requests = fixture.server.totalRequests()

        fixture.start(fixture.config(acceptEula = true)).stop()

        assertEquals(requests, fixture.server.totalRequests())
        assertEquals(1, fixture.resolves.get())
    }

    @Test
    fun invalidPluginFailsBeforeAnyDownload() {
        val notAPlugin = Files.writeString(temp.resolve("notes.txt"), "x")

        assertThrows(ServerRunnerException::class.java) { fixture.runner.start(fixture.config(acceptEula = true, plugins = listOf(notAPlugin))) }

        assertEquals(0, fixture.resolves.get())
        assertTrue(fixture.launcher.commands.isEmpty())
    }

    @Test
    fun failedStartRemovesTheRunDir() {
        RunnerFixture(temp.resolve("early"), mode = "exit-early").use { early ->
            assertThrows(ServerStartException::class.java) { early.runner.start(early.config(acceptEula = true)) }

            assertTrue(early.runDirsCreated.none { Files.exists(it) })
        }
    }
}
