package dev.youndie.proba.reader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.File
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

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
    /**
     * What went wrong when [status] is 0, in the words the failure used. Kept because a fetch that
     * never got an answer and a fetch that got an empty one look identical from [status] alone, and
     * the reason is the only thing that tells a wrong url from an unreachable host.
     */
    val failure: String? = null,
)

class HttpFetcher(
    private val client: HttpClient,
) : Fetcher {
    override suspend fun fetch(url: String): FetchResult =
        try {
            val response = client.get(url)
            FetchResult(response.status.value, if (response.status.value == 200) response.bodyAsText() else null)
        } catch (failure: CancellationException) {
            // A cancelled run is not a repository that failed to answer. Reported as one it becomes
            // `status = 0` with a reason, and proba's whole job is to say what a consumer would
            // actually get -- an answer it invented is worse than no answer.
            throw failure
        } catch (failure: Exception) {
            FetchResult(0, null, failure.message ?: failure::class.simpleName)
        }
}

/** Reads `file:` urls, which is what a local repository such as `~/.m2/repository` is addressed by. */
object FileFetcher : Fetcher {
    @Suppress(
        "ktlint:kapkan:cancellation-swallowed",
        "building a File from a URI is synchronous: there is no suspension point to be cancelled at",
    )
    override suspend fun fetch(url: String): FetchResult {
        val file =
            try {
                File(URI(url))
            } catch (failure: Exception) {
                return FetchResult(0, null, failure.message ?: failure::class.simpleName)
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
