package dev.blockwright.orchestrator.bench

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BenchMainTest {
    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = BenchMain.run(args.toList(), PrintStream(out), PrintStream(err))
        return Run(code, out.toString(), err.toString())
    }

    private fun budgets(dir: Path, budgets: String, required: Boolean = true): Path {
        val file = dir.resolve("budgets.json")
        Files.writeString(
            file,
            """{"schemaVersion":1,"profiles":{"stub":{"required":$required,"clients":1,"warmRuns":1,
            "idleSeconds":1,"activeSeconds":1,"screenshots":2,"startTimeoutSeconds":60,"budgets":{$budgets}}}}""",
        )
        return file
    }

    @Test
    fun aBreachExitsOneNamesMetricAndBudgetAndStillWritesTheReport(@TempDir dir: Path) {
        val result = run("--budgets", budgets(dir, """"coldStartMs":1""").toString(), "--report-dir", dir.resolve("r").toString())
        assertEquals(1, result.code)
        assertTrue("metric=coldStartMs" in result.err && "budget=1.00" in result.err, result.err)
        assertTrue(Files.isRegularFile(dir.resolve("r").resolve(ReportWriter.JSON_FILE)))
    }

    @Test
    fun generousBudgetsPassAndARerunOverwritesTheReport(@TempDir dir: Path) {
        val file = budgets(dir, """"coldStartMs":60000,"screenshotP95Ms":60000,"cpuActivePercent":10000""").toString()
        val report = dir.resolve("r").resolve(ReportWriter.JSON_FILE)
        assertEquals(0, run("--budgets", file, "--report-dir", dir.resolve("r").toString()).code)
        Files.delete(report)
        assertEquals(0, run("--budgets", file, "--report-dir", dir.resolve("r").toString()).code)
        assertTrue(Files.isRegularFile(report))
    }

    @Test
    fun strictFailsABudgetedMetricThatCouldNotRun(@TempDir dir: Path) {
        val os = System.getProperty("os.name")
        assumeFalse(os.startsWith("Linux"))
        val file = budgets(dir, """"pssMiB":1000""").toString()
        val result = run("--budgets", file, "--report-dir", dir.resolve("r").toString(), "--strict")
        assertEquals(1, result.code)
        assertTrue("metric=pssMiB" in result.err)
    }

    @Test
    fun aRequiredProfileWithoutATargetFails(@TempDir dir: Path) {
        val file = dir.resolve("b.json")
        Files.writeString(file, """{"schemaVersion":1,"profiles":{"logic":{"required":true}}}""")
        assertEquals(1, run("--budgets", file.toString(), "--report-dir", dir.resolve("r").toString()).code)
    }

    @Test
    fun anOptionalProfileWithoutATargetIsNotRun(@TempDir dir: Path) {
        val file = dir.resolve("b.json")
        Files.writeString(file, """{"schemaVersion":1,"profiles":{"logic":{"required":false}}}""")
        val result = run("--budgets", file.toString(), "--report-dir", dir.resolve("r").toString(), "--strict")
        assertEquals(0, result.code)
    }

    @Test
    fun badArgumentsAndMissingFilesExitTwo(@TempDir dir: Path) {
        assertEquals(2, run("--nope").code)
        assertEquals(2, run("--budgets", dir.resolve("missing.json").toString(), "--report-dir", dir.toString()).code)
        assertEquals(2, run("--budgets").code)
    }

    @Test
    fun invalidBudgetsExitTwoNamingTheKey(@TempDir dir: Path) {
        val result = run("--budgets", budgets(dir, """"bogusMetric":1""").toString(), "--report-dir", dir.toString())
        assertEquals(2, result.code)
        assertTrue("bogusMetric" in result.err)
    }
}
