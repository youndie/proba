package dev.youndie.proba.server

import dev.youndie.proba.reader.Fixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val GROUP = "io.github.youndie"
private const val REPO = "https://repo.example/snapshots"

/** A repository that publishes two modules, one of which advertises a version it never published. */
private fun repositoryWithIndex(): HttpClient = HttpClient(
    MockEngine { request ->
        val path = request.url.encodedPath
        val json = headersOf("Content-Type", "application/json")
        when {
            path.endsWith("/api/maven/details/snapshots/io/github/youndie") ->
                respond("""{"files":[{"name":"kompot-core","type":"DIRECTORY"},{"name":"ghost","type":"DIRECTORY"}]}""", HttpStatusCode.OK, json)

            path.endsWith("/kompot-core/maven-metadata.xml") ->
                respond("<metadata><versioning><versions><version>0.27.0.46</version></versions></versioning></metadata>", HttpStatusCode.OK)

            path.endsWith("/ghost/maven-metadata.xml") ->
                respond("<metadata><versioning><versions><version>9.9.9</version></versions></versioning></metadata>", HttpStatusCode.OK)

            else -> {
                val file = path.substringAfterLast('/')
                if (file in Fixtures.KompotCoreDocuments) respond(Fixtures.load(file), HttpStatusCode.OK, json)
                else respondError(HttpStatusCode.NotFound)
            }
        }
    },
)

class SweepTest {

    private suspend fun HttpClient.stateOf(id: String) =
        Json.parseToJsonElement(get("/sweep/$id/state").bodyAsText()).jsonObject

    private suspend fun HttpClient.awaitRead(id: String, modules: Int) = withTimeout(20_000) {
        while (stateOf(id)["read"]!!.jsonPrimitive.int() != modules) { /* the run is a background job */ }
        stateOf(id)
    }

    @Test
    fun `the sweep names its update channel, which costs it an empty form`() = testApplication {
        application { proba(repositoryWithIndex()) }

        val body = client.get("/sweep/$GROUP?repo=$REPO").bodyAsText()

        val response = Json.parseToJsonElement(body).jsonObject
        assertEquals("sweep:${GROUP.replace('.', '_')}", response["realtimeTopic"]?.jsonPrimitive?.content)
        // The empty schema is the price of the topic: nothing else on the wire can carry one.
        assertTrue(response["schema"]!!.jsonObject["fields"].toString() == "[]", "the invented form has fields")
        assertEquals("column", response["screen"]!!.jsonObject["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a channel nobody listens to is a number, not a silence`() = testApplication {
        application { proba(repositoryWithIndex()) }

        client.get("/sweep/$GROUP?repo=$REPO")
        val id = GROUP.replace('.', '_')
        val state = client.awaitRead(id, modules = 2)

        // Every module was read and every frame was published — and none of it reached anybody. Told
        // apart by data: without these two numbers the run looks exactly like one that worked.
        assertEquals(2, state["read"]!!.jsonPrimitive.int())
        assertEquals(0, state["subscribers"]!!.jsonPrimitive.int())
        assertEquals(0, state["framesDelivered"]!!.jsonPrimitive.int(), "nothing was listening, so nothing was delivered")
    }

    @Test
    fun `with a subscriber the frames are delivered and the screen changes`() = testApplication {
        application { proba(repositoryWithIndex()) }
        val id = GROUP.replace('.', '_')

        val frames = mutableListOf<String>()
        // Two clients on purpose: the streaming request and the one that starts the run must not share
        // a connection, or starting the run cuts the stream it was supposed to feed.
        val listener = createClient { }
        val trigger = createClient { }
        listener.prepareGet("/updates/sweep:$id").execute { response ->
            val channel = response.bodyAsChannel()
            // The stream opens with a frame of its own, so "subscribed" is distinguishable from
            // "connected to something that will never speak".
            withTimeout(20_000) {
                while (channel.readUTF8Line()?.startsWith("event: open") != true) Unit
            }

            trigger.get("/sweep/$GROUP?repo=$REPO")

            withTimeout(30_000) {
                while (frames.size < 2) {
                    val line = channel.readUTF8Line() ?: break
                    // The open frame carries the topic, not a component: it is the handshake, and
                    // collecting it as an update would make the very first assertion below vacuous.
                    val payload = line.removePrefix("data: ")
                    if (line.startsWith("data: ") && payload.startsWith("{")) frames += payload
                }
            }
        }

        assertTrue(frames.size >= 2, "expected frames, got ${frames.size}")
        val first = Json.parseToJsonElement(frames.first()).jsonObject
        assertTrue(first.containsKey("componentId"), "a frame names the component it replaces")
        assertTrue(first.containsKey("component"))
        assertTrue(frames.any { it.contains("of 2 read") }, "the status line is one of the things that changes")

        val state = trigger.awaitRead(id, modules = 2)
        assertTrue(state["framesDelivered"]!!.jsonPrimitive.int() > 0, "delivered nothing while subscribed")
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.int(): Int = content.toInt()
