package dev.blockwright.serverrunner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class BundlerPreparerTest {
    @TempDir
    lateinit var buildDir: Path

    private val runs = AtomicInteger()

    private fun cached(): CachedPaperJar {
        val jar = Files.writeString(buildDir.resolve("paper-26.3-262.jar"), "not a real jar")
        return CachedPaperJar(PaperBuild("26.3", 262, "STABLE", jar.fileName.toString(), "ab".repeat(32), 14, "https://fill-data.papermc.io/x", 25), jar, buildDir)
    }

    private fun preparer(mode: String) = BundlerPreparer { _, _, _ ->
        runs.incrementAndGet()
        FakeServers.command(mode)
    }

    @Test
    fun successfulPatchIsMarkedAndNotRepeated() {
        val cached = cached()

        val first = preparer("prepare-ok").prepare(JavaRequirement.currentJava(), cached)
        val second = preparer("prepare-ok").prepare(JavaRequirement.currentJava(), cached)

        assertEquals(buildDir.resolve("bundler"), first)
        assertEquals(first, second)
        assertTrue(Files.exists(first.resolve(".prepared")))
        assertTrue(Files.readString(first.resolve("prepare.log")).contains("Applying patches"))
        assertEquals(1, runs.get())
    }

    @Test
    fun failedPatchReportsTheLogTailAndLeavesNoMarker() {
        val error = assertThrows(ServerStartException::class.java) {
            preparer("prepare-fail").prepare(JavaRequirement.currentJava(), cached())
        }

        assertTrue(error.message!!.contains("exited with 1") && error.message!!.contains("patch failed: boom"), error.message)
        assertFalse(Files.exists(buildDir.resolve("bundler").resolve(".prepared")))
    }

    @Test
    fun hangingPatchIsKilledAfterTheTimeout() {
        val impatient = BundlerPreparer(timeout = 500.milliseconds) { _, _, _ -> FakeServers.command("never-ready") }

        val error = assertThrows(ServerStartException::class.java) { impatient.prepare(JavaRequirement.currentJava(), cached()) }

        assertTrue(error.message!!.contains("did not finish"), error.message)
        assertFalse(Files.exists(buildDir.resolve("bundler").resolve(".prepared")))
    }

    @Test
    fun defaultCommandPatchesOnlyIntoTheCachedBundlerDir() {
        val command = BundlerPreparer.patchOnlyCommand(Path.of("java"), Path.of("paper.jar"), Path.of("cache", "bundler"))

        assertTrue("-Dpaperclip.patchonly=true" in command)
        assertTrue("-DbundlerRepoDir=${Path.of("cache", "bundler")}" in command)
        assertTrue(command.none { it.contains("eula", ignoreCase = true) })
    }
}
