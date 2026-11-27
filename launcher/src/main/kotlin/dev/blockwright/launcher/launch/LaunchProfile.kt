package dev.blockwright.launcher.launch

/**
 * How the client JVM runs. [LOGIC] is the cheapest correctness profile: the agent cuts window, GPU, audio and render
 * assets, so it needs no display; [REFERENCE] runs the same agent, options and input with every cut off and a real
 * window, as the parity baseline. Both use the same heap so only the cuts differ.
 */
enum class LaunchProfile(val agentProfile: String, val jvmFlags: List<String>, val needsDisplay: Boolean) {
    LOGIC("logic", SHARED_FLAGS + "-Djava.awt.headless=true", needsDisplay = false),
    REFERENCE("reference", SHARED_FLAGS, needsDisplay = true),
}

private val SHARED_FLAGS = listOf(
    "-Xms256m",
    "-Xmx1G",
    "-XX:+UseSerialGC",
    "-XX:+UseCompactObjectHeaders",
    "-XX:-UsePerfData",
    "-Xss1m",
)
