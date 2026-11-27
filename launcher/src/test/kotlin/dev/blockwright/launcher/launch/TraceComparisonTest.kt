package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TraceComparisonTest {
    private val header = "tick dx dy dz vx vy vz onGround input"
    private val reference = listOf(header, "0 0.0 0.0 0.0 0.0 -0.0784 0.0 true -", "1 0.0 0.0 0.098 0.0 -0.0784 0.0535 true F")

    @Test
    fun identicalTracesMatch() {
        assertEquals(emptyList<String>(), TraceComparison.differences(reference, reference))
    }

    @Test
    fun positionsMayDifferWithinTheTolerance() {
        val shifted = listOf(header, "0 0.0 0.0 0.0 0.0 -0.0784 0.0 true -", "1 0.0 0.0 0.0980000000001 0.0 -0.0784 0.0535 true F")
        assertEquals(emptyList<String>(), TraceComparison.differences(reference, shifted))
        val moved = listOf(header, "0 0.0 0.0 0.0 0.0 -0.0784 0.0 true -", "1 0.0 0.0 0.0981 0.0 -0.0784 0.0535 true F")
        assertEquals(1, TraceComparison.differences(reference, moved).size)
    }

    @Test
    fun velocityGroundAndInputMustBeExact() {
        for (changed in listOf(
            "1 0.0 0.0 0.098 0.0 -0.0784 0.05350000001 true F",
            "1 0.0 0.0 0.098 0.0 -0.0784 0.0535 false F",
            "1 0.0 0.0 0.098 0.0 -0.0784 0.0535 true FJ",
        )) {
            assertEquals(1, TraceComparison.differences(reference, listOf(header, reference[1], changed)).size, changed)
        }
    }

    @Test
    fun differentLengthsAreReported() {
        assertTrue(TraceComparison.differences(reference, reference.dropLast(1)).any { it.startsWith("length") })
    }
}
