package dev.youndie.proba.reader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.File
import java.net.URI

/**
 * Where documents come from.
 *
 * A repository is not always a web server: the one a publication is checked against first is usually
 * the local one it was just published into, and reaching it over http would mean either standing up
 * a server or checking something else instead.
 */
fun interface Fetcher {
    suspend fun fetch(url: String): FetchResult
}

/** A status of 0 means the request never got an answer at all. */
data class FetchResult(
    val status: Int,
    val body: String?,
)

class HttpFetcher(
    private val client: HttpClient,
) : Fetcher {
    override suspend fun fetch(url: String): FetchResult =
        try {
            val response = client.get(url)
            FetchResult(response.status.value, if (response.status.value == 200) response.bodyAsText() else null)
        } catch (failure: Exception) {
            FetchResult(0, null)
        }
}

/** Reads `file:` urls, which is what a local repository such as `~/.m2/repository` is addressed by. */
object FileFetcher : Fetcher {
    override suspend fun fetch(url: String): FetchResult {
        val file =
            try {
                File(URI(url))
            } catch (failure: Exception) {
                return FetchResult(0, null)
            }
        return if (file.isFile) FetchResult(200, file.readText()) else FetchResult(404, null)
    }
}

/** Picks a fetcher by scheme, so one reader can be pointed at either kind of repository. */
class RoutingFetcher(
    private val http: Fetcher,
) : Fetcher {
    override suspend fun fetch(url: String): FetchResult =
        if (url.startsWith("file:")) FileFetcher.fetch(url) else http.fetch(url)
}
