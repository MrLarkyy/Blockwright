package dev.blockwright.orchestrator.bench

import java.nio.file.Path

interface BenchTarget {
    val profile: String

    /** Starts one client; the caller owns it and must close it. */
    fun launch(workDir: Path): BenchClient
}
