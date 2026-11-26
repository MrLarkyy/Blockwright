package dev.blockwright.orchestrator.bench

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

class PssProbeTest {
    private val pid = ProcessHandle.current().pid()

    @Test
    fun parsesASmapsRollupFixture() {
        val text = "Rss:  4000 kB\nPss:  2048 kB\nPss_Anon:  100 kB\nShared_Clean: 5 kB\n"
        assertEquals(2048, PssProbe.parse(text))
    }

    @Test
    fun smapsFallbackSumsEveryMapping(@TempDir proc: Path) {
        val dir = Files.createDirectories(proc.resolve(pid.toString()))
        Files.writeString(dir.resolve("smaps"), "Pss:   1024 kB\nPss_Anon: 7 kB\nPss:   1024 kB\n")
        val measured = PssProbe("Linux", proc).measure(pid)
        assertEquals(2.0, assertInstanceOf(Measurement.Value::class.java, measured).aggregate)
    }

    @Test
    fun rollupWinsOverSmaps(@TempDir proc: Path) {
        val dir = Files.createDirectories(proc.resolve(pid.toString()))
        Files.writeString(dir.resolve("smaps_rollup"), "Pss: 3072 kB\n")
        val measured = PssProbe("Linux", proc).measure(pid)
        assertEquals(3.0, assertInstanceOf(Measurement.Value::class.java, measured).aggregate)
    }

    @Test
    fun unreadableProcOnLinuxFails(@TempDir proc: Path) {
        assertInstanceOf(Measurement.Failed::class.java, PssProbe("Linux", proc).measure(pid))
    }

    @Test
    fun nonLinuxIsNotRunWithAReason() {
        val measured = assertInstanceOf(Measurement.NotRun::class.java, PssProbe("Windows 11").measure(pid))
        assertTrue("Linux" in measured.reason)
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun currentJvmHasPositivePssOnLinux() {
        val measured = assertInstanceOf(Measurement.Value::class.java, PssProbe().measure(pid))
        assertTrue(measured.aggregate > 0)
    }
}
