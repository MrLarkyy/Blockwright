package dev.blockwright.serverrunner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** The EULA is accepted only by the user's explicit flag; tests record the acceptance instead of writing it. */
class EulaGateTest {
    @TempDir
    lateinit var temp: Path

    private lateinit var fixture: RunnerFixture

    @BeforeEach
    fun setUp() {
        fixture = RunnerFixture(temp)
    }

    @AfterEach
    fun tearDown() = fixture.close()

    @Test
    fun withoutAcceptanceNothingIsRequestedWrittenOrStarted() {
        assertThrows(EulaNotAcceptedException::class.java) { fixture.runner.start(fixture.config(acceptEula = false)) }

        assertEquals(0, fixture.resolves.get())
        assertEquals(0, fixture.server.totalRequests())
        assertTrue(fixture.launcher.commands.isEmpty())
        assertTrue(fixture.runDirsCreated.isEmpty())
        assertTrue(fixture.eulaWrites.isEmpty())
        assertFalse(Files.exists(fixture.cacheRoot))
        assertFalse(Files.exists(fixture.artifactDir))
    }

    @Test
    fun explicitAcceptanceWritesTheEulaIntoTheRunDirOnly() {
        val server = fixture.start(fixture.config(acceptEula = true))

        assertEquals(listOf(server.runDir), fixture.eulaWrites)
        server.stop()
    }

    @Test
    fun launchCommandNeverAcceptsTheEula() {
        fixture.start(fixture.config(acceptEula = true)).stop()

        val command = fixture.launcher.commands.single()
        assertTrue(command.none { it.contains("eula", ignoreCase = true) }, command.toString())
    }
}
