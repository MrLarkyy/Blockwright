package dev.blockwright.launcher.download

import java.io.IOException

/**
 * A resolve or download failure. [retryable] marks transient failures (network, server error, hash mismatch of a
 * fresh transfer) that a new attempt may fix; policy violations and client errors are final.
 */
class DownloadException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : IOException(message, cause)
