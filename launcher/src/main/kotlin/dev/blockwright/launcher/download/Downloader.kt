package dev.blockwright.launcher.download

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore

/**
 * Places verified files in the cache. Bytes land in `<target>.part` (resumed with a Range request after an
 * interruption) and reach the target only by an atomic move after size and SHA-1 match, so a cache file never
 * holds unverified bytes. One cache entry is transferred by one thread or process at a time.
 */
class Downloader(
    private val transport: HttpTransport,
    val policy: DownloadPolicy = DownloadPolicy(),
    private val sleeper: (Duration) -> Unit = { Thread.sleep(it) },
) {
    /** Returns [DownloadSpec.target]; a cached file that verifies is reused without any request. */
    fun ensure(spec: DownloadSpec): Path {
        checkPermitted(spec.uri)
        if (isCached(spec)) return spec.target
        return CacheFileLock.withLock(spec.lockFile) {
            if (isCached(spec)) spec.target else download(spec)
        }
    }

    /**
     * Ensures every spec with up to [DownloadPolicy.parallelism] transfers at once; the first failure cancels the
     * remaining transfers and is rethrown.
     */
    fun ensureAll(specs: List<DownloadSpec>) {
        if (specs.isEmpty()) return
        val permits = Semaphore(policy.parallelism)
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val completion = ExecutorCompletionService<Path>(executor)
            val futures = specs.map { spec ->
                completion.submit {
                    permits.acquire()
                    try {
                        ensure(spec)
                    } finally {
                        permits.release()
                    }
                }
            }
            awaitAll(completion, futures)
        }
    }

    private fun awaitAll(completion: ExecutorCompletionService<Path>, futures: List<Future<Path>>) {
        try {
            repeat(futures.size) { completion.take().get() }
        } catch (e: ExecutionException) {
            futures.forEach { it.cancel(true) }
            when (val cause = e.cause) {
                is IOException -> throw cause
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw DownloadException("download failed: ${cause?.message}", cause)
            }
        } catch (e: InterruptedException) {
            futures.forEach { it.cancel(true) }
            Thread.currentThread().interrupt()
            throw DownloadException("interrupted while downloading", e)
        }
    }

    private fun isCached(spec: DownloadSpec): Boolean =
        spec.sha1 != null && Sha1.matches(spec.target, spec.sha1, spec.size)

    private fun download(spec: DownloadSpec): Path {
        val part = spec.target.resolveSibling("${spec.target.fileName}.part")
        Files.createDirectories(spec.target.parent)
        if (spec.sha1 != null) Files.deleteIfExists(spec.target)
        var failure: DownloadException? = null
        repeat(policy.attempts) { attempt ->
            if (attempt > 0) backOff(spec, attempt)
            checkNotInterrupted(spec, null)
            // Without a hash nothing proves two responses are the same revision: never stitch them by resuming.
            if (spec.sha1 == null) Files.deleteIfExists(part)
            try {
                transfer(spec, part)
                val mismatch = mismatch(spec, part)
                if (mismatch == null) {
                    install(part, spec.target)
                    return spec.target
                }
                Files.deleteIfExists(part)
                failure = DownloadException(mismatch, retryable = true)
            } catch (e: DownloadException) {
                if (!e.retryable) throw e
                failure = e
            } catch (e: IOException) {
                checkNotInterrupted(spec, e)
                failure = DownloadException("transfer of ${spec.uri} failed: $e", e, retryable = true)
            }
        }
        throw DownloadException("giving up on ${spec.uri} for ${spec.target} after ${policy.attempts} attempts: ${failure?.message}", failure)
    }

    private fun backOff(spec: DownloadSpec, failedAttempts: Int) {
        val delay = policy.retryDelay(failedAttempts)
        if (delay.isZero) return
        try {
            sleeper(delay)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw DownloadException("interrupted while waiting to retry ${spec.uri}", e)
        }
    }

    private fun checkNotInterrupted(spec: DownloadSpec, cause: Throwable?) {
        if (Thread.currentThread().isInterrupted) throw DownloadException("interrupted while downloading ${spec.uri}", cause)
    }

    private fun transfer(spec: DownloadSpec, part: Path) {
        val cap = spec.size ?: spec.maxBytes
        var offset = if (Files.isRegularFile(part)) Files.size(part) else 0L
        if (offset > cap) {
            Files.delete(part)
            offset = 0
        }
        // A part already holding the expected size is only verified; a missing part (also for 0 bytes) is fetched.
        if (spec.size != null && offset == spec.size && Files.isRegularFile(part)) return
        transport.get(spec.uri, offset).use { response ->
            checkPermitted(response.finalUri)
            val append = acceptStatus(response, offset, part)
            val options = arrayOf(CREATE, WRITE, if (append) APPEND else TRUNCATE_EXISTING)
            val withinCap = Files.newOutputStream(part, *options).use { out ->
                copyCapped(response.body, out, if (append) offset else 0L, cap)
            }
            if (!withinCap) {
                Files.deleteIfExists(part)
                throw DownloadException("${spec.uri} is larger than the expected $cap bytes")
            }
        }
    }

    /** Returns whether the body continues the part file (true) or replaces it (false). */
    private fun acceptStatus(response: TransportResponse, offset: Long, part: Path): Boolean =
        when (val status = response.status) {
            HTTP_PARTIAL -> {
                if (offset == 0L || rangeStart(response.contentRange) != offset) {
                    Files.deleteIfExists(part)
                    throw DownloadException(
                        "unexpected Content-Range ${response.contentRange} from ${response.finalUri}",
                        retryable = true,
                    )
                }
                true
            }
            in HTTP_SUCCESS -> false
            HTTP_RANGE_NOT_SATISFIABLE -> {
                Files.deleteIfExists(part)
                throw DownloadException("range not satisfiable for ${response.finalUri}", retryable = true)
            }
            HTTP_TIMEOUT, HTTP_TOO_MANY, in HTTP_SERVER_ERROR ->
                throw DownloadException("HTTP $status from ${response.finalUri}", retryable = true)
            else -> throw DownloadException("HTTP $status from ${response.finalUri}")
        }

    private fun copyCapped(input: InputStream, out: OutputStream, start: Long, cap: Long): Boolean {
        val buffer = ByteArray(BUFFER)
        var total = start
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            if (total + read > cap) return false
            out.write(buffer, 0, read)
            total += read
        }
    }

    private fun mismatch(spec: DownloadSpec, part: Path): String? {
        val size = Files.size(part)
        if (spec.size != null && size != spec.size) {
            return "size mismatch for ${spec.uri}: expected ${spec.size} bytes, got $size"
        }
        val expected = spec.sha1 ?: return null
        val actual = Sha1.of(part)
        return if (actual.equals(expected, ignoreCase = true)) {
            null
        } else {
            "SHA-1 mismatch for ${spec.uri}: expected $expected, got $actual"
        }
    }

    private fun install(part: Path, target: Path) {
        try {
            Files.move(part, target, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(part, target, REPLACE_EXISTING)
        }
    }

    private fun checkPermitted(uri: URI) {
        if (!policy.permits(uri)) {
            val scheme = if (policy.httpsOnly) "https " else ""
            throw DownloadException("refusing $uri: only ${scheme}URLs on ${policy.allowedHosts} are allowed")
        }
    }

    private fun rangeStart(contentRange: String?): Long? =
        contentRange?.let { CONTENT_RANGE.matchEntire(it.trim()) }?.groupValues?.get(1)?.toLongOrNull()

    private companion object {
        const val BUFFER = 64 * 1024
        const val HTTP_PARTIAL = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HTTP_TIMEOUT = 408
        const val HTTP_TOO_MANY = 429
        val HTTP_SUCCESS = 200..299
        val HTTP_SERVER_ERROR = 500..599
        val CONTENT_RANGE = Regex("bytes (\\d+)-\\d+/(\\d+|\\*)")
    }
}
