package dev.blockwright.launcher.natives

import dev.blockwright.launcher.download.Sha1
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.relativeTo

/**
 * The `.extracted` marker of a natives directory: the source jar hashes plus size and SHA-1 of every extracted
 * file. A directory is current only when its jars match and its files are exactly the recorded ones, unchanged.
 */
internal class NativesMarker(jars: List<NativeJar>) {
    private val jarLines = jars.map { "jar ${it.sha1.lowercase()}" }.distinct().sorted()

    /** Records every file of [dir] (extracted, before the marker exists) and writes the marker into it. */
    fun write(dir: Path) {
        val fileLines = files(dir)?.map { (name, path) -> "file ${Sha1.of(path)} ${Files.size(path)} $name" }
            ?: throw IOException("unexpected non-regular file in $dir")
        Files.writeString(dir.resolve(FILE_NAME), (listOf(HEADER) + jarLines + fileLines.sorted()).joinToString("\n"))
    }

    fun matches(dir: Path): Boolean {
        val markerFile = dir.resolve(FILE_NAME)
        if (!Files.isRegularFile(markerFile, NOFOLLOW_LINKS)) return false
        val lines = Files.readAllLines(markerFile)
        if (lines.firstOrNull() != HEADER) return false
        val recordedJars = lines.filter { it.startsWith("jar ") }
        if (recordedJars != jarLines) return false
        val recorded = lines.filter { it.startsWith("file ") }.map { it.split(' ', limit = 4) }
        if (recorded.any { it.size != 4 }) return false
        val actual = files(dir)?.toMap() ?: return false
        if (actual.keys != recorded.map { it[3] }.toSet() || actual.size != recorded.size) return false
        return recorded.all { (_, sha1, size, name) ->
            val path = actual.getValue(name)
            Files.size(path).toString() == size && Sha1.of(path) == sha1
        }
    }

    /** Relative name (with `/`) to path of every file except the marker; null when anything is not a plain file. */
    private fun files(dir: Path): List<Pair<String, Path>>? {
        val entries = Files.walk(dir).use { stream -> stream.filter { it != dir }.toList() }
        val result = ArrayList<Pair<String, Path>>()
        for (entry in entries) {
            when {
                Files.isDirectory(entry, NOFOLLOW_LINKS) -> continue
                !Files.isRegularFile(entry, NOFOLLOW_LINKS) -> return null
                entry == dir.resolve(FILE_NAME) -> continue
                else -> result += entry.relativeTo(dir).joinToString("/") to entry
            }
        }
        return result
    }

    companion object {
        const val FILE_NAME = ".extracted"
        private const val HEADER = "blockwright-natives v2"
    }
}
