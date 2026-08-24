package dev.youndie.proba.reader

import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * The outcome of reading a publication.
 *
 * A refusal is a value and not an empty picture on purpose. An unreachable coordinate and a library
 * that genuinely declares nothing would otherwise arrive at a caller in the same shape, and every
 * check downstream would call the first one healthy.
 */
sealed interface ReadOutcome {

    data class Read(val publication: Publication) : ReadOutcome

    /** The artefact is there, but publishes no Gradle module metadata. What variants it has is not knowable from here. */
    data class WithoutModuleMetadata(val coordinate: Coordinate, val pomUrl: String) : ReadOutcome

    data class NotFound(val coordinate: Coordinate, val tried: List<Attempt>) : ReadOutcome

    data class Unreadable(val coordinate: Coordinate, val url: String, val reason: String) : ReadOutcome

    /**
     * The version exists but this reader cannot address it.
     *
     * A SNAPSHOT does not keep its files under its own name: they carry a build timestamp, and
     * finding them means reading a second, version-level maven-metadata.xml. Until that is written,
     * saying so is the only honest answer — reporting "nothing is published here" about a version
     * that is published is exactly the confident wrongness this tool exists to catch.
     */
    data class UnsupportedLayout(val coordinate: Coordinate, val reason: String) : ReadOutcome
}

data class Attempt(val url: String, val status: Int)

class PublicationReader(
    private val fetcher: Fetcher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    constructor(client: HttpClient, json: Json = Json { ignoreUnknownKeys = true }) :
        this(RoutingFetcher(HttpFetcher(client)), json)

    suspend fun read(coordinate: Coordinate, repository: MavenRepository): ReadOutcome {
        if (coordinate.isSnapshot) {
            return ReadOutcome.UnsupportedLayout(
                coordinate,
                "a SNAPSHOT keeps its files under a build timestamp rather than under the version, " +
                    "and resolving that layout is not implemented",
            )
        }
        val tried = mutableListOf<Attempt>()

        val moduleUrl = repository.url(coordinate.file("module"))
        val moduleBody = fetch(moduleUrl, tried)
            ?: return absent(coordinate, repository, tried)

        val root = try {
            json.decodeFromString<GmmDocument>(moduleBody)
        } catch (failure: Exception) {
            return ReadOutcome.Unreadable(coordinate, moduleUrl, failure.message ?: "not module metadata")
        }

        // The root of a multiplatform library is a redirector: its variants carry no dependencies and
        // point at one module per target. Several variants point at the same module, so fetch each once.
        val redirects = root.variants.mapNotNull { it.availableAt }.distinctBy { it.coordinate }

        val fetched = coroutineScope {
            redirects.map { redirect ->
                async {
                    val url = repository.url(redirect.coordinate.file("module"))
                    val attempts = mutableListOf<Attempt>()
                    val body = fetch(url, attempts)
                    redirect.coordinate to (body?.let { runCatching { json.decodeFromString<GmmDocument>(it) }.getOrNull() }
                        to attempts.lastOrNull()?.status)
                }
            }.awaitAll()
        }

        val located = buildList {
            // Variants the root keeps for itself — the common/metadata ones.
            root.variants.filter { it.availableAt == null }.forEach { add(coordinate to it) }
            fetched.forEach { (target, result) ->
                result.first?.variants?.forEach { add(target to it) }
            }
        }

        val unreachable = fetched.filter { it.second.first == null }.map { (target, result) ->
            UnreachableTarget(target, repository.url(target.file("module")), result.second ?: 0)
        }

        return ReadOutcome.Read(
            Publication(
                coordinate = coordinate,
                repository = repository,
                component = root.component.let { component ->
                    val group = component.group
                    val module = component.module
                    val version = component.version
                    if (group == null || module == null || version == null) null
                    else ComponentDeclaration(Coordinate(group, module, version), isBackReference = component.url != null)
                },
                targets = group(located),
                documents = listOf(coordinate) + fetched.filter { it.second.first != null }.map { it.first },
                unreachable = unreachable,
            ),
        )
    }

    private suspend fun absent(
        coordinate: Coordinate,
        repository: MavenRepository,
        tried: MutableList<Attempt>,
    ): ReadOutcome {
        // No module metadata is not the same as nothing published: a plain Maven publication has a POM
        // and no variants at all, and saying so is more use than reporting a coordinate that is not there.
        val pomUrl = repository.url(coordinate.file("pom"))
        return if (fetch(pomUrl, tried) != null) {
            ReadOutcome.WithoutModuleMetadata(coordinate, pomUrl)
        } else {
            ReadOutcome.NotFound(coordinate, tried.toList())
        }
    }

    private suspend fun fetch(url: String, tried: MutableList<Attempt>): String? {
        val result = fetcher.fetch(url)
        tried += Attempt(url, result.status)
        return result.body
    }

    private fun group(located: List<Pair<Coordinate, GmmVariant>>): List<Target> =
        located.groupBy({ keyOf(it.second) }, { it })
            .map { (key, entries) ->
                Target(
                    key = key,
                    coordinate = entries.first().first,
                    variants = entries.map { variant(it.second) },
                )
            }
            .sortedBy { it.name }

    private fun keyOf(variant: GmmVariant): TargetKey = TargetKey(
        platform = variant.attribute(PLATFORM_TYPE),
        nativeTarget = variant.attribute(NATIVE_TARGET),
        wasmTarget = variant.attribute(WASM_TARGET),
    )

    private fun variant(source: GmmVariant): Variant {
        val attributes = source.attributes.mapValues { (_, value) -> value.content }
        return Variant(
            name = source.name,
            attributes = attributes,
            usage = Usage.of(attributes[USAGE]),
            role = Role.of(attributes[CATEGORY], attributes[DOCS_TYPE]),
            dependencies = source.dependencies.map { Dependency(it.group, it.module, it.version?.requires) },
            files = source.files.map { ArtefactFile(it.name, it.url, it.size, it.sha1) },
        )
    }

    private fun GmmVariant.attribute(key: String): String? = attributes[key]?.content

    private companion object {
        const val USAGE = "org.gradle.usage"
        const val CATEGORY = "org.gradle.category"
        const val DOCS_TYPE = "org.gradle.docstype"
        const val PLATFORM_TYPE = "org.jetbrains.kotlin.platform.type"
        const val NATIVE_TARGET = "org.jetbrains.kotlin.native.target"
        const val WASM_TARGET = "org.jetbrains.kotlin.wasm.target"
    }
}
