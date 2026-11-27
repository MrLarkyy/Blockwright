package dev.blockwright.launcher.launch

import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.JdkHttpTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A real 26.3 client in the logic profile against a real Paper server: joins without display, GPU, sound or render
 * assets, moves, and stops gracefully. Runs only in `integrationTest` with `-Pblockwright.acceptEula=true`.
 */
@Tag("integration")
class HeadlessClientIntegrationTest {
    private val policy = DownloadPolicy()

    @Test
    fun logicClientJoinsHeadlessMovesAndStopsGracefully() {
        val agent = RealClients.requireEulaAndAgent()
        val version = RealClients.resolve()
        val dir = RealClients.artefacts.resolve("headless")
        RealClients.startServer("headless").use { server ->
            val joinedOnServer = CompletableFuture<String>()
            val leftOnServer = CompletableFuture<String>()
            server.onLine { line ->
                if ("$USER joined the game" in line) joinedOnServer.complete(line)
                if ("$USER left the game" in line) leftOnServer.complete(line)
            }
            JdkHttpTransport(policy).use { transport ->
                val trace = TraceRequest(dir.resolve("client").resolve("trace.txt").toAbsolutePath())
                val client = RealClients.launcher(transport)
                    .launch(RealClients.config(version, agent, server, LaunchProfile.LOGIC, USER, dir.resolve("client"), trace))
                try {
                    val started = TimeSource.Monotonic.markNow()
                    val joined = client.awaitJoined(RealClients.JOIN_TIMEOUT)
                    assertEquals("dummy", joined["video"], joined.toString())
                    assertEquals("Blockwright_headless", joined["gpu"], joined.toString())
                    assertEquals("off", joined["sound"], joined.toString())
                    assertEquals("0", joined["assetReads"], joined.toString())
                    joinedOnServer.get(30, TimeUnit.SECONDS)

                    client.awaitState(AgentStatusLine.TRACED, 60.seconds)
                    val lines = Files.readAllLines(trace.output).drop(1)
                    assertEquals(110, lines.size)
                    assertTrue(lines.any { it.split(' ')[1].toDouble() != 0.0 || it.split(' ')[3].toDouble() != 0.0 }, "the player never moved")

                    val stay = 30.seconds - started.elapsedNow().coerceAtMost(30.seconds) + 5.seconds
                    Thread.sleep(stay.inWholeMilliseconds)
                    assertTrue(client.isAlive, "the client died while in the world")

                    val pid = client.pid
                    val descendants = ProcessHandle.of(pid).map { h -> h.descendants().map { it.pid() }.toList() }.orElse(emptyList())
                    val result = client.stop()

                    assertFalse(result.forcedKill, "the client did not stop within the stop timeout")
                    assertEquals(0, result.exitCode)
                    leftOnServer.get(15, TimeUnit.SECONDS)
                    assertFalse(RealClients.isAlive(pid))
                    descendants.forEach { assertFalse(RealClients.isAlive(it)) }
                    assertFalse(result.runDirKept)
                    assertFalse(Files.exists(client.runDir))

                    val console = Files.readString(result.consoleLog)
                    assertFalse("Crash report" in console || "---- Minecraft Crash Report" in console, "crash in console.log")
                    assertFalse(Regex("\\bException\\b").containsMatchIn(console), "an exception in console.log")
                    assertFalse("Sound engine started" in console, "the sound engine started")
                    assertFalse(Regex("Created: \\d+x\\d+").containsMatchIn(console), "a texture atlas was built")
                } finally {
                    client.stop()
                }
            }
        }
    }

    @Test
    fun aRefusedJoinFailsWithTheClientsReasonBeforeTheTimeout() {
        val agent = RealClients.requireEulaAndAgent()
        val version = RealClients.resolve()
        val dir = RealClients.artefacts.resolve("refused")
        RealClients.startServer("refused").use { server ->
            server.console("whitelist on")
            JdkHttpTransport(policy).use { transport ->
                val client = RealClients.launcher(transport)
                    .launch(RealClients.config(version, agent, server, LaunchProfile.LOGIC, "bwrefused", dir.resolve("client")))
                try {
                    val started = TimeSource.Monotonic.markNow()
                    val error = assertThrows<ClientStartException> { client.awaitJoined(RealClients.JOIN_TIMEOUT) }
                    assertTrue(started.elapsedNow() < RealClients.JOIN_TIMEOUT, "the refusal waited for the timeout")
                    assertTrue("'failed'" in error.message!!, error.message)
                    assertTrue(error.message!!.contains("white", ignoreCase = true), error.message)
                    assertFalse(RealClients.isAlive(client.pid))
                } finally {
                    client.stop()
                }
            }
        }
    }

    private companion object {
        const val USER = "bwlogic"
    }
}
