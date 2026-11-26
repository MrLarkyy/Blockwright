package dev.blockwright.serverrunner

import java.nio.file.Path

class JvmProcessLauncher : ProcessLauncher {
    override fun launch(command: List<String>, workDir: Path): Process =
        ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true).start()
}
