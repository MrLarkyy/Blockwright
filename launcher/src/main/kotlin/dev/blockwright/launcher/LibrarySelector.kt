package dev.blockwright.launcher

import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.manifest.Artifact
import dev.blockwright.launcher.manifest.Library
import dev.blockwright.launcher.platform.Platform
import dev.blockwright.launcher.platform.RuleEvaluator

/**
 * Picks the libraries a platform needs. A library's `artifact` goes on the classpath, which includes modern
 * `natives-<os>` jars (LWJGL extracts those itself); only legacy `natives` classifiers are extracted.
 */
class LibrarySelector(private val platform: Platform) {
    private val rules = RuleEvaluator(platform)

    fun select(libraries: List<Library>): LibrarySelection {
        val classpath = LinkedHashMap<String, Artifact>()
        val natives = LinkedHashMap<String, NativeArtifact>()
        for (library in libraries) {
            if (!rules.allows(library.rules)) continue
            library.downloads?.artifact?.let { classpath.putIfAbsent(pathOf(library, it), it) }
            legacyNative(library)?.let { natives.putIfAbsent(pathOf(library, it.artifact), it) }
        }
        return LibrarySelection(classpath.values.toList(), natives.values.toList())
    }

    private fun legacyNative(library: Library): NativeArtifact? {
        val classifier = library.natives?.get(platform.osName)?.replace(ARCH_PLACEHOLDER, platform.bitness) ?: return null
        val artifact = library.downloads?.classifiers?.get(classifier) ?: return null
        return NativeArtifact(artifact, library.extract?.exclude.orEmpty())
    }

    private fun pathOf(library: Library, artifact: Artifact): String =
        artifact.path ?: throw DownloadException("library ${library.name} has a download without a path")

    private companion object {
        const val ARCH_PLACEHOLDER = "\${arch}"
    }
}
