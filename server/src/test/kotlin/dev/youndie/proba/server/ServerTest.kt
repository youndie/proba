package dev.youndie.proba.server

import dev.youndie.proba.reader.Fixtures
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    private fun withFixtures(vararg documents: String, block: suspend (io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { proba(Fixtures.serving(*documents)) }
            block(client)
        }

    @Test
    fun `the root of the screen carries its type`() = withFixtures(*Fixtures.KompotCoreDocuments) { client ->
        // The reason kompot-ktor has a helper at all: a plain respond() serialises the root through
        // its concrete class and drops the discriminator, so the client receives a screen it cannot
        // identify while every child of it is fine. A test on the children would not notice.
        val body = client.get("/report/io.github.youndie/kompot-core/0.27.0.46?repo=https://repo.example/maven2").bodyAsText()

        val root = Json.parseToJsonElement(body).jsonObject
        assertEquals("column", root["type"]?.jsonPrimitive?.content)
        assertEquals("report", root["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the screen names only tokens, never a colour`() = withFixtures(*Fixtures.KompotCoreDocuments) { client ->
        val body = client.get("/report/io.github.youndie/kompot-core/0.27.0.46?repo=https://repo.example/maven2").bodyAsText()

        // A hex colour anywhere in the response would mean the server had decided something the
        // client is supposed to decide, and the design could no longer be changed without a release.
        assertTrue(Regex("#[0-9a-fA-F]{6}").find(body) == null, "the server sent a literal colour")
        assertTrue(body.contains("severity_defect") || body.contains("surface"), "no token names at all")
    }

    @Test
    fun `a coordinate that is not published gets a screen saying so`() = withFixtures { client ->
        // Not a 404 with an empty body: the reading side draws screens, and an empty screen is
        // indistinguishable from a healthy publication with nothing to report.
        val response = client.get("/report/io.example/absent/1.0.0?repo=https://repo.example/maven2")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Nothing is published at this coordinate"), body.take(200))
        assertTrue(body.contains("absent-1.0.0.module"), "the refusal has to say what was looked for")
    }

    @Test
    fun `the report is at a permanent address`() = withFixtures(*Fixtures.KompotCoreDocuments) { client ->
        val path = "/report/io.github.youndie/kompot-core/0.27.0.46?repo=https://repo.example/maven2"

        assertEquals(client.get(path).bodyAsText(), client.get(path).bodyAsText())
    }
}
