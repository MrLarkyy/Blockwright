package dev.blockwright.launcher.launch

import dev.blockwright.launcher.ResolvedVersion
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One client launch. Typed fields only: no free-form JVM or game arguments reach the command line. The client joins
 * [serverHost]:[serverPort] as the offline player [username]; its console and Minecraft log end up in [artifactDir].
 * [javaExecutable] null runs the launcher's own JVM.
 */
data class ClientLaunchConfig(
    val version: ResolvedVersion,
    val agentJar: Path,
    val profile: LaunchProfile,
    val serverHost: String,
    val serverPort: Int,
    val username: String,
    val artifactDir: Path,
    val javaExecutable: Path? = null,
    val trace: TraceRequest? = null,
    val stopTimeout: Duration = 30.seconds,
    val keepRunDir: Boolean = false,
) {
    init {
        require(serverPort in 1..MAX_PORT) { "serverPort must be 1-$MAX_PORT" }
        require(HOST.matches(serverHost)) { "serverHost must be a host name or IP address" }
        require(stopTimeout.isPositive()) { "stopTimeout must be positive" }
    }

    private companion object {
        const val MAX_PORT = 65535
        val HOST = Regex("[A-Za-z0-9.:\\-\\[\\]]{1,253}")
    }
}
