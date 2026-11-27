package dev.blockwright.launcher.launch

/**
 * The client started but did not reach the awaited state: it reported a failed join, exited, or timed out. The
 * message carries the client's reason or its last console lines; the process is already stopped.
 */
class ClientStartException(message: String, val result: ClientRunResult? = null) : RuntimeException(message)
