package dev.blockwright.orchestrator.bench

import java.nio.file.Path

/**
 * Starts the clients of one profile. [prepare] runs once before anything is timed (a target that needs a server
 * starts it there); [close] releases whatever the target itself owns once the profile is measured, on success and
 * on failure. Clients are closed by their caller.
 */
interface BenchTarget : AutoCloseable {
    val profile: String

    fun prepare() {
        // Most targets need nothing before their first client.
    }

    /** Starts one client; the caller owns it and must close it. */
    fun launch(workDir: Path): BenchClient

    override fun close() {
        // Nothing owned by default.
    }
}
