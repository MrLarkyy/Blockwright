package dev.blockwright.orchestrator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrchestratorModuleTest {
    @Test
    fun runsOnTheProjectToolchain() {
        assertEquals(25, Runtime.version().feature())
    }

    @Test
    fun livesInTheModulePackage() {
        assertEquals("dev.blockwright.orchestrator", javaClass.packageName)
    }
}
