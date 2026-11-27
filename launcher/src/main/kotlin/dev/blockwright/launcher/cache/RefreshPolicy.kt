package dev.blockwright.launcher.cache

import java.time.Duration

/**
 * When a cached version JSON is checked against the online version manifest again. The check is best effort:
 * offline, or when the manifest or the new JSON cannot be fetched, the cached JSON keeps being used.
 */
class RefreshPolicy private constructor(val maxAge: Duration?) {
    /** Whether an entry last checked [age] ago is to be checked again. */
    fun isDue(age: Duration): Boolean = maxAge != null && age >= maxAge

    override fun toString(): String = maxAge?.let { "after($it)" } ?: "NEVER"

    companion object {
        /** The cached JSON is trusted forever. */
        val NEVER = RefreshPolicy(null)

        /** The manifest is consulted on every resolve. */
        val ALWAYS = RefreshPolicy(Duration.ZERO)

        val DEFAULT = after(Duration.ofHours(24))

        fun after(maxAge: Duration): RefreshPolicy {
            require(!maxAge.isNegative) { "maxAge must not be negative" }
            return RefreshPolicy(maxAge)
        }
    }
}
