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

    /**
     * What a consumer build says about the types this publication hands out.
     *
     * Shared with [ApiOmitsSibling] rather than computed twice: the second check's suspicion states
     * the condition this one measures, so two answers that could disagree would be worse than one.
     */
    sealed interface Reach {
        /** Every type the public API mentions is on the compile classpath. */
        data object Complete : Reach

        data class Missing(val types: List<String>, val artefact: String, val classpath: Int) : Reach

        /** No consumer build, or one whose answer does not apply to this artefact. */
        data class Unknown(val why: String) : Reach
    }

    internal fun reach(context: CheckContext): Reach {
        val consumer = context.consumer
            ?: return Reach.Unknown(
                context.consumerRefusal
                    ?.let { "a consumer build was run and did not finish, so what a compile classpath receives is not known here: $it" }
                    ?: "no consumer build was run, so what a compile classpath receives is not known here",
            )

        val subject = consumer.compileClasspath.firstOrNull { it.isModule(context.publication.coordinate) }
            ?: return Reach.Unknown(
                "the consumer build resolved ${consumer.compileClasspath.size} artefact(s), " +
                    "none of them this module — nothing to read a public API out of",
            )

        consumer.toolKind(subject)?.let { kind ->
            return Reach.Unknown(
                "this is $kind, which a tool loads rather than a compiler resolves — " +
                    "nothing puts it on a compile classpath, so what one would receive is not a question about it",
            )
        }

        val unreachable = consumer.apiSurface(subject).filterNot { consumer.onCompileClasspath(it) }.sorted()
        return if (unreachable.isEmpty()) Reach.Complete
        else Reach.Missing(unreachable, subject.file.name, consumer.compileClasspath.size)
    }

    override val id = "api-unreachable"
    override val title = "every type the public API hands out can be named by a consumer"

    override suspend fun run(context: CheckContext): List<Finding> = when (val reach = reach(context)) {
        is Reach.Complete -> emptyList()

        is Reach.Unknown -> listOf(
            Finding(
                checkId = id,
                severity = Severity.Undetermined,
                subject = context.publication.coordinate.toString(),
                message = reach.why,
                evidence = context.consumerRefusal?.lines().orEmpty().take(8),
            ),
        )

        is Reach.Missing -> listOf(
            Finding(
                checkId = id,
                severity = Severity.Defect,
                subject = "${context.consumer?.target}: ${reach.artefact}",
                message = "the public API hands out ${reach.types.size} type(s) a consumer cannot name — " +
                    "${reach.types.take(4).joinToString(", ")}${if (reach.types.size > 4) ", …" else ""} — so code " +
                    "calling it fails with \"Cannot access class\" while this build, its tests and its publish stay green",
                evidence = reach.types.take(12).map { "not on the compile classpath: $it" } +
                    "compile classpath: ${reach.classpath} artefact(s)",
            ),
        )
    }
}
