package dev.blockwright.orchestrator.bench

import java.nio.file.Path

/** Profile name to bench target. A real client profile is one more entry here plus a budgets entry. */
object BenchTargets {
    const val STUB = "stub"

    fun forProfile(profile: String): BenchTarget? = when (profile) {
        STUB -> stub()
        else -> null
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
