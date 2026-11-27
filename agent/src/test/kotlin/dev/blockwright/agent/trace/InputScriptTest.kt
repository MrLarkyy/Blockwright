package dev.blockwright.agent.trace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InputScriptTest {
    private val script = InputScript.WALK_JUMP_STRAFE

    @Test
    fun hasAFixedLengthAndEndsAfterIt() {
        assertEquals(110, script.length)
        assertNull(script.at(110))
        assertNull(script.at(-1))
    }

    @Test
    fun settlesFirstThenExercisesEveryMovement() {
        assertEquals(ScriptedInput(), script.at(0))
        assertEquals(ScriptedInput(), script.at(19))
        val inputs = (0 until script.length).map { script.at(it)!! }
        assertTrue(inputs.any { it.forward })
        assertTrue(inputs.any { it.jump })
        assertTrue(inputs.any { it.left })
        assertTrue(inputs.any { it.right })
        assertTrue(inputs.any { it.sprint })
        assertEquals(ScriptedInput(), inputs.last())
    }

    @Test
    fun isDeterministic() {
        assertEquals((0 until 110).map { script.at(it) }, (0 until 110).map { InputScript.named("walk-jump-strafe").at(it) })
    }

    @Test
    fun namesResolveToBuiltIns() {
        assertSame(script, InputScript.named(null))
        assertThrows<IllegalArgumentException> { InputScript.named("fly") }
    }

    @Test
    fun traceLinesKeepFullPrecisionAndInputLetters() {
        val line = MovementTracer.line(3, 0.1, -0.0784000015258789, 0.0, 1e-17, 0.0, -0.5, true, ScriptedInput(forward = true, jump = true, sprint = true))
        assertEquals("3 0.1 -0.0784000015258789 0.0 1.0E-17 0.0 -0.5 true FJs", line)
        assertEquals("-", MovementTracer.inputLetters(ScriptedInput()))
    }
}
