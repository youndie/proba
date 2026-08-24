package dev.youndie.proba.reader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * The fixtures are module metadata as published, downloaded and committed unchanged.
 *
 * Writing them by hand would test the reader against my idea of the format, which is the idea the
 * reader is already built on: both sides would agree and neither would be right. Real documents can
 * disagree with me, which is the only way a test here earns anything.
 */
object Fixtures {

    val Repository = MavenRepository("fixtures", "https://repo.example/maven2")

    fun load(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "no fixture $name" }
            .bufferedReader().readText()

    /** Serves the named fixtures by the tail of the URL; anything else answers 404. */
    fun serving(vararg fixtures: String): HttpClient {
        val byFileName = fixtures.associateBy { it }
        return HttpClient(
            MockEngine { request ->
                val file = request.url.encodedPath.substringAfterLast('/')
                val fixture = byFileName[file]
                if (fixture == null) {
                    respondError(HttpStatusCode.NotFound)
                } else {
                    respond(load(fixture), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                }
            },
        )
    }

    fun answering(handler: (path: String) -> Pair<HttpStatusCode, String>): HttpClient =
        HttpClient(
            MockEngine { request ->
                val (status, body) = handler(request.url.encodedPath)
                respond(body, status, headersOf("Content-Type", "application/json"))
            },
        )

    val KompotCore = Coordinate("io.github.youndie", "kompot-core", "0.27.0.46")

    val KompotCoreDocuments = arrayOf(
        "kompot-core-0.27.0.46.module",
        "kompot-core-jvm-0.27.0.46.module",
        "kompot-core-iosarm64-0.27.0.46.module",
        "kompot-core-iossimulatorarm64-0.27.0.46.module",
        "kompot-core-iosx64-0.27.0.46.module",
        "kompot-core-wasm-js-0.27.0.46.module",
    )
}
