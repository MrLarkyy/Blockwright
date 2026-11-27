package dev.blockwright.launcher.platform

import dev.blockwright.launcher.manifest.OsRule
import dev.blockwright.launcher.manifest.Rule
import dev.blockwright.launcher.manifest.RuleAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.regex.PatternSyntaxException

/**
 * Mojang rule semantics: no rules allows; otherwise the default is disallow and every matching rule sets the
 * outcome, so the last match wins. A rule naming launcher features matches only when every named feature has
 * that value in [features] (a missing feature is false); library selection passes none.
 */
class RuleEvaluator(private val platform: Platform) {
    fun allows(rules: List<Rule>?, features: Map<String, Boolean> = emptyMap()): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, features)) allowed = rule.action == RuleAction.ALLOW
        }
        return allowed
    }

    private fun matches(rule: Rule, features: Map<String, Boolean>): Boolean {
        val required = rule.features
        if (!required.isNullOrEmpty()) {
            val all = required.all { (name, value) -> (value as? JsonPrimitive)?.booleanOrNull == (features[name] ?: false) }
            if (!all) return false
        }
        return rule.os?.let(::matchesOs) ?: true
    }

    private fun matchesOs(os: OsRule): Boolean =
        (os.name == null || os.name == platform.osName) &&
            (os.arch == null || os.arch == platform.arch) &&
            (os.version == null || versionMatches(os.version))

    private fun versionMatches(pattern: String): Boolean =
        try {
            Regex(pattern).containsMatchIn(platform.osVersion)
        } catch (_: PatternSyntaxException) {
            false
        }
}
