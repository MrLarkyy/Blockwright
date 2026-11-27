package dev.blockwright.orchestrator.bench

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BenchTargetsTest {
    @TempDir lateinit var dir: Path

    private fun properties(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test
    fun logicIsNotRunWithoutTheUsersEulaAcceptance() {
        val agent = Files.writeString(dir.resolve("agent.jar"), "jar")
        assertNull(BenchTargets.forProfile("logic", properties(BenchTargets.AGENT_JAR to agent.toString())))
        assertNull(BenchTargets.forProfile("logic", properties(BenchTargets.ACCEPT_EULA to "false", BenchTargets.AGENT_JAR to agent.toString())))
    }

    @Test
    fun logicIsNotRunWithoutAnAgentJar() {
        assertNull(BenchTargets.forProfile("logic", properties(BenchTargets.ACCEPT_EULA to "true")))
        assertNull(BenchTargets.forProfile("logic", properties(BenchTargets.ACCEPT_EULA to "true", BenchTargets.AGENT_JAR to dir.resolve("missing.jar").toString())))
    }

    @Test
    fun logicWithBothIsARealClientTarget() {
        val agent = Files.writeString(dir.resolve("agent.jar"), "jar")
        val target = BenchTargets.forProfile(
            "logic",
            properties(BenchTargets.ACCEPT_EULA to "true", BenchTargets.AGENT_JAR to agent.toString(), BenchTargets.CACHE_DIR to dir.resolve("cache").toString()),
        )
        target.use {
            assertInstanceOf(ClientBenchTarget::class.java, it)
            assertEquals("logic", it!!.profile)
        }
    }

    @Test
    fun unknownProfilesHaveNoTarget() {
        assertNull(BenchTargets.forProfile("snapshot", properties()))
    }
}
