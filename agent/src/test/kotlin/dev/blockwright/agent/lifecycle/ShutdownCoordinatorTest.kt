package dev.blockwright.agent.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ShutdownCoordinatorTest {
    @Test
    fun onlyTheFirstRequestStopsTheClient() {
        val stops = AtomicInteger()
        val reasons = CopyOnWriteArrayList<String>()
        val coordinator = ShutdownCoordinator(stopClient = { stops.incrementAndGet(); true }, halt = {}, fallback = 60.seconds, onRequest = { reasons += it })

        assertTrue(coordinator.request("stdin-closed"))
        assertFalse(coordinator.request("parent-exited"))
        assertFalse(coordinator.request("stdin-closed"))

        assertEquals(1, stops.get())
        assertEquals(listOf("stdin-closed"), reasons)
        assertTrue(coordinator.isRequested)
    }

    @Test
    fun concurrentRequestsStopOnce() {
        val stops = AtomicInteger()
        val coordinator = ShutdownCoordinator(stopClient = { stops.incrementAndGet(); true }, halt = {}, fallback = 60.seconds)
        val start = CountDownLatch(1)
        val threads = List(8) { Thread { start.await(); coordinator.request("r$it") }.apply { start() } }
        start.countDown()
        threads.forEach { it.join(5_000) }
        assertEquals(1, stops.get())
    }

    @Test
    fun withoutAClientTheJvmHaltsAtOnceWithACleanCode() {
        val halts = CopyOnWriteArrayList<Int>()
        ShutdownCoordinator(stopClient = { false }, halt = { halts += it }, fallback = 60.seconds).request("stdin-closed")
        assertEquals(listOf(ShutdownCoordinator.EXIT_WITHOUT_CLIENT), halts)
    }

    @Test
    fun aClientThatDoesNotStopIsHaltedAfterTheFallback() {
        val halted = CountDownLatch(1)
        val code = AtomicInteger(-1)
        ShutdownCoordinator(stopClient = { true }, halt = { code.set(it); halted.countDown() }, fallback = 100.milliseconds).request("parent-exited")
        assertTrue(halted.await(5, TimeUnit.SECONDS))
        assertEquals(ShutdownCoordinator.EXIT_FALLBACK, code.get())
    }

    @Test
    fun aFailingStopIsTreatedAsNoClient() {
        val halts = CopyOnWriteArrayList<Int>()
        ShutdownCoordinator(stopClient = { error("boom") }, halt = { halts += it }, fallback = 60.seconds).request("stdin-closed")
        assertEquals(listOf(ShutdownCoordinator.EXIT_WITHOUT_CLIENT), halts)
    }
}
