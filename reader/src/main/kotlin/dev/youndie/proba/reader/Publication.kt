package dev.youndie.proba.reader

/**
 * What a consumer's build would find, assembled from every document the reader had to visit.
 *
 * The whole point of this type is that it is *not* the root module: for a multiplatform library the
 * root carries no dependencies at all, and a reader that stops there reports every library healthy.
 */
data class Publication(
    val coordinate: Coordinate,
    val repository: MavenRepository,
    val targets: List<Target>,
    /** What the root document says about which component it belongs to. */
    val component: ComponentDeclaration?,
    /** Every module document actually fetched, root first — the evidence behind the picture. */
    val documents: List<Coordinate>,
    /** Targets the root points at that could not be fetched. A redirector pointing nowhere. */
    val unreachable: List<UnreachableTarget> = emptyList(),
)

/**
 * The `component` block of a module document.
 *
 * A root names itself. A target module names the component that owns it and carries a `url` back to
 * it — kotlinx-coroutines-core-jvm calls itself kotlinx-coroutines-core, and so does every other
 * multiplatform library. Reading a back-reference as a self-declaration makes every target module
 * look published under the wrong coordinate.
 */
data class ComponentDeclaration(val coordinate: Coordinate, val isBackReference: Boolean)

data class UnreachableTarget(val coordinate: Coordinate, val url: String, val status: Int)

/**
 * One platform of the publication. Its identity is the attribute tuple Gradle matches on, not the
 * name of a variant: variant names are a convention, attributes are the contract.
 */
data class Target(
    val key: TargetKey,
    val coordinate: Coordinate,
    val variants: List<Variant>,
) {
    val name: String get() = key.name

    /** What a consumer can compile against. */
    val apiVariant: Variant? get() = variants.firstOrNull { it.usage == Usage.Api && it.role == Role.Library }

    /** What a consumer gets at run time. */
    val runtimeVariant: Variant? get() = variants.firstOrNull { it.usage == Usage.Runtime && it.role == Role.Library }

    val sourcesVariant: Variant? get() = variants.firstOrNull { it.role == Role.Sources }

    /**
     * What common code compiles against. A multiplatform library's shared source set is published
     * with usage `kotlin-metadata` and not `kotlin-api`, so asking a common target for its api
     * variant answers "none" while the dependencies sit right there in the metadata one.
     */
    val metadataVariant: Variant? get() = variants.firstOrNull { it.usage == Usage.Metadata && it.role == Role.Library }
}

data class TargetKey(
    val platform: String?,
    val nativeTarget: String?,
    val wasmTarget: String?,
) {
    /**
     * The name a Kotlin build would call this target. A convenience for reading, derived from the
     * attributes above — never the other way round.
     */
    val name: String
        get() = when (platform) {
            null -> "jvm"                     // a plain java-library publication declares no platform type
            "native" -> nativeTarget?.snakeToCamel() ?: "native"
            "wasm" -> "wasm" + (wasmTarget?.replaceFirstChar(Char::uppercase) ?: "")
            else -> platform                  // common, jvm, js
        }

    private fun String.snakeToCamel(): String =
        split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercase) }
            .joinToString("")
}

data class Variant(
    val name: String,
    val attributes: Map<String, String>,
    val usage: Usage,
    val role: Role,
    val dependencies: List<Dependency>,
    val files: List<ArtefactFile>,
)

/** What the variant is for, read from `org.gradle.usage`. */
enum class Usage {
    Api, Runtime, Metadata, Other;

    companion object {
        fun of(usage: String?): Usage = when (usage) {
            "java-api", "kotlin-api" -> Api
            "java-runtime", "kotlin-runtime" -> Runtime
            "kotlin-metadata" -> Metadata
            else -> Other
        }
    }
}

/** Whether the variant carries the library or something about it, read from `org.gradle.category`. */
enum class Role {
    Library, Sources, Documentation, Other;

    companion object {
        fun of(category: String?, docsType: String?): Role = when {
            category == "library" -> Library
            docsType == "sources" -> Sources
            category == "documentation" -> Documentation
            else -> Other
        }
    }
}

data class Dependency(val group: String, val module: String, val requires: String?) {
    override fun toString(): String = "$group:$module" + (requires?.let { ":$it" } ?: "")
}

/**
 * `declaredName` is the name the file arrives under on a consumer's classpath; `url` is where it was
 * fetched from. Keeping both is the point — a publication is free to disagree with itself here.
 */
data class ArtefactFile(val declaredName: String, val url: String, val size: Long?, val sha1: String?)
