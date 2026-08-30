package dev.youndie.proba.sample.support

/** A type a consumer must be able to name, because the library hands it out. */
data class Token(
    val value: String,
)

/**
 * A type the library mentions only from an `internal` member.
 *
 * Kotlin `internal` has no JVM counterpart and compiles to public, so a reader that trusts the class
 * file reports this as handed out by an API nobody can call — and then suggests widening every
 * consumer's compile classpath for it.
 */
data class Hint(
    val text: String,
)
