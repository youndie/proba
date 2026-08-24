package dev.youndie.proba.checks

import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.Publication

/**
 * One question asked of a publication from the consumer's side.
 *
 * A check that cannot reach an answer says so with [Severity.Undetermined] rather than returning
 * nothing. Nothing is what a healthy publication returns, and the two must never arrive in the same
 * shape: a check silently unable to run reads as a check that passed.
 */
interface Check {
    val id: String
    val title: String

    suspend fun run(context: CheckContext): List<Finding>
}

class CheckContext(
    val publication: Publication,
    val lookup: PublicationLookup = PublicationLookup { null },
    /** Present only when a real consumer build was run for this publication. */
    val consumer: ConsumerView? = null,
    /** Fetches a published file. Absent when only the metadata is available. */
    val artefacts: ArtefactSource? = null,
    /**
     * Why there is no [consumer], when the reason is that one was attempted and failed.
     *
     * "Not run" and "run, and it failed" are different answers and lead to different actions, so a
     * check that has neither must not report the first about the second.
     */
    val consumerRefusal: String? = null,
)

/**
 * The bytes of a published file.
 *
 * Metadata says what a module declares; some questions are only answerable by the artefact, and not
 * all of them need a build to ask. Eight bytes of one class file say which Java a consumer must be
 * running, and no line of metadata has to agree with them.
 */
fun interface ArtefactSource {
    suspend fun bytes(url: String): ByteArray?
}

/** Reads another publication — an api dependency, a neighbouring version. May answer null. */
fun interface PublicationLookup {
    suspend fun read(coordinate: Coordinate): Publication?
}

enum class Severity {
    /** The publication is wrong, and what is wrong can be pointed at. */
    Defect,

    /** The shape is one defects take, and telling them apart needs more than the repository holds. */
    Suspicion,

    /** The check could not run here, and is saying so instead of passing quietly. */
    Undetermined,
}

data class Finding(
    val checkId: String,
    val severity: Severity,
    /** What the finding is about — a target, a file, a coordinate. */
    val subject: String,
    val message: String,
    /** What was read to reach it, so the claim can be checked without rerunning anything. */
    val evidence: List<String> = emptyList(),
)

object Checks {

    val all: List<Check> = listOf(
        VersionInDeclaredName,
        DanglingRedirect,
        ComponentMatchesPath,
        ApiOmitsSibling,
        ApiUnreachable,
        BytecodeRequiresJava,
    )

    fun byId(id: String): Check = all.single { it.id == id }

    suspend fun runAll(context: CheckContext): List<Finding> = all.flatMap { it.run(context) }
}
