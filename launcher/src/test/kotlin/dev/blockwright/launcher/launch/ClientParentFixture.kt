package dev.blockwright.launcher.launch

import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.JdkHttpTransport
import java.nio.file.Path

/**
 * A stand-in for a bench or orchestrator JVM that owns a client: launches a logic client, reports its pid once it
 * joined, then idles until it is killed. Args: agentJar serverPort username artifactDir.
 */
object ClientParentFixture {
    const val CLIENT_PID = "CLIENT_PID "
    const val RUN_DIR = "RUN_DIR "

    @JvmStatic
    fun main(args: Array<String>) {
        val (agent, port, username, artifactDir) = args
        val version = RealClients.resolve()
        val transport = JdkHttpTransport(DownloadPolicy())
        val config = ClientLaunchConfig(
            version = version,
            agentJar = Path.of(agent),
            profile = LaunchProfile.LOGIC,
            serverHost = "127.0.0.1",
            serverPort = port.toInt(),
            username = username,
            artifactDir = Path.of(artifactDir),
        )
        val client = RealClients.launcher(transport).launch(config)
        client.awaitJoined(RealClients.JOIN_TIMEOUT)
        println(CLIENT_PID + client.pid)
        println(RUN_DIR + client.runDir)
        System.out.flush()
        Thread.sleep(Long.MAX_VALUE)
    }
}
