package dev.blockwright.orchestrator.bench

import java.nio.file.Files
import java.nio.file.Path

/** Launches a JVM main class that speaks the bench line protocol. */
class ProcessBenchTarget(
    override val profile: String,
    private val javaExecutable: Path,
    private val classpath: String,
    private val mainClass: String,
    private val args: List<String> = emptyList(),
) : BenchTarget {
    override fun launch(workDir: Path): BenchClient {
        val clientDir = Files.createTempDirectory(workDir, "client-")
        // The classpath goes through an @argfile: a long classpath exceeds the Windows command line limit.
        val argFile = clientDir.resolve("java.args")
        Files.writeString(argFile, "-cp\n\"${classpath.replace("\\", "\\\\")}\"\n")
        val command = listOf(javaExecutable.toString(), "@$argFile", mainClass) + args
        val process = ProcessBuilder(command)
            .directory(clientDir.toFile())
            .redirectError(clientDir.resolve("stderr.log").toFile())
            .start()
        return ProcessBenchClient(process)
    }
}
