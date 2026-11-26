package dev.blockwright.launcher.natives

import dev.blockwright.launcher.download.Sha1
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.relativeTo

/**
 * The `.extracted` marker of a natives directory: the source jar hashes plus size and SHA-1 of every extracted
 * file. A directory is current when its jars match and every recorded file is present and unchanged.
 *
 * Files the marker does not record are ignored and the directory is never listed: the game writes its own
 * libraries into the natives directory at runtime, so their presence says nothing about the extracted ones.
 */
internal class NativesMarker(jars: List<NativeJar>) {
    private val jarLines = jars.map { "jar ${it.sha1.lowercase()}" }.distinct().sorted()

    /** Records every file of the freshly extracted [dir] and writes the marker into it. */
    fun write(dir: Path) {
        val fileLines = Files.walk(dir).use { paths ->
            paths.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
                .map { "file ${Sha1.of(it)} ${Files.size(it)} ${it.relativeTo(dir).joinToString("/")}" }
                .toList()
        }
        Files.writeString(dir.resolve(FILE_NAME), (listOf(HEADER) + jarLines + fileLines.sorted()).joinToString("\n"))
    }

    /** An unreadable marker or recorded file means a damaged directory, to extract again rather than fail on. */
    fun matches(dir: Path): Boolean = try {
        recordedFilesUnchanged(dir.normalize())
    } catch (_: IOException) {
        false
    }

    private fun recordedFilesUnchanged(dir: Path): Boolean {
        val markerFile = dir.resolve(FILE_NAME)
        if (!Files.isRegularFile(markerFile, NOFOLLOW_LINKS)) return false
        val lines = Files.readAllLines(markerFile)
        if (lines.firstOrNull() != HEADER) return false
        if (lines.filter { it.startsWith("jar ") } != jarLines) return false
        return lines.filter { it.startsWith("file ") }.all { line ->
            val fields = line.split(' ', limit = 4)
            fields.size == 4 && unchanged(dir, name = fields[3], size = fields[2], sha1 = fields[1])
        }
    }

    private fun unchanged(dir: Path, name: String, size: String, sha1: String): Boolean {
        val file = try {
            dir.resolve(name).normalize()
        } catch (_: InvalidPathException) {
            return false
        }
        // The marker vouches only for files inside its own directory.
        if (file == dir || !file.startsWith(dir)) return false
        return Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file).toString() == size && Sha1.of(file) == sha1
    }

    companion object {
        const val FILE_NAME = ".extracted"
        private const val HEADER = "blockwright-natives v2"
    }
}
