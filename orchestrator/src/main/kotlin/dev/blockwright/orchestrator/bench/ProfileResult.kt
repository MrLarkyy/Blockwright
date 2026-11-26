package dev.blockwright.orchestrator.bench

/** What a profile run produced; [targetAvailable] is false when no bench target exists for the profile. */
data class ProfileResult(
    val name: String,
    val config: ProfileBudget,
    val targetAvailable: Boolean,
    val metrics: Map<Metric, Measurement>,
)
