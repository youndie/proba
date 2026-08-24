package dev.youndie.proba.server

import dev.youndie.proba.checks.CheckContext
import dev.youndie.proba.checks.Checks
import dev.youndie.proba.checks.httpArtefacts
import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.MavenRepository
import dev.youndie.proba.reader.Publication
import dev.youndie.proba.reader.PublicationReader
import dev.youndie.proba.reader.HttpFetcher
import dev.youndie.proba.reader.ReadOutcome
import dev.youndie.proba.reader.RepositoryIndex
import dev.youndie.proba.reader.RoutingFetcher
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
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.forms.KompotFormResponse
import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondTextWriter
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
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
    val broadcaster = KompotUpdateBroadcaster()
    // Started here and not in the constructor: without it every broadcast reaches the bus and nothing
    // hands it out, and the toolkit fails loudly on that rather than delivering silence.
    broadcaster.start(this)
    val sweeps = SweepRunner(reader, broadcaster, kompotJson)

    routing {
        get("/health") { call.respondText("ok") }

        // Every module of one group, filled in as each is read. The screen is a form response because
        // that is the only shape on the wire that can carry a topic — see the empty schema below.
        get("/sweep/{group}") {
            val group = call.parameters["group"].orEmpty()
            val repository = call.request.queryParameters["repo"]
                ?.let { MavenRepository(it, it) }
                ?: MavenRepository.MavenCentral

            val sweep = sweeps.start(
                scope = this@proba,
                group = group,
                repository = repository,
                index = RepositoryIndex.of(repository, RoutingFetcher(HttpFetcher(http))),
                fresh = call.request.queryParameters["fresh"] == "1",
            )
            if (sweep == null) {
                call.respondText(
                    "this repository publishes no index, so its modules cannot be enumerated from here",
                    status = HttpStatusCode.NotImplemented,
                )
                return@get
            }
            call.respondSweep(sweep)
        }

        get("/sweep/{group}/state") {
            val sweep = sweeps.get(call.parameters["group"].orEmpty())
            if (sweep == null) {
                call.respondText("no such sweep", status = HttpStatusCode.NotFound)
                return@get
            }
            val results = sweep.snapshot()
            call.respondText(
                kompotJson.encodeToString(
                    SweepState.serializer(),
                    SweepState(
                        modules = sweep.modules.size,
                        read = results.values.count { it !is ModuleResult.Pending },
                        // The two numbers that make an idle channel a fact rather than a silence.
                        subscribers = broadcaster.localSubscriberCount(sweep.topic),
                        framesDelivered = sweep.framesDelivered,
                        topic = sweep.topic,
                    ),
                ),
                ContentType.Application.Json,
            )
        }

        // The transport. The protocol does not fix one (SPEC 10.1); this is the browser's native.
        get("/updates/{topic}") {
            val topic = call.parameters["topic"].orEmpty()
            val channel = Channel<String>(Channel.BUFFERED)
            // Subscribing and unsubscribing both live INSIDE the writer, and that is not a style
            // choice: respondTextWriter returns as soon as the response is set up, and its body runs
            // afterwards in a coroutine of its own. Cleanup in a finally around the call therefore
            // closes the channel immediately, the loop below finds it closed, and the stream opens and
            // ends at once — which reads exactly like a broken transport rather than like a mistake
            // three lines up.
            call.respondTextWriter(ContentType.Text.EventStream) {
                broadcaster.subscribe(topic, channel)
                try {
                    // Written after the subscription, so a client waiting for it knows the difference
                    // between "subscribed" and "connected to something that will never speak".
                    write("event: open\ndata: $topic\n\n")
                    flush()
                    for (payload in channel) {
                        write("data: $payload\n\n")
                        flush()
                    }
                } finally {
                    broadcaster.unsubscribe(topic, channel)
                    channel.close()
                }
            }
        }

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
                    val findings = Checks.runAll(context(outcome.publication, reader, repository, http))
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

                is ReadOutcome.UnsupportedLayout -> call.respondKompotComponent(
                    kompotJson,
                    ReportScreen.refusal(coordinate, outcome.reason, listOf("no check was run")),
                )

                is ReadOutcome.Unreadable -> call.respondKompotComponent(
                    kompotJson,
                    ReportScreen.refusal(coordinate, "The module metadata could not be read.", listOf(outcome.url, outcome.reason)),
                )
            }
        }

        // A badge for a README. Cached hard: it is fetched by every reader of every page it sits on,
        // and a publication that already went out does not change.
        get("/badge/{group}/{artifact}/{version}") {
            val coordinate = Coordinate(
                group = call.parameters["group"].orEmpty(),
                artifact = call.parameters["artifact"].orEmpty(),
                version = call.parameters["version"].orEmpty().removeSuffix(".svg"),
            )
            val repository = call.request.queryParameters["repo"]
                ?.let { MavenRepository(it, it) }
                ?: MavenRepository.MavenCentral

            val svg = when (val outcome = reader.read(coordinate, repository)) {
                is ReadOutcome.Read -> Badge.of(Checks.runAll(context(outcome.publication, reader, repository, http)))
                is ReadOutcome.NotFound -> Badge.refusal("not published")
                is ReadOutcome.WithoutModuleMetadata -> Badge.refusal("no module metadata")
                is ReadOutcome.UnsupportedLayout -> Badge.refusal("snapshot")
                is ReadOutcome.Unreadable -> Badge.refusal("unreadable")
            }
            call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respondText(svg, ContentType.Image.SVG)
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
    http: HttpClient,
): CheckContext {
    val cache = mutableMapOf<Coordinate, Publication?>()
    return CheckContext(
        publication = publication,
        lookup = { wanted -> cache.getOrPut(wanted) { (reader.read(wanted, repository) as? ReadOutcome.Read)?.publication } },
        artefacts = httpArtefacts(http),
    )
}


@Serializable
private data class SweepState(
    val modules: Int,
    val read: Int,
    val subscribers: Int,
    val framesDelivered: Int,
    val topic: String,
)

/**
 * The sweep screen goes out as a form response with an empty schema.
 *
 * Not because it is a form. `KompotFormResponse.realtimeTopic` is the only field in the whole
 * protocol that can name an update channel, and a screen that is not a form has nowhere else to put
 * it — so a form with no fields is invented to carry one string.
 */
private suspend fun ApplicationCall.respondSweep(sweep: Sweep) {
    val response = KompotFormResponse(
        schema = FormSchema(formId = sweep.id, fields = emptyList()),
        screen = SweepScreen.of(sweep, sweep.snapshot()),
        realtimeTopic = sweep.topic,
    )
    respondText(kompotJson.encodeToString(KompotFormResponse.serializer(), response), ContentType.Application.Json)
}
