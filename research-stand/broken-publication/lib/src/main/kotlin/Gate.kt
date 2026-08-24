package dev.youndie.proba.sample.lib

import dev.youndie.proba.sample.support.Hint
import dev.youndie.proba.sample.support.Token

object Gate {
    /** Public, and its return type belongs to a module the api variant does not advertise. */
    fun issue(value: String): Token = Token(value)
}

/** Internal, and therefore public in the bytecode. Nothing a consumer can call mentions [Hint]. */
internal val hint: Hint = Hint("only from here")
