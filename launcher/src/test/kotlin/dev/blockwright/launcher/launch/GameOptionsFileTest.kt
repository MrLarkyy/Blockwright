package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GameOptionsFileTest {
    @TempDir lateinit var dir: Path

    @Test
    fun writesASmallViewWithoutFirstRunScreensOrSound() {
        val file = GameOptionsFile.write(dir.resolve("game"))
        val options = Files.readAllLines(file).associate { it.substringBefore(':') to it.substringAfter(':') }
        assertEquals("2", options["renderDistance"])
        assertEquals("5", options["simulationDistance"])
        assertEquals("false", options["pauseOnLostFocus"])
        assertEquals("false", options["onboardAccessibility"])
        assertEquals("true", options["skipMultiplayerWarning"])
        assertEquals("none", options["tutorialStep"])
        assertEquals("0", options["narrator"])
        assertEquals("0.0", options["soundCategory_master"])
        assertEquals("60", options["maxFps"])
        assertTrue(options.keys.none { it.isBlank() })
    }
}
