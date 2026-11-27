package dev.blockwright.launcher.launch

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.launch.fabric.FabricRuntime
import dev.blockwright.launcher.platform.Platform
import dev.blockwright.launcher.platform.RuleEvaluator
import java.nio.file.Files

/**
 * Starts resolved clients with Fabric and the Blockwright agent. Everything that can be refused is checked before
 * a file is written or a process starts: the username, the agent jar's Minecraft version and the JVM version.
 * Each client gets a throwaway run directory with a generated `options.txt` and an empty assets directory.
 */
class ClientLauncher(
    private val cache: CacheLayout,
    private val downloader: Downloader,
    private val starter: ClientProcessStarter = ClientProcessStarter.JVM,
    private val fabric: () -> FabricRuntime = FabricRuntime::pinned,
    platform: Platform = Platform.current(),
) {
    private val arguments = ClientArguments(RuleEvaluator(platform))

    fun launch(config: ClientLaunchConfig): ClientProcess {
        OfflineUuid.validate(config.username)
        AgentJar.read(config.agentJar).requireMinecraft(config.version.id)
        val java = config.javaExecutable?.let(JavaRuntime::of) ?: JavaRuntime.current()
        java.requireAtLeast(config.version.javaMajor)
        // Real paths: Fabric tells its own loader jars from game jars by comparing their locations, so a cache
        // reached through a symbolic link would otherwise load the loader a second time inside the game.
        val classpath = (fabric().ensure(downloader, cache) + config.version.classpath).map { it.toRealPath() }

        val runDir = RunDirectory.create()
        try {
            val gameDir = runDir.resolve(ClientProcess.GAME_DIR)
            val assetsDir = Files.createDirectories(runDir.resolve("assets"))
            GameOptionsFile.write(gameDir)
            val inputs = ClientArguments.Inputs(config, classpath, gameDir, assetsDir, cache.root.resolve("libraries"), ProcessHandle.current().pid())
            val command = LaunchCommand.write(java.executable, runDir.resolve("client.args"), arguments.build(inputs))
            return ClientProcess.start(starter, command, runDir, config.artifactDir, config.stopTimeout, config.keepRunDir)
        } catch (e: Throwable) {
            RunDirectory.delete(runDir)
            throw e
        }
    }
}
