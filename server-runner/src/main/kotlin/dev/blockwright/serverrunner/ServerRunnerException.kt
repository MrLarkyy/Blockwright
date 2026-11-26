package dev.blockwright.serverrunner

/** Base of every failure the server runner reports. */
open class ServerRunnerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
