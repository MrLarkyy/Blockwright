package dev.blockwright.launcher.download

import java.net.URI
import java.time.Duration

/**
 * Limits for every download. Only https URLs on [allowedHosts] are requested (`*.example.com` matches any
 * subdomain); redirects are followed hop by hop and a hop to any other URL is refused before it is requested.
 * A response that stays silent for [idleTimeout] is aborted; JSON without a known size is capped at
 * [jsonMaxBytes]. A failed attempt is retried after [retryDelay] doubling per failure up to [maxRetryDelay].
 * Cached files are verified on every resolve and reused without network.
 */
data class DownloadPolicy(
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val idleTimeout: Duration = Duration.ofSeconds(30),
    val attempts: Int = 3,
    val parallelism: Int = 8,
    val allowedHosts: List<String> = DEFAULT_HOSTS,
    val httpsOnly: Boolean = true,
    val jsonMaxBytes: Long = 16L * 1024 * 1024,
    val retryDelay: Duration = Duration.ofMillis(500),
    val maxRetryDelay: Duration = Duration.ofSeconds(8),
    val maxRedirects: Int = 5,
) {
    init {
        require(attempts >= 1) { "attempts must be at least 1" }
        require(parallelism >= 1) { "parallelism must be at least 1" }
        require(!retryDelay.isNegative && maxRetryDelay >= retryDelay) { "retry delays must satisfy 0 <= retryDelay <= maxRetryDelay" }
        require(maxRedirects >= 0) { "maxRedirects must not be negative" }
    }

    fun permits(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && (httpsOnly || scheme != "http")) return false
        if (uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { hostMatches(it.lowercase(), host) }
    }

    /** The wait before the next attempt after [failedAttempts] failures (1 = after the first failure). */
    fun retryDelay(failedAttempts: Int): Duration {
        require(failedAttempts >= 1) { "failedAttempts must be at least 1" }
        val doublings = minOf(failedAttempts - 1, MAX_DOUBLINGS)
        val delay = retryDelay.multipliedBy(1L shl doublings)
        return if (delay > maxRetryDelay) maxRetryDelay else delay
    }

    private fun hostMatches(pattern: String, host: String): Boolean =
        if (pattern.startsWith("*.")) {
            host.endsWith(pattern.substring(1)) && host.length > pattern.length - 1
        } else {
            host == pattern
        }

    companion object {
        private const val MAX_DOUBLINGS = 20

        /** Mojang for the client, the Fabric maven and Maven Central for the pinned Fabric loader runtime. */
        val DEFAULT_HOSTS = listOf("*.mojang.com", "*.minecraft.net", "maven.fabricmc.net", "repo.maven.apache.org")
    }
}
