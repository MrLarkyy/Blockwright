package dev.blockwright.orchestrator.bench

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object ReportWriter {
    const val JSON_FILE = "bench-report.json"
    const val SUMMARY_FILE = "bench-summary.md"

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** Overwrites both files; returns the JSON report path. */
    fun write(report: BenchReport, verdicts: List<Verdict>, dir: Path): Path {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(SUMMARY_FILE), summary(report, verdicts))
        val file = dir.resolve(JSON_FILE)
        Files.writeString(file, json.encodeToString(BenchReport.serializer(), report))
        return file
    }

    fun summary(report: BenchReport, verdicts: List<Verdict>): String = buildString {
        appendLine("# Benchmark report")
        appendLine()
        appendLine("Result: ${if (report.passed) "PASS" else "FAIL"} (strict=${report.strict}, ${report.os} ${report.arch}, JDK ${report.jdk})")
        appendLine()
        appendLine("| Profile | Metric | Status | Value | Budget | Unit |")
        appendLine("|---|---|---|---|---|---|")
        report.profiles.forEach { profile ->
            profile.metrics.forEach {
                appendLine("| ${profile.name} | ${it.metric} | ${it.status} | ${it.aggregate ?: "-"} | ${it.budget ?: "-"} | ${it.unit} |")
            }
        }
        val failures = verdicts.filter { it.status == VerdictStatus.FAIL }
        if (failures.isNotEmpty()) {
            appendLine()
            appendLine("## Failures")
            failures.forEach { appendLine("- ${it.message()}") }
        }
    }
}
