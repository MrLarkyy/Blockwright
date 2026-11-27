package dev.blockwright.launcher.launch

import dev.blockwright.launcher.manifest.ManifestJson
import dev.blockwright.launcher.manifest.Rule
import dev.blockwright.launcher.manifest.VersionDetails
import dev.blockwright.launcher.platform.RuleEvaluator
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Expands the `arguments.jvm` / `arguments.game` lists of a version JSON: plain strings and `{rules, value}` entries
 * whose rules (OS and launcher features) allow them, with every `${name}` replaced from the given values. An
 * unknown placeholder fails rather than reaching the client verbatim; versions with only the legacy
 * `minecraftArguments` string are not supported.
 */
class ArgumentTemplate(private val rules: RuleEvaluator) {
    enum class Section(val key: String) { JVM("jvm"), GAME("game") }

    fun expand(details: VersionDetails, section: Section, values: Map<String, String>, features: Map<String, Boolean>): List<String> {
        val arguments = details.arguments as? JsonObject
            ?: throw LaunchException(
                if (details.minecraftArguments != null) "unsupported version ${details.id}: legacy minecraftArguments"
                else "version ${details.id} has no launch arguments",
            )
        val entries = arguments[section.key] as? JsonArray ?: return emptyList()
        return entries.flatMap { entry -> allowedValues(details.id, entry, features) }.map { substitute(details.id, it, values) }
    }

    private fun allowedValues(versionId: String, entry: JsonElement, features: Map<String, Boolean>): List<String> = when (entry) {
        is JsonPrimitive -> listOf(text(versionId, entry))
        is JsonObject -> if (rules.allows(rulesOf(versionId, entry["rules"]), features)) values(versionId, entry["value"]) else emptyList()
        else -> throw LaunchException("malformed argument in version $versionId: $entry")
    }

    private fun rulesOf(versionId: String, element: JsonElement?): List<Rule>? {
        if (element == null) return null
        return try {
            ManifestJson.json.decodeFromJsonElement(ListSerializer(Rule.serializer()), element)
        } catch (e: SerializationException) {
            throw LaunchException("malformed argument rules in version $versionId: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw LaunchException("malformed argument rules in version $versionId: ${e.message}", e)
        }
    }

    private fun values(versionId: String, element: JsonElement?): List<String> = when (element) {
        is JsonPrimitive -> listOf(text(versionId, element))
        is JsonArray -> element.map { item -> (item as? JsonPrimitive)?.let { text(versionId, it) } ?: throw LaunchException("malformed argument value in version $versionId") }
        else -> throw LaunchException("malformed argument value in version $versionId")
    }

    private fun text(versionId: String, primitive: JsonPrimitive): String =
        if (primitive.isString) primitive.content else throw LaunchException("non-string argument in version $versionId: $primitive")

    private fun substitute(versionId: String, argument: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(argument) { match ->
            val name = match.groupValues[1]
            values[name] ?: throw LaunchException("unknown placeholder \${$name} in version $versionId")
        }

    private companion object {
        val PLACEHOLDER = Regex("""\$\{([A-Za-z0-9_]+)}""")
    }
}
