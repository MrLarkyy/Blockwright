package dev.blockwright.launcher.launch

import dev.blockwright.launcher.FakeTransport
import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.download.Sha1
import dev.blockwright.launcher.launch.fabric.FabricArtifact
import dev.blockwright.launcher.launch.fabric.FabricRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.listDirectoryEntries

/** Every refusal happens before a download, a file or a process: nothing is started and nothing is left behind. */
class ClientLauncherTest {
    @TempDir lateinit var dir: Path

    private val starts = AtomicInteger()
    private val fabricLoads = AtomicInteger()
    private val transport = FakeTransport()

    private fun launcher() = ClientLauncher(
        CacheLayout(dir.resolve("cache")),
        Downloader(transport, DownloadPolicy()) {},
        starter = { _, _ -> starts.incrementAndGet(); error("must not start") },
        fabric = { fabricLoads.incrementAndGet(); error("must not download") },
    )

    private fun config(agent: Path = LaunchFixtures.agentJar(dir), username: String = "bwlogic", java: Path? = null) =
        ClientLaunchConfig(LaunchFixtures.version(dir), agent, LaunchProfile.LOGIC, "127.0.0.1", 25565, username, dir.resolve("out"), javaExecutable = java)

    private fun assertNothingStarted() {
        assertEquals(0, starts.get())
        assertEquals(0, fabricLoads.get())
        assertEquals(0, transport.calls.size)
        assertTrue(Files.notExists(dir.resolve("out")))
    }

    @Test
    fun refusesAnInvalidUsername() {
        assertThrows<LaunchException> { launcher().launch(config(username = "no spaces")) }
        assertNothingStarted()
    }

    @Test
    fun refusesAnAgentBuiltForAnotherVersion() {
        val error = assertThrows<LaunchException> { launcher().launch(config(agent = LaunchFixtures.agentJar(dir, minecraft = "26.2"))) }
        assertTrue("26.2" in error.message!!, error.message)
        assertNothingStarted()
    }

    @Test
    fun refusesAJvmOlderThanTheVersionNeeds() {
        val home = Files.createDirectories(dir.resolve("jdk21/bin")).parent
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"21.0.1\"\n")
        val error = assertThrows<LaunchException> { launcher().launch(config(java = JavaRuntime.executableIn(home))) }
        assertTrue("needs Java 25" in error.message!!, error.message)
        assertNothingStarted()
    }

    @Test
    fun aProcessThatCannotStartLeavesNoRunDirectory() {
        val jar = "loader".toByteArray()
        val cache = CacheLayout(dir.resolve("cache"))
        Files.createDirectories(cache.library("a/b/1/b-1.jar").parent)
        Files.write(cache.library("a/b/1/b-1.jar"), jar)
        LaunchFixtures.version(dir).classpath.forEach { Files.createDirectories(it.parent); Files.write(it, ByteArray(1)) }
        val launcher = ClientLauncher(
            cache,
            Downloader(transport, DownloadPolicy()) {},
            starter = { _, _ -> starts.incrementAndGet(); throw java.io.IOException("cannot start") },
            fabric = { FabricRuntime("0.20.1", listOf(FabricArtifact("a:b:1", Sha1.of(jar), jar.size.toLong(), "https://maven.fabricmc.net/"))) },
        )
        val before = runDirs()

        assertThrows<java.io.IOException> { launcher.launch(config()) }

        assertEquals(1, starts.get())
        assertEquals(0, transport.calls.size)
        assertEquals(before, runDirs())
    }

    private fun runDirs(): Set<Path> =
        Path.of(System.getProperty("java.io.tmpdir")).listDirectoryEntries("${RunDirectory.PREFIX}*").toSet()
}
