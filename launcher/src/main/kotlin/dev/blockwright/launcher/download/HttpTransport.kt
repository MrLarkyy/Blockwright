package dev.blockwright.launcher.download

import java.net.URI

/** Raw HTTP GET; status handling, limits and verification belong to [Downloader]. */
fun interface HttpTransport {
    /** Requests [uri]; [rangeFrom] > 0 asks for the bytes from that offset on (`Range: bytes=<rangeFrom>-`). */
    fun get(uri: URI, rangeFrom: Long): TransportResponse
}
