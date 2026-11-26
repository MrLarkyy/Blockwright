package dev.blockwright.orchestrator.bench

enum class Metric(val key: String, val unit: String) {
    ColdStart("coldStartMs", "ms"),
    WarmStart("warmStartMs", "ms"),
    Pss("pssMiB", "MiB"),
    CpuIdle("cpuIdlePercent", "%"),
    CpuActive("cpuActivePercent", "%"),
    ScreenshotP95("screenshotP95Ms", "ms"),
    ;

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}
