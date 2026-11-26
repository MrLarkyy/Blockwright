package dev.blockwright.serverrunner

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class JavaRequirementTest {
    private fun build(javaMinimum: Int) = PaperBuild("26.3", 262, "STABLE", "paper.jar", "ab".repeat(32), 1, "https://fill-data.papermc.io/x", javaMinimum)

    @Test
    fun minimumAboveTheRuntimeNamesBoth() {
        val error = assertThrows(ServerStartException::class.java) { JavaRequirement.check(build(26), customJava = null, runtimeFeature = 25) }

        assertTrue(error.message!!.contains("Java 26") && error.message!!.contains("Java 25"), error.message)
    }

    @Test
    fun equalMinimumPasses() {
        assertDoesNotThrow { JavaRequirement.check(build(25), customJava = null, runtimeFeature = 25) }
    }

    @Test
    fun customJavaIsNotProbed() {
        assertDoesNotThrow { JavaRequirement.check(build(99), customJava = Path.of("custom-java"), runtimeFeature = 25) }
    }

    @Test
    fun currentJavaIsTheRunningRuntime() {
        assertTrue(Files.isExecutable(JavaRequirement.currentJava()))
    }
}
