package dev.blockwright.orchestrator.bench

import java.time.Instant

object ReportBuilder {
    fun build(results: List<ProfileResult>, verdicts: List<Verdict>, strict: Boolean, startedAt: Instant): BenchReport =
        BenchReport(
            schemaVersion = BenchReport.SCHEMA_VERSION,
            startedAt = startedAt.toString(),
            gitSha = System.getenv("GITHUB_SHA"),
            os = System.getProperty("os.name"),
            arch = System.getProperty("os.arch"),
            jdk = Runtime.version().toString(),
            cpus = Runtime.getRuntime().availableProcessors(),
            strict = strict,
            passed = verdicts.none { it.status == VerdictStatus.FAIL },
            profiles = results.map { profile(it, verdicts.filter { v -> v.profile == it.name }) },
        )

    private fun profile(result: ProfileResult, verdicts: List<Verdict>) = ProfileReport(
        name = result.name,
        required = result.config.required,
        targetAvailable = result.targetAvailable,
        clients = result.config.clients,
        warmRuns = result.config.warmRuns,
        idleSeconds = result.config.idleSeconds,
        activeSeconds = result.config.activeSeconds,
        screenshots = result.config.screenshots,
        metrics = verdicts.map { metric(result, it) },
    )

    private fun metric(result: ProfileResult, verdict: Verdict): MetricReport {
        val value = result.metrics[verdict.metric] as? Measurement.Value
        return MetricReport(
            metric = verdict.metric.key,
            unit = verdict.metric.unit,
            status = verdict.status.name,
            samples = value?.samples ?: emptyList(),
            aggregate = value?.aggregate,
            budget = verdict.budget,
            reason = verdict.reason,
        )
    }
}
