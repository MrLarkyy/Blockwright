package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * The owner of a client dies without running any hook (as a cancelled Gradle bench on Windows does): the client
 * must notice and exit by itself within 20 s. Runs only with `-Pblockwright.acceptEula=true`.
 */
@Tag("integration")
class ClientParentDeathIntegrationTest {
    @OptIn(ExperimentalPathApi::class)
    @Test
    fun logicClientExitsWhenItsParentIsKilledForcibly() {
        val agent = RealClients.requireEulaAndAgent()
        RealClients.resolve()
        val dir = RealClients.artefacts.resolve("parent-death").toAbsolutePath()
        RealClients.startServer("parent-death").use { server ->
            val parent = startParent(dir, agent, server.port)
            var clientPid: Long? = null
            var runDir: Path? = null
            try {
                parent.inputReader().useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith(ClientParentFixture.CLIENT_PID)) clientPid = line.removePrefix(ClientParentFixture.CLIENT_PID).trim().toLong()
                        if (line.startsWith(ClientParentFixture.RUN_DIR)) {
                            runDir = Path.of(line.removePrefix(ClientParentFixture.RUN_DIR).trim())
                            break
                        }
                    }
                }
                assertNotNull(clientPid, "the parent never reported a joined client")
                val pid = clientPid!!
                assertTrue(RealClients.isAlive(pid), "the client was not running")

                // Only the parent dies, the way Task Manager or a cancelled Gradle run kills it: no hook runs.
                parent.destroyForcibly()
                parent.waitFor(10, TimeUnit.SECONDS)

                val exited = ProcessHandle.of(pid).map { runCatching { it.onExit().get(20, TimeUnit.SECONDS) }.isSuccess }.orElse(true)
                assertTrue(exited && !RealClients.isAlive(pid), "the client outlived its parent by more than 20 s")
            } finally {
                parent.destroyForcibly()
                clientPid?.let { pid -> ProcessHandle.of(pid).ifPresent { it.destroyForcibly() } }
                // The killed parent ran no hook: its run directory is this test's to remove.
                runDir?.let { runCatching { it.deleteRecursively() } }
            }
        }
    }

    private fun startParent(dir: Path, agent: Path, port: Int): Process {
        Files.createDirectories(dir)
        val argFile = dir.resolve("parent.args")
        Files.writeString(argFile, listOf("-cp", System.getProperty("java.class.path")).joinToString("\n") { LaunchCommand.quote(it) })
        val command = buildList {
            add(JavaRuntime.current().executable.toString())
            add("@$argFile")
            System.getProperty("blockwright.it.cacheDir")?.let { add("-Dblockwright.it.cacheDir=$it") }
            add(ClientParentFixture::class.java.name)
            addAll(listOf(agent.toAbsolutePath().toString(), port.toString(), "bworphan", dir.resolve("client").toString()))
        }
        return ProcessBuilder(command).redirectErrorStream(true).start()
    }
}
