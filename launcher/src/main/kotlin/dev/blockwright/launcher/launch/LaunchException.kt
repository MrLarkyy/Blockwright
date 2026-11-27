package dev.blockwright.launcher.launch

/** A launch refused before any process starts: invalid configuration, version, agent jar or JVM. */
class LaunchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
