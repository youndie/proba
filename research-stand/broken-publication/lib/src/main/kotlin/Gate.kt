package dev.youndie.proba.sample.lib

import dev.youndie.proba.sample.support.Token

object Gate {
    /** Public, and its return type belongs to a module the api variant does not advertise. */
    fun issue(value: String): Token = Token(value)
}
