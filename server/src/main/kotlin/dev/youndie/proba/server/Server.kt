package dev.youndie.proba.server

import dev.youndie.proba.checks.CheckContext
import dev.youndie.proba.checks.Checks
import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.MavenRepository
import dev.youndie.proba.reader.Publication
import dev.youndie.proba.reader.PublicationReader
import dev.youndie.proba.reader.ReadOutcome
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.ktor.respondKompotComponent
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
// A top-level extension, so `+` on two modules needs it by name: without the import the operator
// falls back to string concatenation and the error talks about String rather than about serialisation.
import kotlinx.serialization.modules.plus

/**
 * The kompot Json for this server.
 *
 * Three modules: the core's own, the standard components' hand-registered actions, and the
 * registration KSP generated inside kompot-standard itself — which is why no processor runs here. A
 * consumer of stock components never applies KSP.
 */
private val probaSerializers: SerializersModule =
    kompotCoreSerializersModule + kompotStandardSerializersModule + generatedStandardSerializersModule

val kompotJson: Json = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    serializersModule = probaSerializers
}

fun Application.proba(http: HttpClient = HttpClient(CIO)) {
    // The report page is public and rendered by a separate origin, so a browser has to be allowed to
    // ask for it. Read-only and GET-only: nothing here takes a request that changes anything.
    install(CORS) { anyHost() }

    val reader = PublicationReader(http)

    routing {
        get("/health") { call.respondText("ok") }

        // A permanent address: everything the report is about is in the path, so the link somebody
        // pastes tomorrow asks the same question it asked today.
        get("/report/{group}/{artifact}/{version}") {
            val coordinate = Coordinate(
                group = call.parameters["group"].orEmpty(),
                artifact = call.parameters["artifact"].orEmpty(),
                version = call.parameters["version"].orEmpty(),
            )
            val repository = call.request.queryParameters["repo"]
                ?.let { MavenRepository(it, it) }
                ?: MavenRepository.MavenCentral

            when (val outcome = reader.read(coordinate, repository)) {
                is ReadOutcome.Read -> {
                    val findings = Checks.runAll(context(outcome.publication, reader, repository))
                    call.respondKompotComponent(kompotJson, ReportScreen.of(coordinate, findings, deep = false))
                }

                is ReadOutcome.NotFound -> call.respondKompotComponent(
                    kompotJson,
                    ReportScreen.refusal(
                        coordinate,
                        "Nothing is published at this coordinate.",
                        outcome.tried.map { "${it.url} → ${if (it.status == 0) "no answer" else it.status.toString()}" },
                    ),
                )

                is ReadOutcome.WithoutModuleMetadata -> call.respondKompotComponent(
                    kompotJson,
                    ReportScreen.refusal(
                        coordinate,
                        "Published, but without Gradle module metadata: what variants exist is not knowable from the repository alone.",
                        listOf(outcome.pomUrl),
                    ),
                )

                is ReadOutcome.Unreadable -> call.respondKompotComponent(
                    kompotJson,
                    ReportScreen.refusal(coordinate, "The module metadata could not be read.", listOf(outcome.url, outcome.reason)),
                )
            }
        }

        get("{...}") {
            call.respondText("no route for ${call.request.uri}", status = HttpStatusCode.NotFound)
        }
    }
}

private fun context(
    publication: Publication,
    reader: PublicationReader,
    repository: MavenRepository,
): CheckContext {
    val cache = mutableMapOf<Coordinate, Publication?>()
    return CheckContext(
        publication = publication,
        lookup = { wanted -> cache.getOrPut(wanted) { (reader.read(wanted, repository) as? ReadOutcome.Read)?.publication } },
    )
}
