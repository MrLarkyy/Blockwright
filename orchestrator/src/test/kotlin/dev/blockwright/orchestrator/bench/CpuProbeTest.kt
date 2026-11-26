package dev.blockwright.orchestrator.bench

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CpuProbeTest {
    @Test
    fun percentIsCpuOverWall() {
        assertEquals(50.0, CpuProbe.percent(500.milliseconds, 1.seconds))
        assertEquals(200.0, CpuProbe.percent(2.seconds, 1.seconds))
    }

    @Test
    fun zeroWallDeltaIsZeroPercent() {
        assertEquals(0.0, CpuProbe.percent(1.seconds, 0.seconds))
    }

    @Test
    fun missingCpuTimeIsNotRun() {
        assertInstanceOf(Measurement.NotRun::class.java, CpuProbe.window(null, 1.seconds, 1.seconds))
        assertInstanceOf(Measurement.NotRun::class.java, CpuProbe.window(1.seconds, null, 1.seconds))
    }

    @Test
    fun windowIsAValue() {
        val value = assertInstanceOf(Measurement.Value::class.java, CpuProbe.window(1.seconds, 2.seconds, 4.seconds))
        assertEquals(25.0, value.aggregate)
    }

    @Test
    fun currentProcessReportsCpuTime() {
        assertNotNull(CpuProbe().totalCpu(ProcessHandle.current().pid()))
    }
}
