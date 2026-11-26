package dev.blockwright.serverrunner

import dev.blockwright.serverrunner.fill.FillApiClient
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path

/**
 * Starts throwaway Paper test servers: EULA gate, build resolution, verified jar cache, Paperclip preparation, an
 * offline localhost run directory with the plugins under test, launch and readiness. The EULA gate comes first, so
 * without the user's explicit acceptance nothing is requested, written or started.
 */
class PaperServerRunner(
    private val http: BoundedHttpClient = BoundedHttpClient(UrlPolicy.FILL),
    private val source: PaperBuildSource = FillApiClient(http),
    private val preparer: BundlerPreparer = BundlerPreparer(),
    private val launcher: ProcessLauncher = JvmProcessLauncher(),
    private val eulaWriter: (Path) -> Unit = EulaFile::write,
    private val runDirs: () -> Path = RunDirectory::create,
) {
    fun start(config: ServerRunConfig): ServerProcess {
        if (!config.acceptEula) throw EulaNotAcceptedException()
        PluginInstaller.check(config.plugins)
        val cached = PaperJarCache(config.cacheRoot, http).obtain(source, config.version, config.build)
        JavaRequirement.check(cached.build, config.javaExecutable)
        val java = config.javaExecutable ?: JavaRequirement.currentJava()
        val bundlerDir = preparer.prepare(java, cached)
        val port = config.port ?: freeLoopbackPort()
        val runDir = runDirs()
        try {
            ServerProperties.write(runDir, port, config.propertyOverrides)
            PluginInstaller.install(config.plugins, runDir)
            eulaWriter(runDir)
            return ServerProcess.start(
                launcher, command(java, config, bundlerDir, cached), runDir, config.artifactDir, port,
                config.readyTimeout, config.stopTimeout, config.keepRunDir,
            )
        } catch (e: Throwable) {
            if (!config.keepRunDir) RunDirectory.delete(runDir)
            throw e
        }
    }

    private fun command(java: Path, config: ServerRunConfig, bundlerDir: Path, cached: CachedPaperJar): List<String> = listOf(
        java.toString(),
        "-Xmx${config.maxHeap}",
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-DbundlerRepoDir=$bundlerDir",
        "-jar",
        cached.jar.toString(),
        "--nogui",
    )

    // The port is free when picked and could be taken before Paper binds it; Paper then exits early and start
    // reports that with the log tail. Callers that need certainty pin ServerRunConfig.port.
    private fun freeLoopbackPort(): Int = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
}
