package dev.blockwright.launcher

import dev.blockwright.launcher.cache.CacheLayout
import dev.blockwright.launcher.download.CacheFileLock
import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.download.DownloadPolicy
import dev.blockwright.launcher.download.DownloadSpec
import dev.blockwright.launcher.download.Downloader
import dev.blockwright.launcher.download.HttpTransport
import dev.blockwright.launcher.download.Sha1
import dev.blockwright.launcher.manifest.Artifact
import dev.blockwright.launcher.manifest.AssetIndex
import dev.blockwright.launcher.manifest.AssetIndexRef
import dev.blockwright.launcher.manifest.ManifestJson
import dev.blockwright.launcher.manifest.VersionDetails
import dev.blockwright.launcher.manifest.VersionEntry
import dev.blockwright.launcher.manifest.VersionManifest
import dev.blockwright.launcher.natives.ExtractionLimits
import dev.blockwright.launcher.natives.NativeJar
import dev.blockwright.launcher.natives.NativesExtractor
import dev.blockwright.launcher.platform.Platform
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/**
 * Resolves a version into the shared cache: version JSON, client jar, the platform's libraries, extracted legacy
 * natives and, on request, assets, each verified by size and SHA-1.
 *
 * Cache first: a cached version JSON whose SHA-1 matches the one recorded from the manifest is used without
 * fetching the manifest, and files that verify are never requested, so a rerun on a complete cache needs no
 * network. A missing or corrupt file is downloaded again.
 */
class VersionResolver(
    private val cache: CacheLayout,
    transport: HttpTransport,
    private val policy: DownloadPolicy = DownloadPolicy(),
    platform: Platform = Platform.current(),
    limits: ExtractionLimits = ExtractionLimits(),
    private val manifestUri: URI = MANIFEST_URI,
    private val assetBaseUri: URI = ASSET_BASE_URI,
) {
    private val downloader = Downloader(transport, policy)
    private val selector = LibrarySelector(platform)
    private val extractor = NativesExtractor(limits)

    fun resolve(request: ResolveRequest): ResolvedVersion {
        val id = request.versionId
        val details = versionDetails(id)
        val selection = selector.select(details.libraries)
        val clientJar = cache.clientJar(id)
        val client = details.downloads.client
        val clientSpec = DownloadSpec(uri(client.url), clientJar, client.sha1, client.size, client.size)
        val librarySpecs = (selection.classpath + selection.natives.map { it.artifact }).map(::librarySpec)
        downloader.ensureAll(listOf(clientSpec) + librarySpecs)
        val nativeJars = selection.natives.map { NativeJar(libraryPath(it.artifact), it.artifact.sha1, it.excludes) }
        val nativesDir = extractor.extract(nativeJars, cache.nativesDir(id))
        val assetIndex = details.assetIndex?.takeIf { request.includeAssets }
        val assetsDir = assetIndex?.let(::ensureAssets)
        return ResolvedVersion(
            id = id,
            mainClass = details.mainClass,
            clientJar = clientJar,
            // listOf: Path is Iterable<Path>, so `+ clientJar` would append its name elements.
            classpath = selection.classpath.map(::libraryPath) + listOf(clientJar),
            nativesDir = nativesDir,
            assetsDir = assetsDir,
            assetIndexId = assetIndex?.id,
            javaMajor = details.javaVersion?.majorVersion,
            details = details,
        )
    }

    private fun versionDetails(id: String): VersionDetails {
        val json = cache.versionJson(id)
        cachedDetails(id, json)?.let { return it }
        // Locked on the sidecar (not the JSON, which the downloader locks) so concurrent resolves fetch the
        // manifest once: the waiter finds the verified JSON when it gets the lock.
        return CacheFileLock.withLock(cache.versionJsonSha1(id)) {
            cachedDetails(id, json) ?: fetchDetails(id, json)
        }
    }

    private fun fetchDetails(id: String, json: Path): VersionDetails {
        val entry = manifestEntry(id)
        downloader.ensure(DownloadSpec(uri(entry.url), json, entry.sha1, null, policy.jsonMaxBytes))
        writeAtomically(cache.versionJsonSha1(id), entry.sha1.lowercase())
        return ManifestJson.decode(VersionDetails.serializer(), json)
    }

    private fun cachedDetails(id: String, json: Path): VersionDetails? {
        val sidecar = cache.versionJsonSha1(id)
        if (!Files.isRegularFile(json) || !Files.isRegularFile(sidecar)) return null
        if (!Sha1.of(json).equals(Files.readString(sidecar).trim(), ignoreCase = true)) return null
        return try {
            ManifestJson.decode(VersionDetails.serializer(), json)
        } catch (_: DownloadException) {
            null
        }
    }

    private fun manifestEntry(id: String): VersionEntry {
        val target = cache.manifest()
        val refreshFailure = try {
            downloader.ensure(DownloadSpec(manifestUri, target, null, null, policy.jsonMaxBytes))
            null
        } catch (e: DownloadException) {
            if (!Files.isRegularFile(target)) throw e
            e
        }
        val manifest = ManifestJson.decode(VersionManifest.serializer(), target)
        return manifest.versions.firstOrNull { it.id == id }
            ?: throw DownloadException("unknown version: $id", refreshFailure)
    }

    private fun ensureAssets(ref: AssetIndexRef): Path {
        val indexFile = cache.assetIndex(ref.id)
        downloader.ensure(DownloadSpec(uri(ref.url), indexFile, ref.sha1, ref.size, ref.size))
        val index = ManifestJson.decode(AssetIndex.serializer(), indexFile)
        val specs = index.objects.values.distinctBy { it.hash }.map { obj ->
            val target = cache.assetObject(obj.hash)
            DownloadSpec(assetBaseUri.resolve("${obj.hash.substring(0, 2)}/${obj.hash}"), target, obj.hash, obj.size, obj.size)
        }
        downloader.ensureAll(specs)
        return cache.assetsDir()
    }

    private fun librarySpec(artifact: Artifact): DownloadSpec =
        DownloadSpec(uri(artifact.url), libraryPath(artifact), artifact.sha1, artifact.size, artifact.size)

    private fun libraryPath(artifact: Artifact): Path =
        cache.library(artifact.path ?: throw DownloadException("library download ${artifact.url} has no path"))

    private fun uri(value: String): URI =
        try {
            URI(value)
        } catch (e: URISyntaxException) {
            throw DownloadException("invalid URL in version metadata: ${value.take(MAX_SHOWN)}", e)
        }

    private fun writeAtomically(target: Path, content: String) {
        val temp = target.resolveSibling("${target.fileName}.tmp-${UUID.randomUUID()}")
        try {
            Files.writeString(temp, content)
            try {
                Files.move(temp, target, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        val MANIFEST_URI: URI = URI("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        val ASSET_BASE_URI: URI = URI("https://resources.download.minecraft.net/")
        private const val MAX_SHOWN = 120
    }
}
