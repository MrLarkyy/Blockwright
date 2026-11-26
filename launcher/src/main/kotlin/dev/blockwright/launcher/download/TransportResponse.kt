package dev.blockwright.launcher.download

import java.io.Closeable
import java.io.InputStream
import java.net.URI

/** [finalUri] is the URI after redirects; closing releases the connection. */
class TransportResponse(
    val status: Int,
    val finalUri: URI,
    val contentRange: String?,
    val body: InputStream,
) : Closeable {
    override fun close() = body.close()
}
