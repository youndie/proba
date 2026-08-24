package dev.youndie.proba.resolver

import dev.youndie.proba.checks.CheckContext
import dev.youndie.proba.checks.Checks
import dev.youndie.proba.checks.Finding
import dev.youndie.proba.checks.Severity
import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.MavenRepository
import dev.youndie.proba.reader.Publication
import dev.youndie.proba.reader.PublicationReader
import dev.youndie.proba.reader.ReadOutcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * Both tiers: read the publication, ask the cheap questions, and — with `--deep` — run a consumer
 * build so the questions the repository cannot answer get answered by a consumer instead.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage: proba <group:artifact:version> [--repo <base-url>] [--deep] [--workspace <dir>] [--wrapper <dir>]")
        exitProcess(64)
    }

    val coordinate = Coordinate.parse(args[0])
    val repository = args.option("--repo")?.let { MavenRepository(it, it) } ?: MavenRepository.MavenCentral
    val deep = "--deep" in args
    val workspace = File(args.option("--workspace") ?: System.getProperty("java.io.tmpdir") + "/proba-workspace")
    // Where the consumer build takes its Gradle from. Named rather than guessed: the working directory
    // of a forked process is not the repository root, and a guess here fails far from its cause.
    val wrapper = File(args.option("--wrapper") ?: System.getProperty("user.dir"))

    exitProcess(
        HttpClient(CIO).use { http ->
            runBlocking {
                val reader = PublicationReader(http)
                when (val outcome = reader.read(coordinate, repository)) {
                    is ReadOutcome.Read -> {
                        val consumer = if (deep) resolve(coordinate, repository, workspace, wrapper) else null
                        val context = context(outcome.publication, reader, repository, consumer)
                        report(coordinate, deep, Checks.runAll(context))
                    }

                    else -> {
                        println("$coordinate — not checked: $outcome")
                        2
                    }
                }
            }
        },
    )
}

private fun resolve(coordinate: Coordinate, repository: MavenRepository, workspace: File, wrapper: File): ResolvedConsumerView? {
    println("  running a consumer build in ${workspace.absolutePath} …")
    return when (val outcome = GradleConsumerResolver(workspace, wrapper).resolve(coordinate, repository)) {
        is ResolutionOutcome.Resolved -> outcome.view
        is ResolutionOutcome.Failed -> {
            println("  the consumer build did not answer: ${outcome.reason}")
            outcome.output.lines().takeLast(6).forEach { println("    $it") }
            null
        }
    }
}

private fun context(
    publication: Publication,
    reader: PublicationReader,
    repository: MavenRepository,
    consumer: ResolvedConsumerView?,
): CheckContext {
    val cache = mutableMapOf<Coordinate, Publication?>()
    return CheckContext(
        publication = publication,
        lookup = { wanted -> cache.getOrPut(wanted) { (reader.read(wanted, repository) as? ReadOutcome.Read)?.publication } },
        consumer = consumer,
    )
}

private fun Array<String>.option(name: String): String? =
    indexOf(name).takeIf { it >= 0 && it + 1 < size }?.let { this[it + 1] }

private fun report(coordinate: Coordinate, deep: Boolean, findings: List<Finding>): Int {
    val tier = if (deep) "both tiers" else "the repository alone"
    println("$coordinate — ${Checks.all.size} checks against $tier, ${findings.size} finding(s)")
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
