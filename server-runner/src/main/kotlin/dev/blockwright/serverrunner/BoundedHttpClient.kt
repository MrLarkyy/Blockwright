package dev.blockwright.serverrunner

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * GET-only HTTP client for untrusted endpoints: every URL (redirect targets included) must pass the [policy],
 * bodies are capped and every transfer has a deadline. HttpURLConnection is used because it has a per-read
 * timeout; java.net.http stops timing once the headers arrived.
 */
class BoundedHttpClient(
    val policy: UrlPolicy,
    private val connectTimeout: Duration = 10.seconds,
    private val readTimeout: Duration = 30.seconds,
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    /** The status and body of a GET; non-2xx statuses are returned, the caller decides what they mean. */
    data class TextResponse(val status: Int, val body: String)

    fun getText(uri: URI, maxBytes: Long, timeout: Duration): TextResponse {
        val deadline = Deadline(timeout)
        val connection = open(uri, deadline)
        try {
            val status = connection.responseCode
            val stream = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?: return TextResponse(status, "")
            val buffer = ByteArrayOutputStream()
            stream.use { copyBounded(it, buffer, maxBytes, connection.contentLengthLong, deadline, uri) }
            return TextResponse(status, buffer.toString(Charsets.UTF_8))
        } catch (e: SocketTimeoutException) {
            throw timedOut(uri, timeout, e)
        } catch (e: IOException) {
            throw PaperDownloadException("GET $uri failed: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    /** Streams a 200 body into [sink]; anything longer than [maxBytes] is aborted. Returns the bytes copied. */
    fun download(uri: URI, maxBytes: Long, timeout: Duration, sink: OutputStream): Long {
        val deadline = Deadline(timeout)
        val connection = open(uri, deadline)
        try {
            val status = connection.responseCode
            if (status != 200) {
                val echo = connection.errorStream?.use { it.readNBytes(ECHO_CHARS).toString(Charsets.UTF_8) }.orEmpty()
                throw PaperDownloadException("GET $uri answered $status: ${echo.take(ECHO_CHARS)}")
            }
            return connection.inputStream.use { copyBounded(it, sink, maxBytes, connection.contentLengthLong, deadline, uri) }
        } catch (e: SocketTimeoutException) {
            throw timedOut(uri, timeout, e)
        } catch (e: IOException) {
            throw PaperDownloadException("GET $uri failed: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(start: URI, deadline: Deadline): HttpURLConnection {
        var uri = policy.check(start)
        repeat(MAX_REDIRECTS + 1) {
            val connection = connect(uri, deadline)
            val status = try {
                connection.responseCode
            } catch (e: SocketTimeoutException) {
                connection.disconnect()
                throw PaperDownloadException("GET $uri timed out", e)
            } catch (e: IOException) {
                connection.disconnect()
                throw PaperDownloadException("GET $uri failed: ${e.message}", e)
            }
            if (status !in REDIRECTS) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location == null) throw PaperDownloadException("GET $uri answered $status without a Location")
            uri = policy.check(uri.resolve(location))
        }
        throw PaperDownloadException("GET $start redirected more than $MAX_REDIRECTS times")
    }

    private fun connect(uri: URI, deadline: Deadline): HttpURLConnection {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = connectTimeout.inWholeMilliseconds.toInt()
        connection.readTimeout = minOf(readTimeout.inWholeMilliseconds, deadline.remainingMillis()).coerceAtLeast(1).toInt()
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Encoding", "identity")
        return connection
    }

    private fun copyBounded(input: InputStream, sink: OutputStream, maxBytes: Long, declared: Long, deadline: Deadline, uri: URI): Long {
        if (declared > maxBytes) throw tooLarge(uri, maxBytes)
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            if (deadline.expired()) throw PaperDownloadException("GET $uri did not finish within ${deadline.timeout}")
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > maxBytes) throw tooLarge(uri, maxBytes)
            sink.write(buffer, 0, read)
        }
    }

    private fun tooLarge(uri: URI, maxBytes: Long) = PaperDownloadException("GET $uri: body exceeds the limit of $maxBytes bytes")

    private fun timedOut(uri: URI, timeout: Duration, cause: Throwable) =
        PaperDownloadException("GET $uri timed out (limit $timeout)", cause)

    private class Deadline(val timeout: Duration) {
        private val endNanos = System.nanoTime() + timeout.inWholeNanoseconds

        fun remainingMillis(): Long = (endNanos - System.nanoTime()) / 1_000_000

        fun expired(): Boolean = System.nanoTime() - endNanos > 0
    }

    companion object {
        private const val MAX_REDIRECTS = 3
        private const val ECHO_CHARS = 200
        private const val BUFFER_BYTES = 64 * 1024
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val DEFAULT_USER_AGENT = "blockwright-server-runner/${BoundedHttpClient::class.java.`package`?.implementationVersion ?: "dev"}"
    }
}
