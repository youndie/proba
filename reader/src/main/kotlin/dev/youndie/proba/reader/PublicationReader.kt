package dev.youndie.proba.reader

import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The outcome of reading a publication.
 *
 * A refusal is a value and not an empty picture on purpose. An unreachable coordinate and a library
 * that genuinely declares nothing would otherwise arrive at a caller in the same shape, and every
 * check downstream would call the first one healthy.
 */
sealed interface ReadOutcome {
    data class Read(
        val publication: Publication,
    ) : ReadOutcome

    /** The artefact is there, but publishes no Gradle module metadata. What variants it has is not knowable from here. */
    data class WithoutModuleMetadata(
        val coordinate: Coordinate,
        val pomUrl: String,
    ) : ReadOutcome

    data class NotFound(
        val coordinate: Coordinate,
        val tried: List<Attempt>,
    ) : ReadOutcome

    data class Unreadable(
        val coordinate: Coordinate,
        val url: String,
        val reason: String,
    ) : ReadOutcome

    /**
     * The version is there and the reader cannot address its files.
     *
     * Left for a snapshot whose version-level metadata names neither a module nor a pom: something is
     * published, and what it is called is not written anywhere this reader knows to look. Saying so
     * is the only honest answer — "nothing is published here" about a version that is published is
     * exactly the confident wrongness this tool exists to catch.
     */
    data class UnsupportedLayout(
        val coordinate: Coordinate,
        val reason: String,
    ) : ReadOutcome
}

data class Attempt(
    val url: String,
    val status: Int,
)

