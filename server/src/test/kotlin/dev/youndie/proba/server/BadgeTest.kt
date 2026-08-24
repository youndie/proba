package dev.youndie.proba.server

import dev.youndie.proba.reader.Fixtures
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgeEndpointTest {

    @Test
    fun `the endpoint serves an svg and asks to be cached`() = testApplication {
        application { proba(Fixtures.serving(*Fixtures.KompotCoreDocuments)) }

        val response = client.get("/badge/io.github.youndie/kompot-core/0.27.0.46.svg?repo=https://repo.example/maven2")

        assertEquals("image/svg+xml", response.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        assertTrue(response.headers[HttpHeaders.CacheControl]?.contains("max-age") == true)
        assertTrue(response.bodyAsText().startsWith("<svg"))
    }

    @Test
    fun `a coordinate that is not published says so rather than looking clean`() = testApplication {
        application { proba(Fixtures.serving()) }

        val svg = client.get("/badge/io.example/absent/1.0.0.svg?repo=https://repo.example/maven2").bodyAsText()

        assertTrue(svg.contains("not published"), svg.take(200))
    }
}
