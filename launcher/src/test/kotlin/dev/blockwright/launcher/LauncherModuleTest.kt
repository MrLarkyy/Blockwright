package dev.blockwright.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LauncherModuleTest {
    @Test
    fun runsOnTheProjectToolchain() {
        assertEquals(25, Runtime.version().feature())
    }

    @Test
    fun livesInTheModulePackage() {
        assertEquals("dev.blockwright.launcher", javaClass.packageName)
    }
}
