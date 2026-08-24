package dev.youndie.proba.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What a repository will say about what it holds.
 *
 * There is no standard for this: a Maven repository serves files, and listing them is an extension
 * every host implements differently or not at all. So the index is a separate thing from the reader,
 * and its absence is an answer — [RepositoryIndex.of] returns null for a repository nobody can
 * enumerate, rather than an index that answers "no modules" and looks like an empty group.
 */
interface RepositoryIndex {

    /** Every module published under a group. */
    suspend fun modules(group: String): List<String>

    /** Every version of one module, newest last, as the repository advertises them. */
    suspend fun versions(group: String, artifact: String): List<String>

    companion object {
        fun of(repository: MavenRepository, fetcher: Fetcher): RepositoryIndex? = when {
            repository.baseUrl.startsWith(MavenRepository.MavenCentral.baseUrl) -> MavenCentralIndex(fetcher)
            // Reposilite serves /api/maven/details/<repo>/<path> beside /<repo>/<path>.
            Regex("^(https?://[^/]+)/([^/]+)/?$").find(repository.baseUrl)?.let { true } == true ->
                ReposiliteIndex(repository, fetcher)
            else -> null
        }
    }
}

/** Versions come from maven-metadata.xml, which every repository serves because resolution needs it. */
private suspend fun advertisedVersions(
    repository: MavenRepository,
    fetcher: Fetcher,
    group: String,
    artifact: String,
): List<String> {
    val url = repository.url("${group.replace('.', '/')}/$artifact/maven-metadata.xml")
    val body = fetcher.fetch(url).body ?: return emptyList()
    return runCatching {
        val document = DocumentBuilderFactory.newInstance()
            .also { it.isNamespaceAware = false; it.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder()
            .parse(body.byteInputStream())
        val nodes = document.getElementsByTagName("version")
        (0 until nodes.length).map { nodes.item(it).textContent.trim() }
    }.getOrDefault(emptyList())
}

class ReposiliteIndex(private val repository: MavenRepository, private val fetcher: Fetcher) : RepositoryIndex {

    private val details: String = Regex("^(https?://[^/]+)/([^/]+)/?$").find(repository.baseUrl)
        ?.let { "${it.groupValues[1]}/api/maven/details/${it.groupValues[2]}" }
        ?: error("not a reposilite base url: ${repository.baseUrl}")

    override suspend fun modules(group: String): List<String> {
        val body = fetcher.fetch("$details/${group.replace('.', '/')}").body ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(body).jsonObject["files"]?.jsonArray.orEmpty()
                .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "DIRECTORY" }
                .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        }.getOrDefault(emptyList())
    }

    override suspend fun versions(group: String, artifact: String): List<String> =
        advertisedVersions(repository, fetcher, group, artifact)
}

class MavenCentralIndex(private val fetcher: Fetcher) : RepositoryIndex {

    override suspend fun modules(group: String): List<String> {
        val body = fetcher.fetch(
            "https://search.maven.org/solrsearch/select?q=g:%22$group%22&rows=200&wt=json",
        ).body ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(body).jsonObject["response"]?.jsonObject?.get("docs")?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["a"]?.jsonPrimitive?.content }
                .distinct()
        }.getOrDefault(emptyList())
    }

    override suspend fun versions(group: String, artifact: String): List<String> =
        advertisedVersions(MavenRepository.MavenCentral, fetcher, group, artifact)
}
