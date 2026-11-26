package dev.blockwright.serverrunner

import java.nio.file.Path

/** Starts the server JVM; stdout and stderr must arrive merged on [Process.getInputStream]. */
fun interface ProcessLauncher {
    fun launch(command: List<String>, workDir: Path): Process
}
