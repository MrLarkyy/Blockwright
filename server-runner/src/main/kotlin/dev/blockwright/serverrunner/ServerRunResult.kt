package dev.blockwright.serverrunner

import java.nio.file.Path

/**
 * How a server run ended. [exitCode] is null when the process could not be reaped; [runDirKept] is true when the
 * run directory was kept on request or could not be removed (Windows file locks).
 */
data class ServerRunResult(
    val exitCode: Int?,
    val forcedKill: Boolean,
    val consoleLog: Path,
    val latestLog: Path?,
    val runDir: Path,
    val runDirKept: Boolean,
)
