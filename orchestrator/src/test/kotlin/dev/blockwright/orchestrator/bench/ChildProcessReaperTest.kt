package dev.blockwright.orchestrator.bench

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ChildProcessReaperTest {
    @Test
    fun reapAllKillsATrackedClientAndItsDescendants(@TempDir dir: Path) {
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        val target = ProcessBenchTarget(
            "stub",
            Path.of(System.getProperty("java.home"), "bin", exe),
            System.getProperty("java.class.path"),
            "dev.blockwright.orchestrator.bench.StubClient",
        )
        val client = target.launch(dir)
        try {
            client.awaitReady(60.seconds)
            val handle = ProcessHandle.of(client.pid).orElseThrow()
            val family = listOf(handle) + handle.descendants().toList()

            ChildProcessReaper.reapAll()

            family.forEach { it.onExit().get(10, TimeUnit.SECONDS) }
            assertFalse(family.any { it.isAlive })
        } finally {
            client.close()
        }
    }
}
