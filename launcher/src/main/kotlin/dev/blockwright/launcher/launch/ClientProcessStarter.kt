package dev.blockwright.launcher.launch

import java.nio.file.Path

/**
 * Starts the client JVM. stdout and stderr must arrive merged on [Process.getInputStream] and stdin must stay a
 * pipe: closing it is the client's stop signal.
 */
fun interface ClientProcessStarter {
    fun start(command: List<String>, workDir: Path): Process

    companion object {
        val JVM = ClientProcessStarter { command, workDir ->
            ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true).start()
        }
    }
}
