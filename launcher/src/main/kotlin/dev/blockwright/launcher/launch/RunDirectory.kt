package dev.blockwright.launcher.launch

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.math.abs

/**
 * Throwaway client directories; removal retries because Windows releases file handles of a killed JVM late.
 * Each directory records its owner (the launching JVM) and, once started, the client process in [OWNERS_FILE], so a
 * later launch can remove the directory of an owner that was killed without hooks ([sweep]).
 */
object RunDirectory {
    const val PREFIX = "blockwright-client-"
    const val OWNERS_FILE = "blockwright-owners"
    private const val OWNER = "owner"
    private const val CLIENT = "client"
    private const val KEPT = "kept"
    private const val START_TOLERANCE_MILLIS = 1000L

    fun create(): Path = Files.createTempDirectory(PREFIX).also { record(it, OWNER, ProcessHandle.current()) }

    fun recordClient(dir: Path, client: ProcessHandle) = record(dir, CLIENT, client)

    /** Marks a directory the user asked to keep, so [sweep] never removes it. */
    fun markKept(dir: Path) = record(dir, KEPT, ProcessHandle.current())

    @OptIn(ExperimentalPathApi::class)
    fun delete(dir: Path, attempts: Int = 5, pauseMillis: Long = 200): Boolean {
        repeat(attempts) { attempt ->
            try {
                dir.deleteRecursively()
                return true
            } catch (_: IOException) {
                if (attempt < attempts - 1) Thread.sleep(pauseMillis)
            }
        }
        return !Files.exists(dir)
    }

    /**
     * Removes the run directories in [root] whose recorded owner and client are both gone; returns the removed ones.
     * A directory without a readable record (still being created, or not ours) is left alone, and so is a symbolic
     * link. A single attempt each: a directory still locked is tried again at the next launch.
     */
    fun sweep(root: Path = Path.of(System.getProperty("java.io.tmpdir"))): List<Path> {
        val candidates = try {
            root.listDirectoryEntries("$PREFIX*")
        } catch (_: IOException) {
            return emptyList()
        }
        return candidates.filter { dir ->
            Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) && isAbandoned(dir) && delete(dir, attempts = 1)
        }
    }

    private fun isAbandoned(dir: Path): Boolean {
        val records = try {
            Files.readAllLines(dir.resolve(OWNERS_FILE)).mapNotNull(::parse).toMap()
        } catch (_: IOException) {
            return false
        }
        if (KEPT in records) return false
        val owner = records[OWNER] ?: return false
        return !isAlive(owner) && records[CLIENT]?.let(::isAlive) != true
    }

    private fun record(dir: Path, role: String, process: ProcessHandle) {
        val start = process.info().startInstant().map { it.toEpochMilli().toString() }.orElse("-")
        Files.writeString(
            dir.resolve(OWNERS_FILE),
            "$role ${process.pid()} $start\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    private fun parse(line: String): Pair<String, Recorded>? {
        val parts = line.trim().split(' ')
        if (parts.size != 3) return null
        val pid = parts[1].toLongOrNull() ?: return null
        return parts[0] to Recorded(pid, parts[2].toLongOrNull())
    }

    /**
     * A reused pid with another start time is a different process: the recorded one is gone. The tolerance keeps a
     * live owner alive if two JVMs round its start time differently.
     */
    private fun isAlive(recorded: Recorded): Boolean {
        val handle = ProcessHandle.of(recorded.pid).orElse(null) ?: return false
        if (!handle.isAlive) return false
        val start = recorded.startMillis ?: return true
        return handle.info().startInstant().map { abs(it.toEpochMilli() - start) < START_TOLERANCE_MILLIS }.orElse(true)
    }

    private class Recorded(val pid: Long, val startMillis: Long?)
}
