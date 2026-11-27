package dev.blockwright.launcher.launch

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.launch.fabric.FabricRuntime
import dev.blockwright.launcher.platform.Platform
import dev.blockwright.launcher.platform.RuleEvaluator
import java.nio.file.Files
import java.nio.file.Path

/**
 * Starts resolved clients with Fabric and the Blockwright agent. Everything that can be refused is checked before
 * a file is written or a process starts: the username, the agent jar's Minecraft version and the JVM version.
 * Each client gets a throwaway run directory with a generated `options.txt` and an empty assets directory; run
 * directories left by an owner killed without hooks are removed first.
 */
class ClientLauncher(
    private val cache: CacheLayout,
    private val downloader: Downloader,
    private val starter: ClientProcessStarter = ClientProcessStarter.JVM,
    private val fabric: () -> FabricRuntime = FabricRuntime::pinned,
    platform: Platform = Platform.current(),
) {
    private val arguments = ClientArguments(RuleEvaluator(platform))

    /**
     * Downloads the pinned Fabric runtime into the cache now, so a timed [launch] only verifies it; returns its
     * classpath entries. [launch] does the same on its own when this was never called.
     */
    fun prepare(): List<Path> = fabric().ensure(downloader, cache)

    fun launch(config: ClientLaunchConfig): ClientProcess {
        OfflineUuid.validate(config.username)
        AgentJar.read(config.agentJar).requireMinecraft(config.version.id)
        val java = config.javaExecutable?.let(JavaRuntime::of) ?: JavaRuntime.current()
        java.requireAtLeast(config.version.javaMajor)
        // Real paths: Fabric tells its own loader jars from game jars by comparing their locations, so a cache
        // reached through a symbolic link would otherwise load the loader a second time inside the game.
        val classpath = (prepare() + config.version.classpath).map { it.toRealPath() }

        RunDirectory.sweep()
        val runDir = RunDirectory.create()
        try {
            val gameDir = runDir.resolve(ClientProcess.GAME_DIR)
            val assetsDir = Files.createDirectories(runDir.resolve("assets"))
            GameOptionsFile.write(gameDir)
            val token = AgentStatusLine.newToken()
            val inputs = ClientArguments.Inputs(config, classpath, gameDir, assetsDir, cache.root.resolve("libraries"), ProcessHandle.current().pid(), token)
            val command = LaunchCommand.write(java.executable, runDir.resolve("client.args"), arguments.build(inputs))
            return ClientProcess.start(starter, command, runDir, config.artifactDir, config.stopTimeout, config.keepRunDir, token)
        } catch (e: Throwable) {
            RunDirectory.delete(runDir)
            throw e
        }
    }
}
