package dev.blockwright.serverrunner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ServerProcessTest {
    @TempDir
    lateinit var temp: Path

    private val started = CopyOnWriteArrayList<ServerProcess>()
    private val processes = CopyOnWriteArrayList<Process>()
    private val launcher = ProcessLauncher { _, workDir -> JvmProcessLauncher().launch(currentCommand, workDir).also { processes += it } }
    private var currentCommand: List<String> = emptyList()

    @AfterEach
    fun cleanup() {
        started.forEach { it.stop() }
        processes.forEach { ProcessTree.kill(it) }
    }

    private fun runDir(): Path = temp.resolve("run")

    private fun artefacts(): Path = temp.resolve("artefacts")

    private fun start(
        mode: String,
        readyTimeout: Duration = 30.seconds,
        stopTimeout: Duration = 10.seconds,
        keepRunDir: Boolean = false,
    ): ServerProcess {
        currentCommand = FakeServers.command(mode)
        Files.createDirectories(runDir())
        return ServerProcess.start(launcher, currentCommand, runDir(), artefacts(), 25601, readyTimeout, stopTimeout, keepRunDir)
            .also { started += it }
    }

    @Test
    fun readyServerStopsGracefullyAndKeepsItsLogs() {
        val server = start("ready")
        assertTrue(FakeServers.isAlive(server.pid))
        assertTrue(server.shutdownHookInstalled)

        val result = server.stop()

        assertEquals(0, result.exitCode)
        assertFalse(result.forcedKill)
        assertFalse(FakeServers.isAlive(server.pid))
        assertFalse(server.shutdownHookInstalled)
        assertFalse(Files.exists(runDir()))
        assertFalse(result.runDirKept)
        val console = Files.readString(result.consoleLog)
        assertTrue(console.contains(FakePaperServer.READY_LINE) && console.contains("Stopping server"), console)
        assertEquals(artefacts().resolve("latest.log"), result.latestLog)
        assertTrue(Files.readString(result.latestLog!!).contains("Done ("))
    }

    @Test
    fun stopIsIdempotent() {
        val server = start("ready")

        val first = server.stop()

        assertSame(first, server.stop())
        server.close()
    }

    @Test
    fun serverIgnoringStopIsKilledAfterTheTimeout() {
        val server = start("ignore-stop", stopTimeout = 1.seconds)

        val result = server.stop()

        assertTrue(result.forcedKill)
        assertFalse(FakeServers.isAlive(server.pid))
        assertFalse(Files.exists(runDir()))
    }

    @Test
    fun childProcessesDoNotSurvive() {
        val server = start("spawn-child")
        val childPid = Files.readAllLines(server.consoleLog).first { it.startsWith("child ") }.removePrefix("child ").trim().toLong()
        assertTrue(FakeServers.isAlive(childPid))

        server.stop()

        assertFalse(FakeServers.isAlive(server.pid))
        assertFalse(FakeServers.isAlive(childPid))
    }

    @Test
    fun earlyExitFailsFastWithExitCodeAndTail() {
        val begin = System.nanoTime()

        val error = assertThrows(ServerStartException::class.java) { start("exit-early") }

        assertTrue(error.message!!.contains("code 3") && error.message!!.contains("FAILED TO BIND TO PORT"), error.message)
        assertTrue(System.nanoTime() - begin < TimeUnit.SECONDS.toNanos(20))
        assertFalse(processes.single().isAlive)
        assertTrue(Files.readString(artefacts().resolve("console.log")).contains("FAILED TO BIND TO PORT"))
        assertFalse(Files.exists(runDir()))
    }

    @Test
    fun missingReadinessTimesOutAndLeavesNothingRunning() {
        val error = assertThrows(ServerStartException::class.java) { start("never-ready", readyTimeout = 2.seconds) }

        assertTrue(error.message!!.contains("did not report ready") && error.message!!.contains("Loading libraries"), error.message)
        assertFalse(processes.single().isAlive)
        assertFalse(Files.exists(runDir()))
    }

    @Test
    fun consoleCommandsReachTheServerAndOutputReachesListeners() {
        val server = start("echo")
        val echoed = CompletableFuture<String>()
        server.onLine { if (it.startsWith("echo: ")) echoed.complete(it) }

        server.console("say hi")

        assertEquals("echo: say hi", echoed.get(10, TimeUnit.SECONDS))
        server.stop()
        assertTrue(Files.readString(server.consoleLog).contains("echo: say hi"))
    }

    @Test
    fun consoleRejectsLineBreaksAndUseAfterStop() {
        val server = start("ready")

        assertThrows(IllegalArgumentException::class.java) { server.console("say hi\nstop") }
        server.stop()
        assertThrows(IllegalStateException::class.java) { server.console("say hi") }
    }

    @Test
    fun shutdownHookPathKillsTheTreeKeepsTheLogAndRemovesTheRunDir() {
        val server = start("spawn-child")
        val childPid = Files.readAllLines(server.consoleLog).first { it.startsWith("child ") }.removePrefix("child ").trim().toLong()

        server.onShutdown()

        assertFalse(FakeServers.isAlive(server.pid))
        assertFalse(FakeServers.isAlive(childPid))
        assertFalse(Files.exists(runDir()))
        assertTrue(Files.readString(artefacts().resolve("latest.log")).contains("Done ("))
        assertTrue(server.stop().latestLog != null)
    }

    @Test
    fun interruptedStopStillCleansUpAndKeepsTheInterrupt() {
        val server = start("ignore-stop", stopTimeout = 30.seconds)
        val begin = System.nanoTime()

        Thread.currentThread().interrupt()
        val result = server.stop()

        assertTrue(Thread.interrupted())
        assertTrue(result.forcedKill)
        assertFalse(FakeServers.isAlive(server.pid))
        assertFalse(Files.exists(runDir()))
        assertFalse(server.shutdownHookInstalled)
        assertTrue(System.nanoTime() - begin < TimeUnit.SECONDS.toNanos(20))
    }

    @Test
    fun runDirIsKeptOnRequest() {
        val result = start("ready", keepRunDir = true).stop()

        assertTrue(result.runDirKept)
        assertTrue(Files.isDirectory(runDir()))
    }
}
