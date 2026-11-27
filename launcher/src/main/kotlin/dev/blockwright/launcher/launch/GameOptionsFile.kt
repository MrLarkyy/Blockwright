package dev.blockwright.launcher.launch

import java.nio.file.Files
import java.nio.file.Path

/**
 * The `options.txt` every Blockwright client starts with, for both profiles: a small view, no first-run screens, no
 * sound volume, no narrator, 60 frames per second without vsync (ticks follow elapsed time, not frames), no
 * pause when the hidden window has no focus, and the forced OpenGL backend so Vulkan is never probed.
 */
object GameOptionsFile {
    val OPTIONS: Map<String, String> = linkedMapOf(
        "renderDistance" to "2",
        "simulationDistance" to "5",
        "maxFps" to "60",
        "enableVsync" to "false",
        "inactivityFpsLimit" to "minimized",
        "pauseOnLostFocus" to "false",
        "onboardAccessibility" to "false",
        "skipMultiplayerWarning" to "true",
        "joinedFirstServer" to "true",
        "tutorialStep" to "none",
        "narrator" to "0",
        "autoJump" to "false",
        "realmsNotifications" to "false",
        "telemetryOptInExtra" to "false",
        "soundCategory_master" to "0.0",
    )

    fun write(gameDir: Path): Path {
        Files.createDirectories(gameDir)
        val file = gameDir.resolve("options.txt")
        Files.writeString(file, OPTIONS.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key:$value" })
        return file
    }
}
