package dev.blockwright.agent

import dev.blockwright.agent.lifecycle.ParentWatchdog
import dev.blockwright.agent.trace.InputScript
import dev.blockwright.agent.trace.MovementTracer
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import java.nio.file.Path

/** Fabric pre-launch entrypoint: runs before Minecraft's main method, while System.out is still the raw stdout. */
class BlockwrightAgent : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        Agent.start(
            profile = AgentProfile.current(),
            out = System.out,
            stdin = System.`in`,
            parentPid = ParentWatchdog.parsePid(System.getProperty(ParentWatchdog.PROPERTY)),
            statusToken = System.getProperty(AgentStatus.TOKEN_PROPERTY),
            trace = System.getProperty(MovementTracer.PROPERTY)?.takeIf { it.isNotBlank() }?.let(Path::of),
            script = InputScript.named(System.getProperty(InputScript.PROPERTY)),
        )
    }
}
