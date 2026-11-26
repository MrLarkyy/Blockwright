package dev.blockwright.serverrunner

import java.net.URI

/** Which URLs the runner may contact; server-provided URLs are untrusted until they pass [check]. */
data class UrlPolicy(val hosts: Set<String>, val schemes: Set<String> = setOf("https")) {
    fun check(uri: URI): URI {
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme == null || scheme !in schemes || host == null || host !in hosts || uri.rawUserInfo != null) {
            throw PaperDownloadException("refusing URL outside the allowed hosts ${hosts.sorted()} over ${schemes.sorted()}: $uri")
        }
        return uri
    }

    companion object {
        val FILL = UrlPolicy(setOf("fill.papermc.io", "fill-data.papermc.io"))
    }
}
