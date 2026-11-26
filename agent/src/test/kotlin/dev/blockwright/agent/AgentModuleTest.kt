package dev.blockwright.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentModuleTest {
    @Test
    fun runsOnTheProjectToolchain() {
        assertEquals(25, Runtime.version().feature())
    }

    @Test
    fun livesInTheModulePackage() {
        assertEquals("dev.blockwright.agent", javaClass.packageName)
    }
}
