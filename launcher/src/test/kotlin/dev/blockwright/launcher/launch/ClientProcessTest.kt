package dev.blockwright.launcher.launch

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ClientProcessTest {
    @TempDir lateinit var dir: Path

    private val started = mutableListOf<ClientProcess>()

    @AfterEach
    fun stopAll() {
        started.forEach { it.stop() }
    }

    private fun start(mode: String, stopTimeout: Duration = 10.seconds, keepRunDir: Boolean = false): ClientProcess {
        val runDir = Files.createTempDirectory(dir, "run-")
        Files.createDirectories(runDir.resolve(ClientProcess.GAME_DIR).resolve("logs"))
        Files.writeString(runDir.resolve(ClientProcess.GAME_DIR).resolve("logs").resolve("latest.log"), "minecraft log\n")
        val arguments = listOf("-cp", System.getProperty("java.class.path"), FakeClientMain::class.java.name, mode)
        val command = LaunchCommand.write(JavaRuntime.current().executable, runDir.resolve("client.args"), arguments)
        return ClientProcess.start(ClientProcessStarter.JVM, command, runDir, dir.resolve("artifacts-$mode"), stopTimeout, keepRunDir)
            .also { started += it }
    }

    @Test
    fun joinedThenStdinEofStopsGracefully() {
        val client = start("join")
        val joined = client.awaitJoined(30.seconds)
        assertEquals("dummy", joined["video"])

        val result = client.stop()

        assertFalse(result.forcedKill)
        assertEquals(0, result.exitCode)
        assertFalse(client.isAlive)
        assertTrue(Files.readString(result.consoleLog).contains("state=stopping reason=stdin-closed"))
        assertEquals("minecraft log\n", Files.readString(result.latestLog!!))
        assertFalse(result.runDirKept)
        assertFalse(Files.exists(client.runDir))
    }

    @Test
    fun aRefusedJoinFailsFastWithTheClientsReason() {
        val client = start("refuse")
        val begin = TimeSource.Monotonic.markNow()
        val error = assertThrows<ClientStartException> { client.awaitJoined(60.seconds) }
        assertTrue(begin.elapsedNow() < 30.seconds)
        assertTrue("You_are_not_whitelisted_on_this_server!" in error.message!!, error.message)
        assertNotNull(error.result)
        assertFalse(client.isAlive)
    }

    @Test
    fun anExitBeforeJoiningFailsWithTheExitCodeAndLastLines() {
        val client = start("exit")
        val error = assertThrows<ClientStartException> { client.awaitJoined(60.seconds) }
        assertTrue("exited with code 3" in error.message!!, error.message)
        assertTrue("crashing on purpose" in error.message!!, error.message)
    }

    @Test
    fun aSilentClientTimesOutAndIsStopped() {
        val client = start("silent", stopTimeout = 200.milliseconds)
        val error = assertThrows<ClientStartException> { client.awaitJoined(500.milliseconds) }
        assertTrue("did not report 'joined'" in error.message!!, error.message)
        assertFalse(client.isAlive)
        assertTrue(error.result!!.forcedKill)
    }

    @Test
    fun aClientIgnoringTheStopIsKilledWithItsChildren() {
        val client = start("ignore-stop", stopTimeout = 500.milliseconds)
        val child = CompletableFuture<Long>()
        client.onLine { line -> if (line.startsWith("CHILD ")) child.complete(line.removePrefix("CHILD ").trim().toLong()) }
        client.awaitJoined(30.seconds)
        val childPid = child.get(30, TimeUnit.SECONDS)

        val result = client.stop()

        assertTrue(result.forcedKill)
        assertFalse(client.isAlive)
        assertFalse(ProcessHandle.of(childPid).map { it.isAlive }.orElse(false))
    }

    @Test
    fun stopIsIdempotent() {
        val client = start("join")
        client.awaitJoined(30.seconds)
        val first = client.stop()
        assertSame(first, client.stop())
        client.close()
        assertFalse(client.shutdownHookInstalled)
    }

    @Test
    fun anInterruptedStopStillKillsCleansUpAndKeepsTheFlag() {
        val client = start("ignore-stop", stopTimeout = 60.seconds)
        client.awaitJoined(30.seconds)
        Thread.currentThread().interrupt()
        val result = try {
            client.stop()
        } finally {
            assertTrue(Thread.interrupted(), "the interrupt flag was lost")
        }
        assertTrue(result.forcedKill)
        assertFalse(client.isAlive)
        assertFalse(Files.exists(client.runDir))
    }

    @Test
    fun theShutdownHookReleasesProcessAndRunDirectory() {
        val client = start("ignore-stop")
        client.awaitJoined(30.seconds)
        assertTrue(client.shutdownHookInstalled)

        client.onShutdown()

        assertFalse(client.isAlive)
        assertFalse(Files.exists(client.runDir))
    }

    @Test
    fun theRunDirectoryIsKeptOnRequest() {
        val client = start("join", keepRunDir = true)
        client.awaitJoined(30.seconds)
        val result = client.stop()
        assertTrue(result.runDirKept)
        assertTrue(Files.exists(client.runDir))
    }
}
