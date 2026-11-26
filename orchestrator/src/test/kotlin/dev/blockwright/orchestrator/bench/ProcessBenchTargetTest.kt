package dev.blockwright.orchestrator.bench

import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class ProcessBenchTargetTest {
    private fun target(vararg args: String): ProcessBenchTarget {
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        return ProcessBenchTarget(
            "stub",
            Path.of(System.getProperty("java.home"), "bin", exe),
            System.getProperty("java.class.path"),
            "dev.blockwright.orchestrator.bench.StubClient",
            args.toList(),
        )
    }

    private fun descendants() = ProcessHandle.current().descendants().filter { it.isAlive }.map { it.pid() }.toList().toSet()

    @Test
    fun readyScreenshotAndLoadRoundTrip(@TempDir dir: Path) {
        val before = descendants()
        target("--alloc-mib", "4").launch(dir).use { client ->
            client.awaitReady(60.seconds)
            client.screenshot(10.seconds)
            client.startLoad()
            client.stopLoad()
            client.screenshot(10.seconds)
            assertTrue(ProcessHandle.of(client.pid).isPresent)
        }
        assertTrue(before.containsAll(descendants()), "no client process may outlive close()")
    }

    @Test
    fun aClientThatNeverGetsReadyTimesOutAndLeavesNothingRunning(@TempDir dir: Path) {
        val before = descendants()
        val client = target("--hang").launch(dir)
        try {
            assertThrows<BenchException> { client.awaitReady(500.milliseconds) }
        } finally {
            client.close()
        }
        assertTrue(before.containsAll(descendants()), "no client process may outlive close()")
    }

    @Test
    fun theClasspathTravelsInAnArgFileInsideTheClientDir(@TempDir dir: Path) {
        target().launch(dir).use { it.awaitReady(60.seconds) }
        val argFiles = Files.walk(dir).use { stream -> stream.filter { it.fileName.toString() == "java.args" }.toList() }
        assertTrue(argFiles.size == 1)
    }
}
