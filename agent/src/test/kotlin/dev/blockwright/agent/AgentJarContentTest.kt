package dev.blockwright.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.zip.ZipFile

/** The built agent jar (passed in by the build) ships nothing from Mojang and only thin mixins. */
class AgentJarContentTest {
    private val jar: Path = Path.of(System.getProperty("blockwright.agentJar") ?: error("run through Gradle: blockwright.agentJar is not set"))

    @Test
    fun containsNoMinecraftOrMojangClasses() {
        ZipFile(jar.toFile()).use { zip ->
            val forbidden = zip.entries().asSequence().map { it.name }.filter { it.startsWith("net/minecraft/") || it.startsWith("com/mojang/") }.toList()
            assertEquals(emptyList<String>(), forbidden)
        }
    }

    @Test
    fun dependsOnExactlyTheBuildsMinecraftVersion() {
        val manifest = ZipFile(jar.toFile()).use { zip -> zip.getInputStream(zip.getEntry("fabric.mod.json")).readBytes().toString(Charsets.UTF_8) }
        val version = System.getProperty("blockwright.minecraftVersion")
        assertTrue(Regex("\"minecraft\"\\s*:\\s*\"${Regex.escape(version)}\"").containsMatchIn(manifest), manifest)
        assertTrue(Regex("\"id\"\\s*:\\s*\"blockwright-agent\"").containsMatchIn(manifest), manifest)
    }

    @Test
    fun everyMixinOnlyHooksAndDelegatesToKotlin() {
        ZipFile(jar.toFile()).use { zip ->
            val mixins = zip.entries().asSequence().filter { it.name.startsWith("dev/blockwright/agent/mixin/") && it.name.endsWith(".class") }.toList()
            assertTrue(mixins.isNotEmpty())
            mixins.forEach { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                val text = String(bytes, Charsets.ISO_8859_1)
                val accessor = entry.name.endsWith("Accessor.class")
                assertTrue(accessor || "dev/blockwright/agent/AgentHooks" in text, "${entry.name} does not delegate to AgentHooks")
                assertTrue(bytes.size < MAX_MIXIN_BYTES, "${entry.name} is ${bytes.size} bytes: logic belongs in Kotlin")
            }
        }
    }

    private companion object {
        const val MAX_MIXIN_BYTES = 4096
    }
}
