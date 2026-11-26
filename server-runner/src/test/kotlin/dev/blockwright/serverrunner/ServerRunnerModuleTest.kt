package dev.blockwright.serverrunner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerRunnerModuleTest {
    @Test
    fun runsOnTheProjectToolchain() {
        assertEquals(25, Runtime.version().feature())
    }

    @Test
    fun livesInTheModulePackage() {
        assertEquals("dev.blockwright.serverrunner", javaClass.packageName)
    }
}
