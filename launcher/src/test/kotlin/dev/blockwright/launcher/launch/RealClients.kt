package dev.blockwright.launcher.launch

import dev.blockwright.launcher.ResolveRequest
import dev.blockwright.launcher.ResolvedVersion
import dev.blockwright.launcher.VersionResolver
import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.download.JdkHttpTransport
import dev.blockwright.serverrunner.PaperServerRunner
import dev.blockwright.serverrunner.ServerProcess
import dev.blockwright.serverrunner.ServerRunConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Shared setup of the real-client integration tests: the user's explicit EULA flag, the agent jar the build passes
 * in, the cached client (downloaded into the cache outside the repository on first use) and a flat, peaceful Paper
 * server on loopback where every player spawns on the same block.
 */
object RealClients {
    const val VERSION = "26.3"
    const val PAPER_BUILD = 262
    val JOIN_TIMEOUT = 180.seconds

    val cacheRoot: Path = System.getProperty("blockwright.it.cacheDir")?.let(Path::of)
        ?: CacheLayout.defaultRoot().resolveSibling("blockwright-it")
    val artefacts: Path = Path.of("build", "blockwright-artifacts")

    private val policy = DownloadPolicy()

    fun requireEulaAndAgent(): Path {
        assumeTrue(System.getProperty("blockwright.acceptEula") == "true", "the Minecraft EULA was not accepted with -Pblockwright.acceptEula=true")
        val agent = System.getProperty("blockwright.agentJar")
        assumeTrue(!agent.isNullOrBlank(), "no agent jar (run through :launcher:integrationTest)")
        return Path.of(agent!!)
    }

    fun resolve(): ResolvedVersion = JdkHttpTransport(policy).use { transport ->
        VersionResolver(CacheLayout(cacheRoot), transport, policy).resolve(ResolveRequest(VERSION))
    }

    fun launcher(transport: JdkHttpTransport): ClientLauncher =
        ClientLauncher(CacheLayout(cacheRoot), Downloader(transport, policy))

    fun startServer(name: String): ServerProcess {
        val server = PaperServerRunner().start(
            ServerRunConfig(
                version = VERSION,
                build = PAPER_BUILD,
                cacheRoot = cacheRoot,
                artifactDir = artefacts.resolve(name).resolve("server"),
                acceptEula = true,
                propertyOverrides = mapOf(
                    "white-list" to "false",
                    "level-type" to "minecraft:flat",
                    "generate-structures" to "false",
                    "difficulty" to "peaceful",
                    "spawn-protection" to "0",
                    "view-distance" to "4",
                    "simulation-distance" to "4",
                ),
            ),
        )
        server.console("gamerule respawn_radius 0")
        return server
    }

    fun config(
        version: ResolvedVersion,
        agent: Path,
        server: ServerProcess,
        profile: LaunchProfile,
        username: String,
        artifactDir: Path,
        trace: TraceRequest? = null,
    ) = ClientLaunchConfig(
        version = version,
        agentJar = agent,
        profile = profile,
        serverHost = "127.0.0.1",
        serverPort = server.port,
        username = username,
        artifactDir = artifactDir,
        trace = trace,
        keepRunDir = System.getProperty("blockwright.it.keepRunDir") == "true",
    )

    fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}
