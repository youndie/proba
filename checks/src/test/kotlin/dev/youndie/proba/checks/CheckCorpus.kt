package dev.youndie.proba.checks

import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.Fixtures
import dev.youndie.proba.reader.Publication
import dev.youndie.proba.reader.PublicationReader
import dev.youndie.proba.reader.ReadOutcome
import dev.youndie.proba.reader.servingAnything
import io.ktor.client.HttpClient

/**
 * A case is a publication and the answer a check owes about it.
 *
 * Every check must appear here with a case where it has to fire and a case where it has to stay
 * quiet, and [CheckCorpusTest] fails the build when one does not. A check with only quiet cases
 * proves nothing: staying quiet is also what a check does when it cannot run, when its subject never
 * reaches it, and when it was never wired up at all.
 */
enum class Expectation { Fires, Silent, Undetermined }

class CheckCase(
    val checkId: String,
    val expectation: Expectation,
    val name: String,
    val context: suspend () -> CheckContext,
)

object CheckCorpus {

    private const val GROUP = "io.github.youndie"
    private val SAMPLE = Coordinate("dev.youndie.proba.sample", "lib", "1.0.0")

    val cases: List<CheckCase> = listOf(

        CheckCase("version-in-declared-name", Expectation.Fires, "the archive keeps the build's version, not the coordinate's") {
            CheckContext(read(Fixtures.KompotCore, *Fixtures.KompotCoreDocuments))
        },
        CheckCase("version-in-declared-name", Expectation.Silent, "kotlinx-coroutines-core-jvm 1.11.0") {
            CheckContext(
                read(
                    Coordinate("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"),
                    "kotlinx-coroutines-core-jvm-1.11.0.module",
                ),
            )
        },

        CheckCase("dangling-redirect", Expectation.Fires, "the root alone, every target it names unpublished") {
            CheckContext(read(Fixtures.KompotCore, "kompot-core-0.27.0.46.module"))
        },
        CheckCase("dangling-redirect", Expectation.Silent, "kompot-core with every target published") {
            CheckContext(read(Fixtures.KompotCore, *Fixtures.KompotCoreDocuments))
        },

        CheckCase("component-matches-path", Expectation.Fires, "a real document answering at a version it does not claim") {
            CheckContext(
                readFrom(
                    Coordinate(GROUP, "kompot-core-jvm", "0.27.0.99"),
                    Fixtures.servingAnything("kompot-core-jvm-0.27.0.46.module"),
                ),
            )
        },
        CheckCase("component-matches-path", Expectation.Silent, "kompot-core-jvm at its own coordinate") {
            CheckContext(read(Coordinate(GROUP, "kompot-core-jvm", "0.27.0.46"), "kompot-core-jvm-0.27.0.46.module"))
        },

        CheckCase("api-omits-sibling", Expectation.Fires, "kompot-analytics-jvm 0.10.0.17 advertises only the standard library") {
            CheckContext(read(Coordinate(GROUP, "kompot-analytics-jvm", "0.10.0.17"), "kompot-analytics-jvm-0.10.0.17.module"))
        },
        CheckCase("api-omits-sibling", Expectation.Silent, "kompot-analytics-jvm 0.27.0.46 adds no sibling at run time") {
            CheckContext(read(Coordinate(GROUP, "kompot-analytics-jvm", "0.27.0.46"), "kompot-analytics-jvm-0.27.0.46.module"))
        },
        CheckCase("api-omits-sibling", Expectation.Silent, "kompot-core arrives through kompot-client, which is advertised") {
            CheckContext(
                publication = read(
                    Coordinate(GROUP, "kompot-ds-material-compose", "0.27.0.46"),
                    "kompot-ds-material-compose-0.27.0.46.module",
                    "kompot-ds-material-compose-desktop-0.27.0.46.module",
                ),
                lookup = lookup("kompot-client-0.27.0.46.module", "kompot-client-desktop-0.27.0.46.module"),
            )
        },
        CheckCase("api-unreachable", Expectation.Fires, "the public API hands out a type the classpath does not carry") {
            CheckContext(
                publication = read(SAMPLE, "lib-1.0.0.module"),
                consumer = RecordedConsumer.withoutSupport(),
            )
        },
        CheckCase("api-unreachable", Expectation.Silent, "the same API, with the type on the classpath") {
            CheckContext(
                publication = read(SAMPLE, "lib-1.0.0.module"),
                consumer = RecordedConsumer.withSupport(),
            )
        },
        CheckCase("api-unreachable", Expectation.Undetermined, "no consumer build was run") {
            CheckContext(read(SAMPLE, "lib-1.0.0.module"))
        },

        CheckCase("api-omits-sibling", Expectation.Undetermined, "the same publication with nothing to look up") {
            CheckContext(
                read(
                    Coordinate(GROUP, "kompot-ds-material-compose", "0.27.0.46"),
                    "kompot-ds-material-compose-0.27.0.46.module",
                    "kompot-ds-material-compose-desktop-0.27.0.46.module",
                ),
            )
        },
    )

    private suspend fun read(coordinate: Coordinate, vararg documents: String): Publication =
        readFrom(coordinate, Fixtures.serving(*documents))

    private suspend fun readFrom(coordinate: Coordinate, client: HttpClient): Publication {
        val outcome = PublicationReader(client).read(coordinate, Fixtures.Repository)
        return (outcome as? ReadOutcome.Read)?.publication
            ?: error("the case cannot be built: $coordinate read as $outcome")
    }

    private fun lookup(vararg documents: String) = PublicationLookup { coordinate ->
        (PublicationReader(Fixtures.serving(*documents)).read(coordinate, Fixtures.Repository) as? ReadOutcome.Read)
            ?.publication
    }
}
