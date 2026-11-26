package dev.blockwright.orchestrator.bench

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BudgetGateTest {
    private fun result(
        metrics: Map<Metric, Measurement>,
        budgets: Map<String, Double> = mapOf("pssMiB" to 100.0),
        required: Boolean = false,
        target: Boolean = true,
    ) = ProfileResult("p", ProfileBudget(required = required, budgets = budgets), target, metrics)

    private fun pss(gate: BudgetGate, result: ProfileResult) = gate.evaluate(result).first { it.metric == Metric.Pss }

    private fun value(v: Double) = Measurement.Value(listOf(v), v)

    @Test
    fun valueUnderAndEqualToBudgetPass() {
        assertEquals(VerdictStatus.PASS, pss(BudgetGate(false), result(mapOf(Metric.Pss to value(99.0)))).status)
        assertEquals(VerdictStatus.PASS, pss(BudgetGate(false), result(mapOf(Metric.Pss to value(100.0)))).status)
    }

    @Test
    fun valueOverBudgetFailsAndTheMessageNamesEverything() {
        val verdict = pss(BudgetGate(false), result(mapOf(Metric.Pss to value(100.5))))
        assertEquals(VerdictStatus.FAIL, verdict.status)
        assertEquals("FAIL profile=p metric=pssMiB value=100.50 budget=100.00 unit=MiB", verdict.message())
    }

    @Test
    fun notRunIsUngatedUnlessStrict() {
        val measured = result(mapOf(Metric.Pss to Measurement.NotRun("no proc")))
        assertEquals(VerdictStatus.NOT_RUN, pss(BudgetGate(false), measured).status)
        assertEquals(VerdictStatus.FAIL, pss(BudgetGate(true), measured).status)
    }

    @Test
    fun strictIgnoresNotRunWithoutABudget() {
        val measured = result(mapOf(Metric.Pss to Measurement.NotRun("no proc")), budgets = emptyMap())
        assertEquals(VerdictStatus.NOT_RUN, pss(BudgetGate(true), measured).status)
    }

    @Test
    fun strictNotRunFailureHasTheFullShape() {
        val verdict = pss(BudgetGate(true), result(mapOf(Metric.Pss to Measurement.NotRun("no proc"))))
        assertTrue(verdict.message().startsWith("FAIL profile=p metric=pssMiB value=NOT_RUN budget=100.00 unit=MiB"))
    }

    @Test
    fun failedMeasurementFails() {
        val verdict = pss(BudgetGate(false), result(mapOf(Metric.Pss to Measurement.Failed("boom"))))
        assertEquals(VerdictStatus.FAIL, verdict.status)
        assertTrue("boom" in verdict.message())
    }

    @Test
    fun requiredProfileWithoutTargetFailsAndOptionalIsNotRun() {
        val required = BudgetGate(false).evaluate(result(emptyMap(), required = true, target = false))
        assertTrue(required.all { it.status == VerdictStatus.FAIL })
        val optional = BudgetGate(true).evaluate(result(emptyMap(), required = false, target = false))
        assertTrue(optional.all { it.status == VerdictStatus.NOT_RUN })
    }

    @Test
    fun metricWithoutABudgetIsReportedNotGated() {
        val verdict = pss(BudgetGate(true), result(mapOf(Metric.Pss to value(1e9)), budgets = emptyMap()))
        assertEquals(VerdictStatus.PASS, verdict.status)
        assertEquals(1e9, verdict.value)
    }
}
