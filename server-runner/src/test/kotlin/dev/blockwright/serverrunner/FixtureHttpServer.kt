package dev.blockwright.serverrunner

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** A local HTTP server on an ephemeral 127.0.0.1 port that counts the requests per path. */
class FixtureHttpServer : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val executor = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }

    val policy = UrlPolicy(setOf("127.0.0.1"), setOf("http"))
    val http = BoundedHttpClient(policy)

    init {
        server.executor = executor
        server.start()
    }

    fun uri(path: String): URI = URI("http://127.0.0.1:${server.address.port}$path")

    fun requests(path: String): Int = counts[path]?.get() ?: 0

    fun totalRequests(): Int = counts.values.sumOf { it.get() }

    fun route(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            counts.computeIfAbsent(exchange.requestURI.path) { AtomicInteger() }.incrementAndGet()
            exchange.use { runCatching { handler(it) } }
        }
    }

    fun bytes(path: String, body: ByteArray, status: Int = 200) = route(path) { respond(it, status, body) }

    fun text(path: String, body: String, status: Int = 200) = bytes(path, body.toByteArray(), status)

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        fun respond(exchange: HttpExchange, status: Int, body: ByteArray) {
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }
}
