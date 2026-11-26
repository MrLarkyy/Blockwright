package dev.blockwright.launcher

import dev.blockwright.launcher.download.HttpTransport
import dev.blockwright.launcher.download.TransportResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory HTTP: serves registered bodies, honours Range like a real server and records every request. */
class FakeTransport : HttpTransport {
    data class Call(val uri: URI, val rangeFrom: Long)

    val calls = CopyOnWriteArrayList<Call>()
    private val bodies = ConcurrentHashMap<URI, ByteArray>()

    @Volatile var offline = false

    @Volatile var honourRange = true

    @Volatile var latencyMillis = 0L

    /** Replaces the default behaviour for every request when set. */
    @Volatile var handler: ((URI, Long) -> TransportResponse)? = null

    fun put(uri: String, body: ByteArray): URI = URI(uri).also { bodies[it] = body }

    fun put(uri: URI, body: ByteArray): URI = uri.also { bodies[it] = body }

    fun remove(uri: URI) {
        bodies.remove(uri)
    }

    fun callsTo(uri: URI): Int = calls.count { it.uri == uri }

    override fun get(uri: URI, rangeFrom: Long): TransportResponse {
        calls += Call(uri, rangeFrom)
        if (latencyMillis > 0) Thread.sleep(latencyMillis)
        handler?.let { return it(uri, rangeFrom) }
        if (offline) throw IOException("offline")
        val body = bodies[uri] ?: return response(404, uri, ByteArray(0))
        if (rangeFrom > 0 && honourRange) {
            if (rangeFrom >= body.size) return response(416, uri, ByteArray(0))
            val rest = body.copyOfRange(rangeFrom.toInt(), body.size)
            return response(206, uri, rest, "bytes $rangeFrom-${body.size - 1}/${body.size}")
        }
        return response(200, uri, body)
    }

    companion object {
        fun response(status: Int, uri: URI, body: ByteArray, contentRange: String? = null) =
            TransportResponse(status, uri, contentRange, ByteArrayInputStream(body))
    }
}
