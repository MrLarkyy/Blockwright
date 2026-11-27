package dev.blockwright.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AgentProfileTest {
    @Test
    fun absentPropertyLeavesTheClientUntouched() {
        assertEquals(AgentProfile.NONE, AgentProfile.parse(null))
        assertEquals(AgentProfile.NONE, AgentProfile.parse(" "))
        assertFalse(AgentProfile.NONE.cutsActive)
    }

    @Test
    fun onlyLogicCuts() {
        assertEquals(AgentProfile.LOGIC, AgentProfile.parse("logic"))
        assertEquals(AgentProfile.REFERENCE, AgentProfile.parse("reference"))
        assertTrue(AgentProfile.LOGIC.cutsActive)
        assertFalse(AgentProfile.REFERENCE.cutsActive)
    }

    @Test
    fun unknownProfileFailsInsteadOfRunningUncut() {
        val error = assertThrows<IllegalArgumentException> { AgentProfile.parse("logci") }
        assertTrue("logci" in error.message!!)
    }
}
