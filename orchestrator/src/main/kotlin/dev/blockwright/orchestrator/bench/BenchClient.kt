package dev.blockwright.orchestrator.bench

import kotlin.time.Duration

interface BenchClient : AutoCloseable {
    val pid: Long

    /** False while the client cannot generate CPU load on request: the active CPU metric is then not run. */
    val supportsLoad: Boolean get() = true

    fun awaitReady(timeout: Duration)

    fun screenshot(timeout: Duration)

    fun startLoad()

    fun stopLoad()
}
