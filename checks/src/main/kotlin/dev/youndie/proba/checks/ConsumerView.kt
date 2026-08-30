package dev.youndie.proba.checks

import dev.youndie.proba.reader.Coordinate
import java.io.File

/**
 * What a real consumer build received.
 *
 * The cheap tier reads what a publication *declares*. This is the other question: what a build that
 * actually asked for the coordinate ended up holding. It is an interface here and implemented
 * elsewhere so that a check can be written against it without the checks knowing how to run Gradle —
 * and so that a check can be exercised against a recorded consumer without running one.
 */
interface ConsumerView {
    val target: String

    /** Every artefact on the compile classpath, as Gradle's own resolution reported it. */
    val compileClasspath: List<ResolvedArtifact>

    val runtimeClasspath: List<ResolvedArtifact>

    /** Class names the artefact's public API mentions and does not itself declare. */
    fun apiSurface(artifact: ResolvedArtifact): Set<String>

    /** Whether a class can be found anywhere on the compile classpath. */
    fun onCompileClasspath(className: String): Boolean

    /**
     * What kind of artefact this is, when it is one a tool loads rather than one a compiler resolves.
     *
     * A KSP processor implements `SymbolProcessorProvider`, so those types really are in its public
     * signatures — and it is still not a defect, because nothing puts a processor on a compile
     * classpath. KSP loads it through its own configuration and supplies that API itself, which is
     * why `implementation` is what every processor declares.
     *
     * The question this check asks — what a consumer receives when they add this — has no meaning for
     * such an artefact. Answering it anyway describes a consumer who does not exist.
     */
    fun toolKind(artifact: ResolvedArtifact): String? = null
}

data class ResolvedArtifact(
    val coordinate: Coordinate?,
    val file: File,
) {
    /**
     * Whether this artefact is the module that was asked about. The artefact resolved for a
     * multiplatform coordinate carries a target suffix — asking for `kompot-core` gets
     * `kompot-core-jvm` — so the suffix is allowed and nothing else is.
     */
    fun isModule(other: Coordinate): Boolean =
        coordinate != null &&
            coordinate.group == other.group &&
            (coordinate.artifact == other.artifact || coordinate.artifact.startsWith("${other.artifact}-"))
}
