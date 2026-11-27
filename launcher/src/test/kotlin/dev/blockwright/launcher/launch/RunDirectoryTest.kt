package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RunDirectoryTest {
    @TempDir lateinit var root: Path

    private val self = ProcessHandle.current()
    private val selfStart = self.info().startInstant().map { it.toEpochMilli().toString() }.orElse("-")

    private fun runDir(name: String, owners: String?): Path {
        val dir = Files.createDirectories(root.resolve(RunDirectory.PREFIX + name))
        Files.writeString(dir.resolve("options.txt"), "renderDistance:2\n")
        owners?.let { Files.writeString(dir.resolve(RunDirectory.OWNERS_FILE), it) }
        return dir
    }

    private fun deadPid(): Long {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(java, "-version").redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        process.waitFor()
        return process.pid()
    }

    @Test
    fun removesADirectoryWhoseOwnerAndClientAreGone() {
        val dead = deadPid()
        val gone = runDir("gone", "owner $dead -\nclient $dead -\n")
        val neverStarted = runDir("never-started", "owner $dead -\n")

        assertEquals(setOf(gone, neverStarted), RunDirectory.sweep(root).toSet())
        assertFalse(Files.exists(gone))
        assertFalse(Files.exists(neverStarted))
    }

    @Test
    fun keepsADirectoryWhileItsOwnerOrItsClientLives() {
        val dead = deadPid()
        val ownerAlive = runDir("owner-alive", "owner ${self.pid()} $selfStart\n")
        val clientAlive = runDir("client-alive", "owner $dead -\nclient ${self.pid()} $selfStart\n")

        assertEquals(emptyList<Path>(), RunDirectory.sweep(root))
        assertTrue(Files.exists(ownerAlive))
        assertTrue(Files.exists(clientAlive))
    }

    @Test
    fun aDirectoryMarkedKeptSurvivesASweepWithBothProcessesDead() {
        val dead = deadPid()
        val kept = runDir("kept", "owner $dead -\nclient $dead -\n")
        val abandoned = runDir("abandoned", "owner $dead -\nclient $dead -\n")
        RunDirectory.markKept(kept)

        assertEquals(listOf(abandoned), RunDirectory.sweep(root))
        assertTrue(Files.exists(kept))
        assertFalse(Files.exists(abandoned))
    }

    @Test
    fun aReusedPidWithAnotherStartTimeCountsAsGone() {
        val reused = runDir("reused", "owner ${self.pid()} 0\n")
        assertEquals(listOf(reused), RunDirectory.sweep(root))
    }

    @Test
    fun leavesDirectoriesWithoutARecordAndOthersAlone() {
        val unrecorded = runDir("unrecorded", null)
        val garbled = runDir("garbled", "owner\n")
        val other = Files.createDirectories(root.resolve("someone-else"))
        Files.writeString(other.resolve(RunDirectory.OWNERS_FILE), "owner ${deadPid()} -\n")

        assertEquals(emptyList<Path>(), RunDirectory.sweep(root))
        assertTrue(Files.exists(unrecorded))
        assertTrue(Files.exists(garbled))
        assertTrue(Files.exists(other))
    }

    @Test
    fun aNewRunDirectoryRecordsItsOwnerAndSurvivesASweep() {
        val dir = RunDirectory.create()
        try {
            assertTrue(Files.readString(dir.resolve(RunDirectory.OWNERS_FILE)).startsWith("owner ${self.pid()} "))
            RunDirectory.recordClient(dir, self)
            assertFalse(dir in RunDirectory.sweep(dir.parent))
            assertTrue(Files.exists(dir))
        } finally {
            RunDirectory.delete(dir)
        }
    }
}
