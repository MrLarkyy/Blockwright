package dev.blockwright.launcher.launch

import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.JdkHttpTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

/**
 * Game logic parity: the same scripted input on the same server, once with every cut off and a real window
 * (REFERENCE), once in the logic profile, must give the same per-tick movement. The reference needs a display:
 * without one the test is skipped, unless `-Pblockwright.requireParity=true` (CI) turns that into a failure.
 */
@Tag("integration")
class MovementParityIntegrationTest {
    private val policy = DownloadPolicy()

    @Test
    fun logicProfileMovesExactlyLikeTheReferenceClient() {
        val agent = RealClients.requireEulaAndAgent()
        if (!displayAvailable()) {
            if (System.getProperty("blockwright.requireParity") == "true") fail("no display for the reference client, and parity is required")
            assumeTrue(false, "no display for the reference client")
        }
        val version = RealClients.resolve()
        val dir = RealClients.artefacts.resolve("parity").toAbsolutePath()
        RealClients.startServer("parity").use { server ->
            JdkHttpTransport(policy).use { transport ->
                val launcher = RealClients.launcher(transport)
                val reference = trace(launcher, RealClients.config(version, agent, server, LaunchProfile.REFERENCE, "bwref", dir.resolve("reference"), TraceRequest(dir.resolve("reference.trace"))))
                val logic = trace(launcher, RealClients.config(version, agent, server, LaunchProfile.LOGIC, "bwlogic", dir.resolve("logic"), TraceRequest(dir.resolve("logic.trace"))))

                assertEquals(TraceRequest.WALK_JUMP_STRAFE_TICKS + 1, reference.size, "reference trace length")
                assertTrue(reference.drop(1).any { it.split(' ')[3].toDouble() != 0.0 }, "the reference player never moved")
                val differences = TraceComparison.differences(reference, logic)
                assertTrue(differences.isEmpty(), "movement differs:\n" + differences.take(10).joinToString("\n"))
            }
        }
    }

    private fun trace(launcher: ClientLauncher, config: ClientLaunchConfig): List<String> {
        val client = launcher.launch(config)
        try {
            client.awaitJoined(RealClients.JOIN_TIMEOUT)
            client.awaitState(AgentStatusLine.TRACED, 60.seconds)
        } finally {
            client.stop()
        }
        return Files.readAllLines(config.trace!!.output)
    }

    private fun displayAvailable(): Boolean {
        if (!System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) return true
        return listOf("DISPLAY", "WAYLAND_DISPLAY").any { !System.getenv(it).isNullOrBlank() }
    }
}