class PublicationReader(
    private val fetcher: Fetcher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    // One version-level metadata document serves every file of that snapshot, and a multiplatform
    // snapshot asks for one per target.
    private val snapshots = mutableMapOf<String, String?>()

    /** The current timestamped value per snapshot directory, learned while resolving its files. */
    private val snapshotStamps = mutableMapOf<String, String>()

    constructor(client: HttpClient, json: Json = Json { ignoreUnknownKeys = true }) :
        this(RoutingFetcher(HttpFetcher(client)), json)

    suspend fun read(
        coordinate: Coordinate,
        repository: MavenRepository,
    ): ReadOutcome {
        val tried = mutableListOf<Attempt>()

        val moduleUrl =
            fileUrl(coordinate, "module", repository, tried)
                ?: return absent(coordinate, repository, tried)
        val moduleBody =
            fetch(moduleUrl, tried)
                ?: return absent(coordinate, repository, tried)

        val root =
            try {
                json.decodeFromString<GmmDocument>(moduleBody)
            } catch (failure: Exception) {
                return ReadOutcome.Unreadable(coordinate, moduleUrl, failure.message ?: "not module metadata")
            }

        // The root of a multiplatform library is a redirector: its variants carry no dependencies and
        // point at one module per target. Several variants point at the same module, so fetch each once.
        val redirects = root.variants.mapNotNull { it.availableAt }.distinctBy { it.coordinate }

        val fetched =
            coroutineScope {
                redirects
                    .map { redirect ->
                        async {
                            val attempts = mutableListOf<Attempt>()
                            // The targets of a snapshot are snapshots too, and each keeps its own timestamp.
                            val url = fileUrl(redirect.coordinate, "module", repository, attempts)
                            val body = url?.let { fetch(it, attempts) }
                            redirect.coordinate to
                                (
                                    body?.let { runCatching { json.decodeFromString<GmmDocument>(it) }.getOrNull() }
                                        to attempts.lastOrNull()?.status
                                )
                        }
                    }.awaitAll()
            }

        val located =
            buildList {
                // Variants the root keeps for itself — the common/metadata ones.
                root.variants.filter { it.availableAt == null }.forEach { add(coordinate to it) }
                fetched.forEach { (target, result) ->
                    result.first?.variants?.forEach { add(target to it) }
                }
            }

        val unreachable =
            fetched.filter { it.second.first == null }.map { (target, result) ->
                UnreachableTarget(target, repository.url(target.file("module")), result.second ?: 0)
            }

        return ReadOutcome.Read(
            Publication(
                coordinate = coordinate,
                repository = repository,
                component =
                    root.component.let { component ->
                        val group = component.group
                        val module = component.module
                        val version = component.version
                        if (group == null || module == null || version == null) {
                            null
                        } else {
                            ComponentDeclaration(
                                Coordinate(group, module, version),
                                isBackReference =
                                    component.url != null,
                            )
                        }
                    },
                targets = group(located),
                documents = listOf(coordinate) + fetched.filter { it.second.first != null }.map { it.first },
                unreachable = unreachable,
            ),
        )
    }

    /**
     * Where a file of this coordinate actually lies.
     *
     * A release keeps its files under its own name. A SNAPSHOT does not: `0.1.0-SNAPSHOT` publishes
     * `s3-client-0.1.0-20260817.123924-1.module`, and which timestamp is current is written in a
     * second, version-level maven-metadata.xml. A reader that assumes the release layout reports a
     * published version as absent, with confidence.
     */
    private suspend fun fileUrl(
        coordinate: Coordinate,
        extension: String,
        repository: MavenRepository,
        tried: MutableList<Attempt>,
    ): String? {
        if (!coordinate.isSnapshot) return repository.url(coordinate.file(extension))
        val metadataUrl = repository.url("${coordinate.directory}/maven-metadata.xml")
        val metadata = snapshots.getOrPut(metadataUrl) { fetch(metadataUrl, tried) } ?: return null
        val value = snapshotValue(metadata, extension) ?: return null
        snapshotStamps[coordinate.directory] = value
        return repository.url("${coordinate.directory}/${coordinate.artifact}-$value.$extension")
    }

    /** The timestamped name for one extension, ignoring the classified entries beside it. */
    private fun snapshotValue(
        metadata: String,
        extension: String,
    ): String? =
        runCatching {
            val document =
                DocumentBuilderFactory
                    .newInstance()
                    .also {
                        it.isNamespaceAware = false
                        it.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    }.newDocumentBuilder()
                    .parse(metadata.byteInputStream())
            val entries = document.getElementsByTagName("snapshotVersion")
            (0 until entries.length)
                .map { entries.item(it) }
                .firstOrNull { entry ->
                    val children = (0 until entry.childNodes.length).map { entry.childNodes.item(it) }
                    children.none { it.nodeName == "classifier" } &&
                        children.any { it.nodeName == "extension" && it.textContent.trim() == extension }
                }?.let { entry ->
                    (0 until entry.childNodes.length)
                        .map { entry.childNodes.item(it) }
                        .firstOrNull { it.nodeName == "value" }
                        ?.textContent
                        ?.trim()
                }
        }.getOrNull()

    private suspend fun absent(
        coordinate: Coordinate,
        repository: MavenRepository,
        tried: MutableList<Attempt>,
    ): ReadOutcome {
        // No module metadata is not the same as nothing published: a plain Maven publication has a POM
        // and no variants at all, and saying so is more use than reporting a coordinate that is not there.
        val pomUrl = fileUrl(coordinate, "pom", repository, tried)
        if (pomUrl == null) {
            return if (coordinate.isSnapshot) {
                ReadOutcome.UnsupportedLayout(
                    coordinate,
                    "the version-level metadata of this snapshot names neither a module nor a pom",
                )
            } else {
                ReadOutcome.NotFound(coordinate, tried.toList())
            }
        }
        return if (fetch(pomUrl, tried) != null) {
            ReadOutcome.WithoutModuleMetadata(coordinate, pomUrl)
        } else {
            ReadOutcome.NotFound(coordinate, tried.toList())
        }
    }

    private suspend fun fetch(
        url: String,
        tried: MutableList<Attempt>,
    ): String? {
        val result = fetcher.fetch(url)
        tried += Attempt(url, result.status)
        return result.body
    }

    /**
     * Targets are formed from the variants that carry the library; documentation joins them.
     *
     * A sources variant does not always say which platform it documents. Gradle's Java plugin adds
     * `sourcesElements` with no Kotlin platform attribute at all, while `apiElements` beside it has
     * one — so grouping every variant by its attributes alone splits one publication into two
     * targets, both of them called jvm, one holding the code and one holding the sources. Asking
     * such a target for its sources then answers "none" about a publication that has them, which is
     * how this reader came to state, out loud, that a library published no sources when it did.
     *
     * Documentation with no platform of its own is attached to the one target there is. When there
     * are several it is left where it fell rather than guessed at: a wrong attachment would make one
     * platform look documented and another not.
     */
    private fun group(located: List<Pair<Coordinate, GmmVariant>>): List<Target> {
        val (documentation, library) = located.partition { roleOf(it.second) != Role.Library }
        val targets =
            library
                .groupBy({ keyOf(it.second) }, { it })
                .map { (key, entries) ->
                    Target(
                        key = key,
                        coordinate = entries.first().first,
                        variants = entries.map { variant(it.second, entries.first().first) },
                    )
                }

        val attached =
            documentation.map { (coordinate, source) ->
                val key = keyOf(source)
                val home =
                    targets.firstOrNull { it.key == key }
                        ?: targets.singleOrNull()?.takeIf { key == TargetKey(null, null, null) }
                (home?.key ?: key) to (coordinate to source)
            }

        val extra = attached.groupBy({ it.first }, { it.second })
        return (
            targets.map { target ->
                target.copy(
                    variants = target.variants + extra[target.key].orEmpty().map { variant(it.second, it.first) },
                )
            } +
                extra.filterKeys { key -> targets.none { it.key == key } }.map { (key, entries) ->
                    Target(
                        key = key,
                        coordinate = entries.first().first,
                        variants = entries.map { variant(it.second, it.first) },
                    )
                }
        ).sortedBy { it.name }
    }

    private fun roleOf(variant: GmmVariant): Role = Role.of(variant.attribute(CATEGORY), variant.attribute(DOCS_TYPE))

    private fun keyOf(variant: GmmVariant): TargetKey =
        TargetKey(
            platform = variant.attribute(PLATFORM_TYPE),
            nativeTarget = variant.attribute(NATIVE_TARGET),
            wasmTarget = variant.attribute(WASM_TARGET),
        )

    private fun variant(
        source: GmmVariant,
        coordinate: Coordinate,
    ): Variant {
        val attributes = source.attributes.mapValues { (_, value) -> value.content }
        return Variant(
            name = source.name,
            attributes = attributes,
            usage = Usage.of(attributes[USAGE]),
            role = Role.of(attributes[CATEGORY], attributes[DOCS_TYPE]),
            dependencies = source.dependencies.map { Dependency(it.group, it.module, it.version?.requires) },
            files = source.files.map { ArtefactFile(it.name, resolved(it.url, coordinate), it.size, it.sha1) },
        )
    }

    /**
     * The name a file of a snapshot really has.
     *
     * Module metadata inside a snapshot names its files with `-SNAPSHOT` in them, and the files on
     * disk carry the build timestamp instead. Anything fetching by that url gets a 404 and reads it
     * as an absent artefact — so the url is corrected here, once, rather than in every caller who
     * would have to know about snapshots to get it right.
     */
    private fun resolved(
        url: String,
        coordinate: Coordinate,
    ): String {
        if (!coordinate.isSnapshot) return url
        val stamped = snapshotStamps[coordinate.directory] ?: return url
        return url.replace(coordinate.version, stamped)
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
