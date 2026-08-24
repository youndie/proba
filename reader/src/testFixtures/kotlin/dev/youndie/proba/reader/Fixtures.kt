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

    fun load(name: String): String = String(bytes(name))

    /** Some fixtures are artefacts rather than documents: a jar says things no metadata has to repeat. */
    fun bytes(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "no fixture $name" }.use { it.readBytes() }

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

/**
 * Serves one recorded document whatever is asked for.
 *
 * This is how a publication is put somewhere it does not belong without editing it: the document
 * stays exactly as it was published, and only the path it answers on is ours. A hand-edited fixture
 * would drift into meaning something else the moment the real one changed, and nothing would say so.
 */
fun Fixtures.servingAnything(fixture: String): HttpClient =
    HttpClient(
        MockEngine {
            respond(load(fixture), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        },
    )
