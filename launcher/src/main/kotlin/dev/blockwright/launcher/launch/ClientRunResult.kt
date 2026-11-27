package dev.blockwright.launcher.launch

import java.nio.file.Path

/**
 * How a client run ended. [exitCode] is null when the process could not be reaped; [forcedKill] is true when it
 * did not stop within the stop timeout; [latestLog] is Minecraft's own log copied into the artefact directory;
 * [runDirKept] is true when the run directory was kept on request or could not be removed (Windows file locks).
 */
data class ClientRunResult(
    val exitCode: Int?,
    val forcedKill: Boolean,
    val consoleLog: Path,
    val latestLog: Path?,
    val runDir: Path,
    val runDirKept: Boolean,
)
