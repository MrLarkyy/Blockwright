package dev.blockwright.orchestrator.bench

import java.util.Locale

data class Verdict(
    val profile: String,
    val metric: Metric,
    val status: VerdictStatus,
    val value: Double?,
    val budget: Double?,
    val reason: String?,
) {
    fun message(): String {
        val head = "${status.name.replace('_', ' ')} profile=$profile metric=${metric.key}"
        val numbers = buildString {
            value?.let { append(" value=${format(it)}") }
            budget?.let { append(" budget=${format(it)}") }
            if (value != null || budget != null) append(" unit=${metric.unit}")
        }
        return head + numbers + (reason?.let { " reason=$it" } ?: "")
    }

    private fun format(number: Double) = String.format(Locale.ROOT, "%.2f", number)
}
