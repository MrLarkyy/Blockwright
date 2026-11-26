package dev.blockwright.orchestrator.bench

sealed interface Measurement {
    data class Value(val samples: List<Double>, val aggregate: Double) : Measurement

    data class NotRun(val reason: String) : Measurement

    data class Failed(val reason: String) : Measurement

    companion object {
        /** The first non-value part wins; otherwise the samples of all parts are folded into one value. */
        fun combine(parts: List<Measurement>, fold: (List<Double>) -> Double): Measurement {
            parts.firstOrNull { it !is Value }?.let { return it }
            val samples = parts.flatMap { (it as Value).samples }
            return if (samples.isEmpty()) NotRun("no samples") else Value(samples, fold(samples))
        }
    }
}
