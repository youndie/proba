package dev.youndie.proba.checks

import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.Dependency
import dev.youndie.proba.reader.Publication
import dev.youndie.proba.reader.Target

/**
 * A module whose api variant advertises none of its own siblings while the run time gets them.
 *
 * This is the metadata half of the defect where every project dependency is declared
 * `implementation`: the build, the tests and the publish are all green, the published metadata says
 * a consumer needs nothing but the standard library, and only a consumer finds out.
 *
 * It is a [Severity.Suspicion] and not a defect, because the same shape is correct whenever the
 * public API genuinely does not mention the sibling. Telling the two apart means reading the public
 * signatures out of the artefact, which the repository alone cannot answer.
 *
 * The comparison follows **api edges transitively**, because that is what a consumer's compile
 * classpath really receives: `kompot-ds-material-compose` never names `kompot-core` itself and does
 * not need to, since it advertises `kompot-client`, whose own api variant advertises `kompot-core`.
 * Without the transitive walk this check calls that healthy publication broken.
 */
object ApiOmitsSibling : Check {

    override val id = "api-omits-sibling"
    override val title = "what the api variant advertises covers the siblings the run time gets"

    private const val MAX_DEPTH = 6

    override suspend fun run(context: CheckContext): List<Finding> {
        val publication = context.publication
        val group = publication.coordinate.group

        // The suspicion states its own condition — correct only if no public signature mentions them —
        // and the confirming tier measures exactly that. When it ran and found nothing unreachable, the
        // case is decided and the word for it is not "suspicion": keeping it would teach a reader that
        // suspicions are noise, and the next one, on a publication nothing answered, reads the same.
        val answered = ApiUnreachable.reach(context) is ApiUnreachable.Reach.Complete

        return publication.targets.mapNotNull { target ->
            // Only for the target the consumer build actually ran for; the others were not answered.
            if (answered && target.name == context.consumer?.target) return@mapNotNull null
            val api = target.apiVariant ?: target.metadataVariant ?: return@mapNotNull null
            val runtime = target.runtimeVariant ?: return@mapNotNull null

            val omitted = runtime.dependencies.filter { it.group == group }.map { it.key() }.toSet() -
                api.dependencies.map { it.key() }.toSet()
            if (omitted.isEmpty()) return@mapNotNull null

            val walk = reach(omitted, api.dependencies, context, group)

            when {
                walk.remaining.isEmpty() -> null

                walk.unread.isNotEmpty() -> Finding(
                    checkId = id,
                    severity = Severity.Undetermined,
                    subject = target.name,
                    message = "the api variant does not reach ${walk.remaining.sorted().joinToString(", ")}, " +
                        "and ${walk.unread.sorted().joinToString(", ")} could not be read, so whether the " +
                        "transitive closure covers them is not known from here",
                    evidence = walk.unread.sorted().map { "unread $it" },
                )

                else -> Finding(
                    checkId = id,
                    severity = Severity.Suspicion,
                    subject = target.name,
                    message = "a consumer compiling against this target never receives " +
                        "${walk.remaining.sorted().joinToString(", ")}, which the run time does receive — " +
                        "correct only if no public signature mentions them",
                    evidence = listOf(
                        "api ${api.name}: ${api.dependencies.joinToString(", ") { it.key() }.ifEmpty { "nothing" }}",
                        "runtime ${runtime.name}: ${runtime.dependencies.joinToString(", ") { it.key() }}",
                    ),
                )
            }
        }
    }

    private class Walk(val remaining: Set<String>, val unread: Set<String>)

    /**
     * Walks api edges outwards until nothing is missing any more, and no further.
     *
     * Driven by what is still missing rather than by building the whole closure: a branch that could
     * not be read only matters if the answer still depends on it. Building the closure first made a
     * healthy publication come back undetermined because two of its api dependencies were unread —
     * while the third already covered everything the run time added.
     */
    private suspend fun reach(
        omitted: Set<String>,
        direct: List<Dependency>,
        context: CheckContext,
        group: String,
    ): Walk {
        var remaining = omitted
        val visited = mutableSetOf<String>()
        val unread = mutableSetOf<String>()
        var frontier = direct.filter { it.group == group }

        repeat(MAX_DEPTH) {
            if (remaining.isEmpty() || frontier.isEmpty()) return Walk(remaining, unread)

            val next = mutableListOf<Dependency>()
            for (dependency in frontier) {
                if (!visited.add(dependency.key())) continue
                remaining = remaining - dependency.key()
                val version = dependency.requires
                val read = version?.let { context.lookup.read(Coordinate(dependency.group, dependency.module, it)) }
                if (read == null) {
                    unread += dependency.key()
                    continue
                }
                next += read.apiDependencies().filter { it.group == group }
            }
            frontier = next
        }
        return Walk(remaining, unread)
    }

    private fun Publication.apiDependencies(): List<Dependency> =
        targets.flatMap { target: Target ->
            (target.apiVariant ?: target.metadataVariant)?.dependencies.orEmpty()
        }

    private fun Dependency.key() = "$group:$module"
}
