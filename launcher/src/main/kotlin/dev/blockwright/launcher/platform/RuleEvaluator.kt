package dev.blockwright.launcher.platform

import dev.blockwright.launcher.manifest.OsRule
import dev.blockwright.launcher.manifest.Rule
import dev.blockwright.launcher.manifest.RuleAction
import java.util.regex.PatternSyntaxException

/**
 * Mojang rule semantics: no rules allows; otherwise the default is disallow and every matching rule sets the
 * outcome, so the last match wins. A rule naming launcher features never matches because no feature is enabled
 * when libraries are selected.
 */
class RuleEvaluator(private val platform: Platform) {
    fun allows(rules: List<Rule>?): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule)) allowed = rule.action == RuleAction.ALLOW
        }
        return allowed
    }

    private fun matches(rule: Rule): Boolean {
        if (!rule.features.isNullOrEmpty()) return false
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
