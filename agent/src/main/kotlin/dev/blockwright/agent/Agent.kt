package dev.blockwright.agent

import dev.blockwright.agent.lifecycle.ParentWatchdog
import dev.blockwright.agent.lifecycle.ShutdownCoordinator
import dev.blockwright.agent.lifecycle.StartupFailures
import dev.blockwright.agent.lifecycle.StdinEofWatcher
import dev.blockwright.agent.trace.InputScript
import dev.blockwright.agent.trace.MovementTracer
import net.minecraft.client.Minecraft
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path

/** The agent's process-wide state; the mixins reach it through the Kotlin hooks that delegate here. */
object Agent {
    @Volatile var profile: AgentProfile = AgentProfile.NONE
        private set

    @Volatile private var statusWriter: AgentStatus? = null

    val status: AgentStatus get() = statusWriter ?: AgentStatus(System.out).also { statusWriter = it }

    val shutdown = ShutdownCoordinator(
        stopClient = ::stopClient,
        onRequest = { reason -> status.emit("stopping", mapOf("reason" to reason)) },
    )

    val startupFailures = StartupFailures { status.takeIf { profile != AgentProfile.NONE } }

    /** Set when the launcher asked for a movement trace (`blockwright.trace.out`). */
    @Volatile var tracer: MovementTracer? = null
        private set

    /** Called once from the pre-launch entrypoint, before Minecraft's main method runs. */
    fun start(
        profile: AgentProfile,
        out: PrintStream,
        stdin: InputStream,
        parentPid: Long?,
        statusToken: String?,
        trace: Path? = null,
        script: InputScript? = null,
    ) {
        this.profile = profile
        statusWriter = AgentStatus(out, statusToken)
        if (profile == AgentProfile.NONE) return
        tracer = trace?.let { MovementTracer(it, script ?: InputScript.WALK_JUMP_STRAFE, status) }
        status.emit("starting", mapOf("profile" to profile.property))
        StdinEofWatcher(stdin) { shutdown.request("stdin-closed") }.start()
        parentPid?.let { ParentWatchdog(it) { shutdown.request("parent-exited") }.start() }
    }

    /** The client once its first tick ran; a stop requested before that halts the still-starting JVM. */
    @Volatile var client: Minecraft? = null
        internal set

    private fun stopClient(): Boolean {
        val minecraft = client ?: return false
        minecraft.execute { minecraft.stop() }
        return true
    }
}
