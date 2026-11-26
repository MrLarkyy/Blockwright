package dev.blockwright.launcher.download

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * [HttpTransport] on `java.net.http`: HTTP/2 when offered, a fixed user agent without contact details and an
 * idle timeout on headers and body. Redirects are followed here, one hop at a time, so every hop is checked
 * against the [DownloadPolicy] before it is requested.
 */
class JdkHttpTransport(private val policy: DownloadPolicy) : HttpTransport, AutoCloseable {
    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(policy.connectTimeout)
        .build()

    override fun get(uri: URI, rangeFrom: Long): TransportResponse {
        var current = uri
        repeat(policy.maxRedirects + 1) {
            checkPermitted(current, uri)
            val response = send(current, rangeFrom)
            val location = response.headers().firstValue("Location").orElse(null)
            if (response.statusCode() !in REDIRECTS || location == null) {
                return TransportResponse(
                    status = response.statusCode(),
                    finalUri = current,
                    contentRange = response.headers().firstValue("Content-Range").orElse(null),
                    body = IdleTimeoutInputStream(response.body(), policy.idleTimeout),
                )
            }
            response.body().close()
            current = try {
                current.resolve(location)
            } catch (e: IllegalArgumentException) {
                throw DownloadException("invalid redirect from $current", e)
            }
        }
        throw DownloadException("more than ${policy.maxRedirects} redirects for $uri")
    }

    private fun send(uri: URI, rangeFrom: Long): HttpResponse<InputStream> {
        val request = HttpRequest.newBuilder(uri)
            .timeout(policy.idleTimeout)
            .header("User-Agent", USER_AGENT)
            .apply { if (rangeFrom > 0) header("Range", "bytes=$rangeFrom-") }
            .GET()
            .build()
        return try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted while requesting $uri", e)
        }
    }

    private fun checkPermitted(hop: URI, original: URI) {
        if (!policy.permits(hop)) {
            val what = if (hop == original) "URL" else "redirect from $original to"
            throw DownloadException("refusing $what $hop: not an allowed host or scheme")
        }
    }

    override fun close() = client.close()

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val USER_AGENT = "blockwright/" + (JdkHttpTransport::class.java.`package`?.implementationVersion ?: "dev")
    }
}
