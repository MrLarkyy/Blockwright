package dev.blockwright.orchestrator.bench

import kotlinx.serialization.Serializable

@Serializable
data class BenchReport(
    val schemaVersion: Int,
    /** Informational only; no logic reads it. */
    val startedAt: String,
    val gitSha: String?,
    val os: String,
    val arch: String,
    val jdk: String,
    val cpus: Int,
    val strict: Boolean,
    val passed: Boolean,
    val profiles: List<ProfileReport>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}
