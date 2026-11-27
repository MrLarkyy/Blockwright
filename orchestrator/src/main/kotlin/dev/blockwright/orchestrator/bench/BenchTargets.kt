package dev.blockwright.orchestrator.bench

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.launch.LaunchProfile
import java.nio.file.Files
import java.nio.file.Path

/** Profile name to bench target. A real client profile is one more entry here plus a budgets entry. */
object BenchTargets {
    const val STUB = "stub"
    const val LOGIC = "logic"
    const val ACCEPT_EULA = "blockwright.acceptEula"
    const val AGENT_JAR = "blockwright.agentJar"
    const val CACHE_DIR = "blockwright.it.cacheDir"

    fun forProfile(profile: String, properties: (String) -> String? = System::getProperty): BenchTarget? = when (profile) {
        STUB -> stub()
        LOGIC -> realClient(LOGIC, LaunchProfile.LOGIC, properties)
        else -> null
    }

    /**
     * A real client needs a Paper server, which runs only after the user accepted the Minecraft EULA for this run
     * (`-Pblockwright.acceptEula=true`), and the agent jar the build passes in. Without either the profile is not run.
     */
    private fun realClient(name: String, profile: LaunchProfile, properties: (String) -> String?): BenchTarget? {
        if (properties(ACCEPT_EULA) != "true") return null
        val agent = properties(AGENT_JAR)?.takeIf { it.isNotBlank() }?.let(Path::of)?.takeIf(Files::isRegularFile) ?: return null
        val cache = properties(CACHE_DIR)?.takeIf { it.isNotBlank() }?.let(Path::of) ?: CacheLayout.defaultRoot()
        return ClientBenchTarget(name, profile, agent, cache)
    }

    private fun stub(): BenchTarget {
        val java = Path.of(System.getProperty("java.home"), "bin", if (isWindows()) "java.exe" else "java")
        return ProcessBenchTarget(
            STUB,
            java,
            System.getProperty("java.class.path"),
            "dev.blockwright.orchestrator.bench.StubClient",
            listOf("--alloc-mib", "32"),
        )
    }

    private fun isWindows() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}
