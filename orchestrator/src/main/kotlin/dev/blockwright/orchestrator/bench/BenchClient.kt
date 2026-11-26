package dev.blockwright.orchestrator.bench

import kotlin.time.Duration

interface BenchClient : AutoCloseable {
    val pid: Long

    fun awaitReady(timeout: Duration)

    fun screenshot(timeout: Duration)

    fun startLoad()

    fun stopLoad()
}
