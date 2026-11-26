package dev.blockwright.serverrunner

import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/** Launch helpers for [FakePaperServer]: its classpath is the test and main classes plus the Kotlin stdlib. */
object FakeServers {
    private val classpath: String = listOf(FakePaperServer::class.java, JavaRequirement::class.java, KotlinVersion::class.java)
        .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
        .distinct()
        .joinToString(File.pathSeparator)

    fun command(mode: String): List<String> =
        listOf(JavaRequirement.currentJava().toString(), "-Xmx64m", "-cp", classpath, FakePaperServer::class.java.name, mode)

    /** A launcher that records the real command and starts the fake server in [mode] instead. */
    class Launcher(private val mode: String) : ProcessLauncher {
        val commands = CopyOnWriteArrayList<List<String>>()

        override fun launch(command: List<String>, workDir: Path): Process {
            commands += command
            return JvmProcessLauncher().launch(FakeServers.command(mode), workDir)
        }
    }

    fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}
