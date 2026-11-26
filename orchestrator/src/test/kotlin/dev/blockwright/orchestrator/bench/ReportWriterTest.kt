package dev.blockwright.orchestrator.bench

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ReportWriterTest {
    private fun build(): Pair<BenchReport, List<Verdict>> {
        val config = ProfileBudget(budgets = mapOf("pssMiB" to 1.0, "coldStartMs" to 100.0))
        val metrics = mapOf(
            Metric.Pss to Measurement.Value(listOf(5.0), 5.0),
            Metric.ColdStart to Measurement.Value(listOf(50.0), 50.0),
            Metric.CpuIdle to Measurement.NotRun("n/a"),
        )
        val result = ProfileResult("p", config, true, metrics)
        val verdicts = BudgetGate(false).evaluate(result)
        return ReportBuilder.build(listOf(result), verdicts, false, Instant.EPOCH) to verdicts
    }

    @Test
    fun jsonDecodesBackWithEveryMetricAndItsStatus(@TempDir dir: Path) {
        val (report, verdicts) = build()
        val file = ReportWriter.write(report, verdicts, dir)
        val decoded = ReportWriter.json.decodeFromString(BenchReport.serializer(), Files.readString(file))
        assertEquals(report, decoded)
        assertEquals(Metric.entries.map { it.key }, decoded.profiles.single().metrics.map { it.metric })
        assertFalse(decoded.passed)
        assertEquals("FAIL", decoded.profiles.single().metrics.first { it.metric == "pssMiB" }.status)
    }

    @Test
    fun summaryListsTheFailures(@TempDir dir: Path) {
        val (report, verdicts) = build()
        ReportWriter.write(report, verdicts, dir)
        val summary = Files.readString(dir.resolve(ReportWriter.SUMMARY_FILE))
        assertTrue("FAIL profile=p metric=pssMiB value=5.00 budget=1.00 unit=MiB" in summary)
    }
}
