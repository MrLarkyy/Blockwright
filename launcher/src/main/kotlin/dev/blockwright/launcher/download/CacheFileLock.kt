package dev.blockwright.launcher.download

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.locks.ReentrantLock

/**
 * Exclusive access to one cache entry across threads and processes. A JVM may hold only one `FileLock` per file,
 * so threads first serialise on an in-process lock, then the holder takes the OS lock on `<target>.lock`. Lock
 * files stay on disk: deleting one would let two processes lock different inodes of the same name.
 */
object CacheFileLock {
    private class Entry {
        val lock = ReentrantLock()
        var users = 0
    }

    private val entries = HashMap<Path, Entry>()

    /** Appended to the name of a cache entry to name its lock file. */
    internal const val SUFFIX = ".lock"

    fun <T> withLock(target: Path, block: () -> T): T {
        val key = target.toAbsolutePath().normalize()
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        try {
            entry.lock.lockInterruptibly()
            try {
                Files.createDirectories(key.parent)
                val lockFile = key.resolveSibling("${key.fileName}$SUFFIX")
                FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
                    channel.lock().use { return block() }
                }
            } finally {
                entry.lock.unlock()
            }
        } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0) entries.remove(key)
            }
        }
    }
}
