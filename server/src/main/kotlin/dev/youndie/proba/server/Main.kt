package dev.youndie.proba.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(CIO, port = port, host = "0.0.0.0") { proba() }.start(wait = true)
}
