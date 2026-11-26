package dev.blockwright.orchestrator.bench

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object BudgetsLoader {
    const val SCHEMA_VERSION = 1

    fun load(path: Path): BudgetsFile {
        if (!Files.isRegularFile(path)) throw BenchConfigException("budgets file not found: $path")
        return parse(Files.readString(path))
    }

    fun parse(text: String): BudgetsFile {
        val file = try {
            Json.decodeFromString<BudgetsFile>(text)
        } catch (e: SerializationException) {
            throw BenchConfigException("budgets file is invalid: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw BenchConfigException("budgets file is invalid: ${e.message}", e)
        }
        if (file.schemaVersion != SCHEMA_VERSION) {
            throw BenchConfigException("schemaVersion ${file.schemaVersion} is not supported, expected $SCHEMA_VERSION")
        }
        file.profiles.forEach { (name, profile) -> validate(name, profile) }
        return file
    }

    private fun validate(name: String, profile: ProfileBudget) {
        check(profile.clients >= 1) { "profile $name: clients must be at least 1" }
        check(profile.warmRuns >= 0) { "profile $name: warmRuns must not be negative" }
        check(profile.screenshots >= 1) { "profile $name: screenshots must be at least 1" }
        check(profile.idleSeconds >= 1 && profile.activeSeconds >= 1) {
            "profile $name: idleSeconds and activeSeconds must be at least 1"
        }
        check(profile.startTimeoutSeconds >= 1) { "profile $name: startTimeoutSeconds must be at least 1" }
        profile.budgets.forEach { (key, budget) ->
            val metric = Metric.fromKey(key) ?: throw BenchConfigException("profile $name: unknown metric key '$key'")
            check(budget > 0) { "profile $name: budget '$key' must be positive, got $budget" }
            val warmSamples = profile.clients - 1 + profile.warmRuns
            check(metric != Metric.WarmStart || warmSamples > 0) {
                "profile $name: budget '$key' needs a warm sample, set clients > 1 or warmRuns > 0"
            }
        }
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw BenchConfigException(message())
    }
}
