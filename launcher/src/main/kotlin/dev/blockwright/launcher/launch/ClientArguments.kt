package dev.blockwright.launcher.launch

import dev.blockwright.launcher.launch.fabric.FabricRuntime
import dev.blockwright.launcher.platform.RuleEvaluator
import java.io.File
import java.nio.file.Path

/**
 * The full argument list after `java`: profile flags and Blockwright properties, the version's JVM arguments (with
 * the classpath), Fabric's Knot main class, then the version's game arguments with quick play to the server.
 * Mojang services point at an unreachable loopback URL and the access token is the non-secret `0`, so an offline
 * client never talks to Mojang with a fake identity.
 */
class ClientArguments(private val rules: RuleEvaluator) {
    /**
     * [classpath] is the Fabric runtime followed by the version's classpath (client jar last); [statusToken] is the
     * launch's secret the agent writes into each status line.
     */
    data class Inputs(
        val config: ClientLaunchConfig,
        val classpath: List<Path>,
        val gameDir: Path,
        val assetsDir: Path,
        val librariesDir: Path,
        val parentPid: Long,
        val statusToken: String,
    )

    fun build(inputs: Inputs): List<String> {
        val config = inputs.config
        val version = config.version
        val uuid = OfflineUuid.of(config.username)
        val classpath = inputs.classpath.joinToString(File.pathSeparator) { it.toString() }
        val values = mapOf(
            "natives_directory" to version.nativesDir.toString(),
            "launcher_name" to LAUNCHER_NAME,
            "launcher_version" to LAUNCHER_VERSION,
            "classpath" to classpath,
            "classpath_separator" to File.pathSeparator,
            "library_directory" to inputs.librariesDir.toString(),
            "auth_player_name" to config.username,
            "version_name" to version.id,
            "game_directory" to inputs.gameDir.toString(),
            "assets_root" to inputs.assetsDir.toString(),
            "assets_index_name" to (version.assetIndexId ?: version.details.assetIndex?.id ?: version.id),
            "auth_uuid" to OfflineUuid.undashed(uuid),
            "auth_access_token" to OFFLINE_TOKEN,
            "clientid" to OFFLINE_TOKEN,
            "auth_xuid" to OFFLINE_TOKEN,
            "user_type" to "legacy",
            "version_type" to LAUNCHER_NAME,
            "quickPlayMultiplayer" to "${config.serverHost}:${config.serverPort}",
        )
        val template = ArgumentTemplate(rules)
        return buildList {
            addAll(config.profile.jvmFlags)
            add("-D$PROFILE_PROPERTY=${config.profile.agentProfile}")
            add("-D$PARENT_PID_PROPERTY=${inputs.parentPid}")
            add("-D${AgentStatusLine.TOKEN_PROPERTY}=${inputs.statusToken}")
            add("-D$FABRIC_ADD_MODS=${config.agentJar.toAbsolutePath()}")
            add("-D$DISCOVERY_PROPERTY=$UNREACHABLE_SERVICES")
            config.trace?.let {
                add("-D$TRACE_PROPERTY=${it.output.toAbsolutePath()}")
                add("-D$TRACE_SCRIPT_PROPERTY=${it.script}")
            }
            addAll(template.expand(version.details, ArgumentTemplate.Section.JVM, values, FEATURES))
            add(FabricRuntime.MAIN_CLASS)
            addAll(template.expand(version.details, ArgumentTemplate.Section.GAME, values, FEATURES))
            addAll(listOf("--graphicsBackend", "opengl"))
        }
    }

    companion object {
        const val LAUNCHER_NAME = "blockwright"
        const val LAUNCHER_VERSION = "0.1.0"
        const val OFFLINE_TOKEN = "0"
        const val PROFILE_PROPERTY = "blockwright.profile"
        const val PARENT_PID_PROPERTY = "blockwright.parentPid"
        const val TRACE_PROPERTY = "blockwright.trace.out"
        const val TRACE_SCRIPT_PROPERTY = "blockwright.trace.script"
        const val FABRIC_ADD_MODS = "fabric.addMods"
        const val DISCOVERY_PROPERTY = "minecraft.api.discovery.host"

        /** Port 9 (discard) on loopback: connections are refused at once, nothing leaves the machine. */
        const val UNREACHABLE_SERVICES = "http://127.0.0.1:9/minecraft/client"

        /** Only quick play to a server; never demo mode, custom resolution or a quick-play log file. */
        val FEATURES = mapOf("is_quick_play_multiplayer" to true)
    }
}
