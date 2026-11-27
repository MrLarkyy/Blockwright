package dev.blockwright.launcher.launch

import dev.blockwright.launcher.launch.fabric.FabricRuntime
import dev.blockwright.launcher.platform.Platform
import dev.blockwright.launcher.platform.RuleEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ClientArgumentsTest {
    @TempDir lateinit var root: Path

    private fun build(profile: LaunchProfile = LaunchProfile.LOGIC, username: String = "bwlogic", trace: TraceRequest? = null): List<String> {
        val version = LaunchFixtures.version(root)
        val config = ClientLaunchConfig(version, root.resolve("agent.jar"), profile, "127.0.0.1", 25565, username, root.resolve("out"), trace = trace)
        val inputs = ClientArguments.Inputs(config, listOf(root.resolve("fabric.jar")) + version.classpath, root.resolve("game"), root.resolve("assets"), root.resolve("libraries"), 4242, TOKEN)
        return ClientArguments(RuleEvaluator(Platform("linux", "x86_64", "6.8"))).build(inputs)
    }

    @Test
    fun profileFlagsAndAgentPropertiesComeBeforeTheKnotMainClass() {
        val args = build()
        val main = args.indexOf(FabricRuntime.MAIN_CLASS)
        assertTrue(main > 0)
        val jvm = args.subList(0, main)
        assertTrue(jvm.containsAll(LaunchProfile.LOGIC.jvmFlags))
        assertTrue("-Dblockwright.profile=logic" in jvm)
        assertTrue("-Dblockwright.parentPid=4242" in jvm)
        assertTrue("-Dblockwright.statusToken=$TOKEN" in jvm)
        assertTrue("-Dfabric.addMods=${root.resolve("agent.jar").toAbsolutePath()}" in jvm)
        assertEquals(listOf(root.resolve("fabric.jar"), root.resolve("libraries/a.jar"), root.resolve("versions/client.jar")).joinToString(File.pathSeparator), jvm[jvm.indexOf("-cp") + 1])
    }

    @Test
    fun mojangServicesPointAtLoopbackAndTheTokenIsNotASecret() {
        val args = build()
        assertTrue("-Dminecraft.api.discovery.host=http://127.0.0.1:9/minecraft/client" in args)
        assertEquals("0", args[args.indexOf("--accessToken") + 1])
        assertEquals("1d98c4a4bc0a3c13b4f38e5a7a6ca9dc".length, args[args.indexOf("--uuid") + 1].length)
    }

    @Test
    fun joinsTheServerByQuickPlayWithTheForcedBackend() {
        val args = build()
        assertEquals("127.0.0.1:25565", args[args.indexOf("--quickPlayMultiplayer") + 1])
        assertEquals(listOf("--graphicsBackend", "opengl"), args.takeLast(2))
        assertFalse("--demo" in args)
    }

    @Test
    fun referenceRunsTheAgentWithCutsOff() {
        val args = build(LaunchProfile.REFERENCE)
        assertTrue("-Dblockwright.profile=reference" in args)
        assertFalse("-Djava.awt.headless=true" in args)
    }

    @Test
    fun aTraceRequestReachesTheAgent() {
        val args = build(trace = TraceRequest(root.resolve("t.txt")))
        assertTrue("-Dblockwright.trace.out=${root.resolve("t.txt").toAbsolutePath()}" in args)
        assertTrue("-Dblockwright.trace.script=walk-jump-strafe" in args)
        assertFalse(build().any { it.startsWith("-Dblockwright.trace") })
    }

    @Test
    fun anInvalidUsernameNeverReachesTheCommandLine() {
        assertThrows<LaunchException> { build(username = "bad name\n-Dx=y") }
    }

    private companion object {
        const val TOKEN = "0123456789abcdef0123456789abcdef"
    }
}
