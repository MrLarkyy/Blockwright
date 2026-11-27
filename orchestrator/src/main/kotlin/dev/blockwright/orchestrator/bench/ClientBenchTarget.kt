package dev.blockwright.orchestrator.bench

import dev.blockwright.launcher.ResolveRequest
import dev.blockwright.launcher.ResolvedVersion
import dev.blockwright.launcher.VersionResolver
import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.download.JdkHttpTransport
import dev.blockwright.launcher.launch.ClientLaunchConfig
import dev.blockwright.launcher.launch.ClientLauncher
import dev.blockwright.launcher.launch.LaunchProfile
import dev.blockwright.serverrunner.PaperServerRunner
import dev.blockwright.serverrunner.ServerProcess
import dev.blockwright.serverrunner.ServerRunConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Real clients of one launch profile against one local Paper server. [prepare] resolves the client, downloads the
 * Fabric runtime and starts the server before anything is timed; [close] stops the server. Only built with the user's explicit EULA acceptance.
 * Each client (`bench1`, `bench2`, ...) is tracked by the reaper and also stops itself if this JVM dies.
 */
class ClientBenchTarget(
    override val profile: String,
    private val launchProfile: LaunchProfile,
    private val agentJar: Path,
    private val cacheRoot: Path,
) : BenchTarget {
    private val policy = DownloadPolicy()
    private val transport = JdkHttpTransport(policy)
    private val cache = CacheLayout(cacheRoot)
    private val launcher = ClientLauncher(cache, Downloader(transport, policy))
    private var serverDir: Path? = null
    private var server: ServerProcess? = null
    private var version: ResolvedVersion? = null
    private var fabricReady = false
    private var launched = 0

    override fun prepare() {
        if (version == null) version = VersionResolver(cache, transport, policy).resolve(ResolveRequest(VERSION))
        if (!fabricReady) {
            launcher.prepare()
            fabricReady = true
        }
        if (server == null) {
            val dir = Files.createTempDirectory("bw-bench-server-").also { serverDir = it }
            server = PaperServerRunner().start(
                ServerRunConfig(
                    version = VERSION,
                    build = PAPER_BUILD,
                    cacheRoot = cacheRoot,
                    artifactDir = dir,
                    acceptEula = true,
                    propertyOverrides = mapOf("white-list" to "false", "level-type" to "minecraft:flat", "difficulty" to "peaceful"),
                ),
            )
        }
    }

    override fun launch(workDir: Path): BenchClient {
        prepare()
        val name = "bench${++launched}"
        val config = ClientLaunchConfig(
            version = version!!,
            agentJar = agentJar,
            profile = launchProfile,
            serverHost = "127.0.0.1",
            serverPort = server!!.port,
            username = name,
            artifactDir = workDir.resolve(name),
        )
        return ClientBenchClient.of(launcher.launch(config))
    }

    @OptIn(ExperimentalPathApi::class)
    override fun close() {
        try {
            server?.stop()
        } finally {
            server = null
            transport.close()
            serverDir?.let { runCatching { it.deleteRecursively() } }
        }
    }

    companion object {
        const val VERSION = "26.3"
        const val PAPER_BUILD = 262
    }
}
