package dev.blockwright.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class McpModuleTest {
    @Test
    fun runsOnTheProjectToolchain() {
        assertEquals(25, Runtime.version().feature())
    }

    @Test
    fun livesInTheModulePackage() {
        assertEquals("dev.blockwright.mcp", javaClass.packageName)
    }
}
