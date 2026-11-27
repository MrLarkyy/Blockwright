package dev.blockwright.orchestrator.bench

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClientBenchClientTest {
    private val waits = mutableListOf<Duration>()
    private var stops = 0
    private val client = ClientBenchClient(ProcessHandle.current().pid(), { waits += it }, { stops++ })

    // The client registers its pid with the reaper; closing untracks it so the hook never touches this JVM.
    @AfterEach
    fun close() = client.close()

    @Test
    fun readyMeansJoined() {
        client.awaitReady(90.seconds)
        assertEquals(listOf(90.seconds), waits)
    }

    @Test
    fun closeStopsTheClientOnce() {
        client.close()
        client.close()
        assertEquals(1, stops)
    }

    @Test
    fun hasNoScreenshotOrLoadYet() {
        assertFalse(client.supportsLoad)
        assertThrows<BenchException> { client.screenshot(1.seconds) }
        assertThrows<BenchException> { client.startLoad() }
        client.stopLoad()
    }

    @Test
    fun aFailedJoinReachesTheRunner() {
        val failing = ClientBenchClient(ProcessHandle.current().pid(), { throw IllegalStateException("refused") }, {})
        assertThrows<IllegalStateException> { failing.awaitReady(1.seconds) }
        failing.close()
    }
}
