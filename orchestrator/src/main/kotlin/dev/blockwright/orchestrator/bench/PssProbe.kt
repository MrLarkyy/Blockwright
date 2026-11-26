package dev.blockwright.orchestrator.bench

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Proportional set size of a process and its descendants, read from /proc (Linux only). */
class PssProbe(
    private val os: String = System.getProperty("os.name"),
    private val procRoot: Path = Path.of("/proc"),
) {
    fun measure(pid: Long): Measurement {
        if (!os.startsWith("Linux", ignoreCase = true)) {
            return Measurement.NotRun("PSS needs /proc/<pid>/smaps_rollup, Linux only")
        }
        val descendants = ProcessHandle.of(pid).map { handle -> handle.descendants().map { it.pid() }.toList() }.orElse(emptyList())
        val rootKiB = pssKiB(pid) ?: return Measurement.Failed("cannot read PSS of process $pid under $procRoot")
        // A descendant may exit between listing and reading; it then contributes nothing.
        val totalKiB = rootKiB + descendants.sumOf { pssKiB(it) ?: 0L }
        val mib = totalKiB / KIB_PER_MIB
        return Measurement.Value(listOf(mib), mib)
    }

    private fun pssKiB(pid: Long): Long? {
        val dir = procRoot.resolve(pid.toString())
        for (name in listOf("smaps_rollup", "smaps")) {
            try {
                return parse(Files.readString(dir.resolve(name)))
            } catch (_: IOException) {
                continue
            }
        }
        return null
    }

    companion object {
        private const val KIB_PER_MIB = 1024.0
        private val pssLine = Regex("^Pss:\\s+(\\d+)\\s+kB", RegexOption.MULTILINE)

        /** Sums every "Pss:" line: one in smaps_rollup, one per mapping in smaps. */
        fun parse(text: String): Long = pssLine.findAll(text).sumOf { it.groupValues[1].toLong() }
    }
}
