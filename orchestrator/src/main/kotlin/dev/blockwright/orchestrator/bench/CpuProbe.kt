package dev.blockwright.orchestrator.bench

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.toKotlinDuration

/** Total CPU time of a process and its descendants, turned into percent of one core over a window. */
class CpuProbe {
    /** Null when the OS does not report CPU time for the process. */
    fun totalCpu(pid: Long): Duration? {
        val handle = ProcessHandle.of(pid).orElse(null) ?: return null
        val own = handle.info().totalCpuDuration().map { it.toKotlinDuration() }.orElse(null) ?: return null
        val children = handle.descendants().toList().sumOf { child ->
            child.info().totalCpuDuration().map { it.toNanos() }.orElse(0L)
        }
        return own + children.nanoseconds
    }

    companion object {
        fun percent(cpuDelta: Duration, wallDelta: Duration): Double =
            if (wallDelta <= Duration.ZERO) 0.0 else cpuDelta / wallDelta * 100.0

        fun window(before: Duration?, after: Duration?, wall: Duration): Measurement {
            if (before == null || after == null) return Measurement.NotRun("CPU time is not reported for this process")
            val value = percent(after - before, wall)
            return Measurement.Value(listOf(value), value)
        }
    }
}
