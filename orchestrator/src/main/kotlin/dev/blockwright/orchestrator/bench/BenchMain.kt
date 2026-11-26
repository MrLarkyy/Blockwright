package dev.blockwright.orchestrator.bench

import java.io.PrintStream
import java.time.Instant
import kotlin.system.exitProcess

/** Exit codes: 0 pass, 1 budget breach or a required profile without target, 2 configuration or usage error. */
object BenchMain {
    const val EXIT_PASS = 0
    const val EXIT_BREACH = 1
    const val EXIT_CONFIG = 2

    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList(), System.out, System.err))
    }

    fun run(args: List<String>, out: PrintStream, err: PrintStream, runner: BenchRunner = BenchRunner()): Int {
        val startedAt = Instant.now()
        val options: BenchOptions
        val budgets: BudgetsFile
        try {
            options = BenchOptions.parse(args)
            budgets = BudgetsLoader.load(options.budgets)
            options.profile?.let { if (it !in budgets.profiles) throw BenchConfigException("profile '$it' is not in the budgets file") }
        } catch (e: BenchConfigException) {
            err.println("config error: ${e.message}")
            return EXIT_CONFIG
        }
        val names = options.profile?.let { listOf(it) } ?: budgets.profiles.keys.sorted()
        val results = names.map { name -> measure(runner, name, budgets.profiles.getValue(name)) }
        val gate = BudgetGate(options.strict)
        val verdicts = results.flatMap(gate::evaluate)
        val report = ReportBuilder.build(results, verdicts, options.strict, startedAt)
        val file = ReportWriter.write(report, verdicts, options.reportDir)
        verdicts.forEach { (if (it.status == VerdictStatus.FAIL) err else out).println(it.message()) }
        out.println("report: $file")
        return if (report.passed) EXIT_PASS else EXIT_BREACH
    }

    private fun measure(runner: BenchRunner, name: String, config: ProfileBudget): ProfileResult {
        val target = BenchTargets.forProfile(name) ?: return ProfileResult(name, config, false, emptyMap())
        return runner.run(name, config, target)
    }
}
