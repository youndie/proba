package dev.youndie.proba.checks

/**
 * The verdict [ApiOmitsSibling] cannot reach: a type the library hands out that a consumer cannot name.
 *
 * The suspicion and the defect have the same shape in the metadata, because declaring a dependency
 * `implementation` is correct exactly when the public API does not mention it. Deciding needs two
 * things the repository does not hold: what the public signatures actually say, which is in the
 * artefact, and what a consumer's compile classpath actually receives, which is Gradle's answer and
 * not a re-implementation of Gradle's rules.
 *
 * Only what the compile classpath was resolved for is answered. A target no consumer build was run
 * for is [Severity.Undetermined] and says so, because a check that quietly skips a target reads
 * exactly like a check that passed it.
 */
object ApiUnreachable : Check {

    override val id = "api-unreachable"
    override val title = "every type the public API hands out can be named by a consumer"

    override suspend fun run(context: CheckContext): List<Finding> {
        val consumer = context.consumer ?: return listOf(
            Finding(
                checkId = id,
                severity = Severity.Undetermined,
                subject = context.publication.coordinate.toString(),
                message = "no consumer build was run, so what a compile classpath receives is not known here",
            ),
        )

        val subject = consumer.compileClasspath.firstOrNull { it.isModule(context.publication.coordinate) }
            ?: return listOf(
                Finding(
                    checkId = id,
                    severity = Severity.Undetermined,
                    subject = context.publication.coordinate.toString(),
                    message = "the consumer build resolved ${consumer.compileClasspath.size} artefact(s), " +
                        "none of them this module — nothing to read a public API out of",
                ),
            )

        val unreachable = consumer.apiSurface(subject)
            .filterNot { consumer.onCompileClasspath(it) }
            .sorted()

        if (unreachable.isEmpty()) return emptyList()

        return listOf(
            Finding(
                checkId = id,
                severity = Severity.Defect,
                subject = "${consumer.target}: ${subject.file.name}",
                message = "the public API hands out ${unreachable.size} type(s) a consumer cannot name — " +
                    "${unreachable.take(4).joinToString(", ")}${if (unreachable.size > 4) ", …" else ""} — so code " +
                    "calling it fails with \"Cannot access class\" while this build, its tests and its publish stay green",
                evidence = unreachable.take(12).map { "not on the compile classpath: $it" } +
                    "compile classpath: ${consumer.compileClasspath.size} artefact(s)",
            ),
        )
    }
}
