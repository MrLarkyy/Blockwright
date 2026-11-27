package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AgentJarTest {
    @TempDir lateinit var dir: Path

    @Test
    fun readsTheMinecraftVersionTheAgentWasBuiltFor() {
        val agent = AgentJar.read(LaunchFixtures.agentJar(dir, minecraft = "26.3"))
        assertEquals("26.3", agent.minecraftVersion)
        assertEquals("0.1.0", agent.version)
        agent.requireMinecraft("26.3")
    }

    @Test
    fun refusesAnotherMinecraftVersion() {
        val error = assertThrows<LaunchException> { AgentJar.read(LaunchFixtures.agentJar(dir, minecraft = "26.2")).requireMinecraft("26.3") }
        assertTrue("built for Minecraft 26.2, not 26.3" in error.message!!, error.message)
    }

    @Test
    fun refusesMissingOversizedOrForeignManifests() {
        assertThrows<LaunchException> { AgentJar.read(dir.resolve("missing.jar")) }
        assertThrows<LaunchException> { AgentJar.read(LaunchFixtures.agentJar(dir, id = "other-mod")) }
        assertThrows<LaunchException> { AgentJar.read(LaunchFixtures.agentJar(dir, manifest = """{"id": "blockwright-agent"}""")) }
        assertThrows<LaunchException> { AgentJar.read(LaunchFixtures.agentJar(dir, manifest = "not json")) }
        val huge = LaunchFixtures.agentJar(dir, manifest = """{"id": "blockwright-agent", "pad": "${"x".repeat(AgentJar.MAX_MANIFEST_BYTES)}"}""")
        assertTrue("exceeds" in assertThrows<LaunchException> { AgentJar.read(huge) }.message!!)

        val empty = dir.resolve("empty.jar")
        ZipOutputStream(Files.newOutputStream(empty)).use { it.putNextEntry(ZipEntry("a.txt")); it.closeEntry() }
        assertTrue("no fabric.mod.json" in assertThrows<LaunchException> { AgentJar.read(empty) }.message!!)

        val notAJar = Files.writeString(dir.resolve("plain.jar"), "plain")
        assertThrows<LaunchException> { AgentJar.read(notAJar) }
    }
}
