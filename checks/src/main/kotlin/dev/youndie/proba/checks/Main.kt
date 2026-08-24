package dev.youndie.proba.checks

import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.MavenRepository
import dev.youndie.proba.reader.Publication
import dev.youndie.proba.reader.PublicationReader
import dev.youndie.proba.reader.ReadOutcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/** Runs every check against a published coordinate and prints what they answered. */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage: proba <group:artifact:version> [--repo <base-url>]")
        exitProcess(64)
    }

    val coordinate = Coordinate.parse(args[0])
    val repository = args.indexOf("--repo").takeIf { it >= 0 && it + 1 < args.size }
        ?.let { MavenRepository(args[it + 1], args[it + 1]) }
        ?: MavenRepository.MavenCentral

    exitProcess(
        HttpClient(CIO).use { http ->
            runBlocking {
                val reader = PublicationReader(http)
                when (val outcome = reader.read(coordinate, repository)) {
                    is ReadOutcome.Read -> report(coordinate, Checks.runAll(context(outcome.publication, reader, repository)))
                    else -> {
                        println("$coordinate — not checked: $outcome")
                        2
                    }
                }
            }
        },
    )
}

private fun context(publication: Publication, reader: PublicationReader, repository: MavenRepository): CheckContext {
    // A dependency read once is read once: a transitive api walk revisits the same coordinate from
    // several targets, and each visit is a request over the network.
    val cache = mutableMapOf<Coordinate, Publication?>()
    return CheckContext(publication) { coordinate ->
        cache.getOrPut(coordinate) {
            (reader.read(coordinate, repository) as? ReadOutcome.Read)?.publication
        }
    }
}

private fun report(coordinate: Coordinate, findings: List<Finding>): Int {
    println("$coordinate — ${Checks.all.size} checks, ${findings.size} finding(s)")
    if (findings.isEmpty()) {
        println("  nothing to report")
        return 0
    }
    findings.groupBy { it.severity }.toSortedMap().forEach { (severity, group) ->
        println()
        println("  ${severity.name.uppercase()}")
        group.forEach { finding ->
            println("    [${finding.checkId}] ${finding.subject}")
            println("        ${finding.message}")
            finding.evidence.forEach { println("        · $it") }
        }
    }
    return if (findings.any { it.severity == Severity.Defect }) 1 else 0
}
