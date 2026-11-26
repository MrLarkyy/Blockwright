package dev.blockwright.launcher.platform

import dev.blockwright.launcher.manifest.OsRule
import dev.blockwright.launcher.manifest.Rule
import dev.blockwright.launcher.manifest.RuleAction.ALLOW
import dev.blockwright.launcher.manifest.RuleAction.DISALLOW
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleEvaluatorTest {
    private val linux = RuleEvaluator(Platform("linux", "x86_64", "6.8.0"))
    private val mac = RuleEvaluator(Platform("osx", "arm64", "15.1"))
    private val windows10 = RuleEvaluator(Platform("windows", "x86_64", "10.0"))
    private val windows32 = RuleEvaluator(Platform("windows", "x86", "10.0"))

    @Test
    fun noRulesAllow() {
        assertTrue(linux.allows(null))
        assertTrue(linux.allows(emptyList()))
    }

    @Test
    fun rulesDefaultToDisallowWhenNothingMatches() {
        val osxOnly = listOf(Rule(ALLOW, OsRule(name = "osx")))
        assertTrue(mac.allows(osxOnly))
        assertFalse(linux.allows(osxOnly))
    }

    @Test
    fun lastMatchingRuleWins() {
        val allButOsx = listOf(Rule(ALLOW), Rule(DISALLOW, OsRule(name = "osx")))
        assertTrue(linux.allows(allButOsx))
        assertTrue(windows10.allows(allButOsx))
        assertFalse(mac.allows(allButOsx))
    }

    @Test
    fun archX86MeansThirtyTwoBit() {
        val only32 = listOf(Rule(ALLOW, OsRule(name = "windows", arch = "x86")))
        assertTrue(windows32.allows(only32))
        assertFalse(windows10.allows(only32))
    }

    @Test
    fun versionIsARegexSearchedInOsVersion() {
        val win10 = listOf(Rule(ALLOW, OsRule(name = "windows", version = "^10\\.")))
        assertTrue(windows10.allows(win10))
        assertFalse(RuleEvaluator(Platform("windows", "x86_64", "6.1")).allows(win10))
        assertFalse(windows10.allows(listOf(Rule(ALLOW, OsRule(version = "[unclosed")))))
    }

    @Test
    fun featureRulesNeverMatch() {
        val features = buildJsonObject { put("is_demo_user", JsonPrimitive(true)) }
        assertFalse(linux.allows(listOf(Rule(ALLOW, features = features))))
        assertTrue(linux.allows(listOf(Rule(ALLOW), Rule(DISALLOW, features = features))))
    }
}
