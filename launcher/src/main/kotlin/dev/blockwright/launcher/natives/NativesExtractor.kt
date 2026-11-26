package dev.blockwright.launcher.natives

import dev.blockwright.launcher.download.CacheFileLock
import dev.blockwright.launcher.download.DownloadException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Extracts legacy natives jars into one directory. Work happens in a sibling staging directory that replaces the
 * target only when complete, so a failure leaves no partial directory. A marker records the source jars and every
 * extracted file; a rerun skips extraction when all recorded files are present and unchanged.
 *
 * The game writes its own libraries into the same directory at runtime and keeps them open while it runs. Files
 * the marker does not record are therefore left alone and a valid directory is never replaced: on Windows the
 * replacement fails while a client of that version runs, which must not stop another client from resolving.
 *
 * Check and replacement run under one lock, so no reader holds a file open while the directory is swapped and a
 * staging or backup directory found there can only be the leftover of a crashed run.
 */
class NativesExtractor(private val limits: ExtractionLimits = ExtractionLimits()) {
    /** Returns [target], which always exists afterwards (empty when there is nothing to extract). */
    fun extract(jars: List<NativeJar>, target: Path): Path = CacheFileLock.withLock(target) {
        val marker = NativesMarker(jars)
        removeLeftovers(target)
        if (!marker.matches(target)) replace(jars, target, marker)
        target
    }

    @OptIn(ExperimentalPathApi::class)
    private fun removeLeftovers(target: Path) {
        val prefixes = listOf("${target.fileName}$STAGING", "${target.fileName}$BACKUP")
        val leftovers = Files.list(target.parent).use { entries ->
            entries.filter { entry -> prefixes.any { entry.fileName.toString().startsWith(it) } }.toList()
        }
        leftovers.forEach { it.deleteRecursively() }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun replace(jars: List<NativeJar>, target: Path, marker: NativesMarker) {
        Files.createDirectories(target.parent)
        val staging = target.resolveSibling("${target.fileName}$STAGING${UUID.randomUUID()}")
        try {
            Files.createDirectory(staging)
            val budget = Budget()
            jars.forEach { unpack(it, staging, budget) }
            marker.write(staging)
            swap(staging, target)
        } finally {
            staging.deleteRecursively()
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun swap(staging: Path, target: Path) {
        val old = target.resolveSibling("${target.fileName}$BACKUP${UUID.randomUUID()}")
        val hadOld = Files.exists(target)
        if (hadOld) Files.move(target, old)
        try {
            moveDirectory(staging, target)
        } catch (e: IOException) {
            if (hadOld) runCatching { Files.move(old, target) }
            throw e
        }
        if (hadOld) old.deleteRecursively()
    }

    private fun moveDirectory(from: Path, to: Path) {
        try {
            Files.move(from, to, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to)
        }
    }

    private fun unpack(jar: NativeJar, staging: Path, budget: Budget) {
        ZipInputStream(Files.newInputStream(jar.path).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.countEntry(jar)
                val destination = destination(staging, entry.name, jar)
                if (entry.isDirectory || jar.excludes.any { entry.name.startsWith(it) }) continue
                Files.createDirectories(destination.parent)
                Files.newOutputStream(destination).use { copy(zip, it, budget, entry.name, jar) }
            }
        }
    }

    private fun destination(staging: Path, name: String, jar: NativeJar): Path {
        val trimmed = name.removeSuffix("/")
        val unsafe = trimmed.isEmpty() ||
            trimmed.startsWith("/") ||
            trimmed.any { it == '\\' || it == ':' || it.isISOControl() } ||
            trimmed.split('/').any { it.isEmpty() || it == "." || it == ".." } ||
            trimmed == NativesMarker.FILE_NAME
        val resolved = staging.resolve(trimmed).normalize()
        if (unsafe || !resolved.startsWith(staging) || resolved == staging) {
            throw DownloadException("unsafe entry name in ${jar.path}: ${trimmed.take(MAX_SHOWN)}")
        }
        return resolved
    }

    private fun copy(input: InputStream, out: OutputStream, budget: Budget, name: String, jar: NativeJar) {
        val buffer = ByteArray(BUFFER)
        var entryBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            entryBytes += read
            if (entryBytes > limits.maxEntryBytes) {
                throw DownloadException("$name in ${jar.path} exceeds ${limits.maxEntryBytes} bytes uncompressed")
            }
            budget.countBytes(read.toLong(), jar)
            out.write(buffer, 0, read)
        }
    }

    private inner class Budget {
        private var entries = 0
        private var bytes = 0L

        fun countEntry(jar: NativeJar) {
            entries++
            if (entries > limits.maxEntries) throw DownloadException("${jar.path}: more than ${limits.maxEntries} natives entries")
        }

        fun countBytes(count: Long, jar: NativeJar) {
            bytes += count
            if (bytes > limits.maxTotalBytes) {
                throw DownloadException("${jar.path}: natives exceed ${limits.maxTotalBytes} bytes uncompressed")
            }
        }
    }

    private companion object {
        const val STAGING = ".tmp-"
        const val BACKUP = ".old-"
        const val BUFFER = 64 * 1024
        const val MAX_SHOWN = 120
    }
}
