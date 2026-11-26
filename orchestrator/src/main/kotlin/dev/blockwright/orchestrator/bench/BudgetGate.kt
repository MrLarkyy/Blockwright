package dev.blockwright.orchestrator.bench

import dev.blockwright.orchestrator.bench.VerdictStatus.FAIL
import dev.blockwright.orchestrator.bench.VerdictStatus.NOT_RUN
import dev.blockwright.orchestrator.bench.VerdictStatus.PASS

/** Turns measurements into verdicts. In strict mode a budgeted metric that could not run fails. */
class BudgetGate(private val strict: Boolean) {
    fun evaluate(result: ProfileResult): List<Verdict> = Metric.entries.map { metric ->
        val budget = result.config.budgets[metric.key]
        if (!result.targetAvailable) return@map missingTarget(result, metric, budget)
        when (val measurement = result.metrics[metric] ?: Measurement.NotRun("not measured")) {
            is Measurement.Value -> value(result.name, metric, measurement.aggregate, budget)
            is Measurement.NotRun -> notRun(result.name, metric, budget, measurement.reason)
            is Measurement.Failed -> Verdict(result.name, metric, FAIL, null, budget, measurement.reason)
        }
    }

    private fun missingTarget(result: ProfileResult, metric: Metric, budget: Double?): Verdict {
        val status = if (result.config.required) FAIL else NOT_RUN
        return Verdict(result.name, metric, status, null, budget, "no bench target for profile ${result.name}")
    }

    private fun value(profile: String, metric: Metric, value: Double, budget: Double?): Verdict = when {
        budget == null -> Verdict(profile, metric, PASS, value, null, "no budget")
        value > budget -> Verdict(profile, metric, FAIL, value, budget, null)
        else -> Verdict(profile, metric, PASS, value, budget, null)
    }

    private fun notRun(profile: String, metric: Metric, budget: Double?, reason: String): Verdict {
        val status = if (strict && budget != null) FAIL else NOT_RUN
        return Verdict(profile, metric, status, null, budget, reason)
    }
}
