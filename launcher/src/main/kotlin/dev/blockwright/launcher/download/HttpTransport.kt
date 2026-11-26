package dev.blockwright.launcher.download

import java.net.URI

/**
 * One HTTP GET. A transport follows redirects itself and checks every hop against the [DownloadPolicy] before
 * requesting it; the status of the final response, size limits, retries and verification belong to [Downloader].
 */
fun interface HttpTransport {
    /**
     * Requests [uri]; [rangeFrom] > 0 asks for the bytes from that offset on (`Range: bytes=<rangeFrom>-`).
     * Returns the response after redirects, whatever its status.
     */
    fun get(uri: URI, rangeFrom: Long): TransportResponse
}
