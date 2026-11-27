package dev.blockwright.agent.lifecycle

import dev.blockwright.agent.AgentStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class StartupFailuresTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val bytes = ByteArrayOutputStream()
    private val status = AgentStatus(PrintStream(bytes, true, Charsets.UTF_8), token)

    private fun lines() = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }

    @Test
    fun anErrorBoxBecomesAnAlertLineAndIsNotShown() {
        assertTrue(StartupFailures { status }.replacesErrorBox("Something went wrong.\nPlease update drivers"))
        assertEquals(listOf("BLOCKWRIGHT_AGENT $token state=alert reason=Something_went_wrong._Please_update_drivers"), lines())
    }

    @Test
    fun aWindowThatCannotBeCreatedFailsTheStartWithTheReason() {
        StartupFailures { status }.onWindowInitFailed("Failed to initialize graphics.\n\n- Tried OpenGL: Couldn't find matching GLX visual")
        val line = lines().single()
        assertTrue(line.startsWith("BLOCKWRIGHT_AGENT $token state=failed reason=Failed_to_initialize_graphics._-_Tried_OpenGL:"), line)
    }

    @Test
    fun aClientTheLauncherDidNotStartKeepsItsBoxesAndSaysNothing() {
        val failures = StartupFailures { null }
        assertFalse(failures.replacesErrorBox("box"))
        failures.onWindowInitFailed("gone")
        assertEquals(emptyList<String>(), lines())
    }
}
