package dev.blockwright.orchestrator.bench

import java.nio.file.Path

data class BenchOptions(val budgets: Path, val reportDir: Path, val strict: Boolean, val profile: String?) {
    companion object {
        fun parse(args: List<String>): BenchOptions {
            var budgets: Path? = null
            var reportDir: Path? = null
            var strict = false
            var profile: String? = null
            val iterator = args.iterator()
            fun value(flag: String) = if (iterator.hasNext()) iterator.next() else throw BenchConfigException("$flag needs a value")
            while (iterator.hasNext()) {
                when (val flag = iterator.next()) {
                    "--budgets" -> budgets = Path.of(value(flag))
                    "--report-dir" -> reportDir = Path.of(value(flag))
                    "--profile" -> profile = value(flag)
                    "--strict" -> strict = true
                    else -> throw BenchConfigException("unknown argument '$flag'")
                }
            }
            return BenchOptions(
                budgets ?: throw BenchConfigException("--budgets is required"),
                reportDir ?: throw BenchConfigException("--report-dir is required"),
                strict,
                profile,
            )
        }
    }
}
