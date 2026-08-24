package dev.youndie.proba.reader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Gradle Module Metadata as published, and no more than that.
 *
 * These types deliberately mirror the file rather than the picture proba wants: what a reader
 * believes about a publication has to be derivable from what is written down, and keeping the two
 * apart is what makes the derivation reviewable.
 */
@Serializable
data class GmmDocument(
    val formatVersion: String = "",
    val component: GmmComponent = GmmComponent(),
    val variants: List<GmmVariant> = emptyList(),
)

@Serializable
data class GmmComponent(
    val group: String? = null,
    val module: String? = null,
    val version: String? = null,
    /** Present only on a target module: the path back to the component that owns it. */
    val url: String? = null,
)

@Serializable
data class GmmVariant(
    val name: String,
    val attributes: Map<String, JsonPrimitive> = emptyMap(),
    val dependencies: List<GmmDependency> = emptyList(),
    val dependencyConstraints: List<GmmDependency> = emptyList(),
    val files: List<GmmFile> = emptyList(),
    @SerialName("available-at") val availableAt: GmmAvailableAt? = null,
)

/** A variant that lives in another module: the root module of a multiplatform library is a redirector. */
@Serializable
data class GmmAvailableAt(
    val url: String,
    val group: String,
    val module: String,
    val version: String,
) {
    val coordinate: Coordinate get() = Coordinate(group, module, version)
}

@Serializable
data class GmmDependency(
    val group: String,
    val module: String,
    val version: GmmVersion? = null,
)

@Serializable
data class GmmVersion(
    val requires: String? = null,
    val prefers: String? = null,
    val strictly: String? = null,
)

/**
 * `name` is what the file is called once it reaches a consumer; `url` is where it lies in the
 * repository. They are separate fields and they are allowed to disagree.
 */
@Serializable
data class GmmFile(
    val name: String,
    val url: String,
    val size: Long? = null,
    val sha512: String? = null,
    val sha256: String? = null,
    val sha1: String? = null,
    val md5: String? = null,
)
