package dev.blockwright.launcher.download

import java.net.URI
import java.time.Duration

/**
 * Limits for every download. Only https URLs on [allowedHosts] are requested (`*.example.com` matches any
 * subdomain), also after redirects; a response that stays silent for [idleTimeout] is aborted; JSON without a
 * known size is capped at [jsonMaxBytes]. Cached files are verified on every resolve and reused without network.
 */
data class DownloadPolicy(
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val idleTimeout: Duration = Duration.ofSeconds(30),
    val attempts: Int = 3,
    val parallelism: Int = 8,
    val allowedHosts: List<String> = listOf("*.mojang.com", "*.minecraft.net"),
    val httpsOnly: Boolean = true,
    val jsonMaxBytes: Long = 16L * 1024 * 1024,
) {
    init {
        require(attempts >= 1) { "attempts must be at least 1" }
        require(parallelism >= 1) { "parallelism must be at least 1" }
    }

    fun permits(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && (httpsOnly || scheme != "http")) return false
        if (uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { hostMatches(it.lowercase(), host) }
    }

    private fun hostMatches(pattern: String, host: String): Boolean =
        if (pattern.startsWith("*.")) {
            host.endsWith(pattern.substring(1)) && host.length > pattern.length - 1
        } else {
            host == pattern
        }
}
