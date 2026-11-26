package dev.blockwright.serverrunner.fill

import dev.blockwright.serverrunner.BoundedHttpClient
import dev.blockwright.serverrunner.PaperBuild
import dev.blockwright.serverrunner.PaperBuildSource
import dev.blockwright.serverrunner.PaperDownloadException
import dev.blockwright.serverrunner.UrlPolicy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Resolves Paper builds from the PaperMC Fill v3 API; every value it returns has been validated. */
class FillApiClient(
    private val http: BoundedHttpClient = BoundedHttpClient(UrlPolicy.FILL),
    private val baseUri: URI = URI("https://fill.papermc.io/v3/projects/paper/"),
    private val maxBodyBytes: Long = 4L * 1024 * 1024,
    private val timeout: Duration = 30.seconds,
) : PaperBuildSource {
    private val json = Json { ignoreUnknownKeys = true }

    override fun resolve(version: String, build: Int?): PaperBuild {
        PaperBuild.requireVersion(version)
        build?.let(PaperBuild::requireBuild)
        val versionInfo = fetch<FillVersionResponse>("versions/$version", "Paper version $version")
        val javaMinimum = versionInfo.version.java?.version?.minimum
            ?: throw PaperDownloadException("Fill names no minimum Java version for Paper $version")
        val chosen = if (build != null) {
            fetch<FillBuild>("versions/$version/builds/$build", "Paper $version build $build").also {
                if (it.id != build) throw PaperDownloadException("Fill answered build ${it.id} for Paper $version build $build")
            }
        } else {
            fetch<List<FillBuild>>("versions/$version/builds", "Paper $version builds")
                .filter { it.channel == STABLE }
                .maxByOrNull { it.id }
                ?: throw PaperDownloadException("Paper $version has no $STABLE build on Fill; pin a build explicitly")
        }
        return toPaperBuild(version, chosen, javaMinimum)
    }

    private inline fun <reified T> fetch(path: String, what: String): T {
        val uri = baseUri.resolve(path)
        val response = http.getText(uri, maxBodyBytes, timeout)
        if (response.status != 200) throw PaperDownloadException("$what: Fill answered ${response.status} (${describeError(response.body)})")
        return try {
            json.decodeFromString<T>(response.body)
        } catch (e: SerializationException) {
            throw PaperDownloadException("$what: unexpected Fill response from $uri", e)
        } catch (e: IllegalArgumentException) {
            throw PaperDownloadException("$what: unexpected Fill response from $uri", e)
        }
    }

    private fun describeError(body: String): String {
        val error = runCatching { json.decodeFromString<FillError>(body) }.getOrNull()
        return listOfNotNull(error?.error, error?.message).joinToString(": ").ifEmpty { body.take(ECHO_CHARS) }
    }

    private fun toPaperBuild(version: String, build: FillBuild, javaMinimum: Int): PaperBuild {
        val what = "Paper $version build ${build.id}"
        val download = build.downloads[SERVER_DOWNLOAD] ?: throw PaperDownloadException("$what has no $SERVER_DOWNLOAD download")
        val sha256 = download.checksums.sha256.lowercase()
        if (!PaperBuild.isSha256(sha256)) throw PaperDownloadException("$what: invalid sha256 \"${sha256.take(80)}\"")
        if (download.size !in 1..PaperBuild.MAX_JAR_BYTES) throw PaperDownloadException("$what: size ${download.size} outside (0, ${PaperBuild.MAX_JAR_BYTES}]")
        if (!PaperBuild.isJarFileName(download.name)) throw PaperDownloadException("$what: invalid file name \"${download.name.take(80)}\"")
        val url = try {
            URI(download.url)
        } catch (e: URISyntaxException) {
            throw PaperDownloadException("$what: invalid download URL", e)
        }
        http.policy.check(url)
        return PaperBuild(version, build.id, build.channel, download.name, sha256, download.size, url.toString(), javaMinimum)
    }

    private companion object {
        const val STABLE = "STABLE"
        const val SERVER_DOWNLOAD = "server:default"
        const val ECHO_CHARS = 200
    }
}
