package dev.blockwright.launcher.download

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * [HttpTransport] on `java.net.http`: HTTP/2 when offered, redirects followed except https to http, a fixed
 * user agent without contact details, and an idle timeout on headers and body.
 */
class JdkHttpTransport(
    connectTimeout: Duration,
    private val idleTimeout: Duration,
) : HttpTransport, AutoCloseable {
    constructor(policy: DownloadPolicy) : this(policy.connectTimeout, policy.idleTimeout)

    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(connectTimeout)
        .build()

    override fun get(uri: URI, rangeFrom: Long): TransportResponse {
        val request = HttpRequest.newBuilder(uri)
            .timeout(idleTimeout)
            .header("User-Agent", USER_AGENT)
            .apply { if (rangeFrom > 0) header("Range", "bytes=$rangeFrom-") }
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted while requesting $uri", e)
        }
        return TransportResponse(
            status = response.statusCode(),
            finalUri = response.uri(),
            contentRange = response.headers().firstValue("Content-Range").orElse(null),
            body = IdleTimeoutInputStream(response.body(), idleTimeout),
        )
    }

    override fun close() = client.close()

    private companion object {
        val USER_AGENT = "blockwright/" + (JdkHttpTransport::class.java.`package`?.implementationVersion ?: "dev")
    }
}
