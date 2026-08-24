package dev.youndie.proba.resolver

import dev.youndie.proba.checks.CheckContext
import dev.youndie.proba.checks.Checks
import dev.youndie.proba.checks.Finding
import dev.youndie.proba.checks.httpArtefacts
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
        println(
            "usage: proba <group:artifact:version> [--repo <base-url>] [--deep] [--workspace <dir>] " +
                "[--wrapper <dir>] [--gradle-home <dir>] [--fail-on defect|suspicion|none] [--summary <file>]",
        )
        exitProcess(64)
    }

    val coordinate = Coordinate.parse(args[0])
    val repository = args.option("--repo")?.let { MavenRepository(it, it) } ?: MavenRepository.MavenCentral
    val deep = "--deep" in args
    // What counts as failing. A suspicion is a shape defects take and not a defect, so it does not
    // stop a build by default: a check that cries wolf gets switched off, and then it stops finding
    // the real ones too.
    val failOn = when (val level = args.option("--fail-on") ?: "defect") {
        "defect" -> Severity.Defect
        "suspicion" -> Severity.Suspicion
        "none" -> null
        else -> {
            println("--fail-on takes defect, suspicion or none, not \"$level\"")
            exitProcess(64)
        }
    }
    val summary = args.option("--summary")?.let { File(it) }
    val workspace = File(args.option("--workspace") ?: System.getProperty("java.io.tmpdir") + "/proba-workspace")
    // Where the consumer build takes its Gradle from. Named rather than guessed: the working directory
    // of a forked process is not the repository root, and a guess here fails far from its cause.
    val wrapper = File(args.option("--wrapper") ?: System.getProperty("user.dir"))
    val gradleHome = args.option("--gradle-home")?.let { File(it) }

    exitProcess(
        HttpClient(CIO).use { http ->
            runBlocking {
                val reader = PublicationReader(http)
                when (val outcome = reader.read(coordinate, repository)) {
                    is ReadOutcome.Read -> {
                        val consumer = if (deep) resolve(coordinate, repository, workspace, wrapper, gradleHome) else null
                        val context = context(outcome.publication, reader, repository, consumer, http)
                        val findings = Checks.runAll(context)
                        summary?.writeText(markdown(coordinate, deep, findings))
                        report(coordinate, deep, findings, failOn)
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

private fun resolve(
    coordinate: Coordinate,
    repository: MavenRepository,
    workspace: File,
    wrapper: File,
    gradleHome: File?,
): ResolvedConsumerView? {
    println("  running a consumer build in ${workspace.absolutePath} …")
    return when (
        val outcome = GradleConsumerResolver(workspace, wrapper, gradleHome = gradleHome).resolve(coordinate, repository)
    ) {
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
    http: HttpClient,
): CheckContext {
    val cache = mutableMapOf<Coordinate, Publication?>()
    return CheckContext(
        publication = publication,
        lookup = { wanted -> cache.getOrPut(wanted) { (reader.read(wanted, repository) as? ReadOutcome.Read)?.publication } },
        consumer = consumer,
        artefacts = httpArtefacts(http),
    )
}

private fun Array<String>.option(name: String): String? =
    indexOf(name).takeIf { it >= 0 && it + 1 < size }?.let { this[it + 1] }

/**
 * The findings as a markdown table, for a CI run's summary.
 *
 * The severity is a word here and a word only. A badge or a page can add a colour and a shape; a
 * summary is read as text, pasted into an issue and quoted in a chat, and none of those carry either.
 */
private fun markdown(coordinate: Coordinate, deep: Boolean, findings: List<Finding>): String = buildString {
    appendLine("### proba — `$coordinate`")
    appendLine()
    appendLine("${findings.size} finding(s) against ${if (deep) "both tiers" else "the repository alone"}.")
    if (findings.isEmpty()) return@buildString
    appendLine()
    appendLine("| severity | check | subject | what |")
    appendLine("| --- | --- | --- | --- |")
    findings.sortedBy { it.severity.ordinal }.forEach {
        appendLine("| ${it.severity.name.lowercase()} | `${it.checkId}` | `${it.subject}` | ${it.message.replace("|", "\\|")} |")
    }
}

private fun report(coordinate: Coordinate, deep: Boolean, findings: List<Finding>, failOn: Severity?): Int {
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
    // Undetermined never fails a build. It means the check could not run here, and turning "I do not
    // know" into a red build teaches people to pass --fail-on none, which switches off the answers too.
    val failing = failOn?.let { level -> findings.count { it.severity == level } } ?: 0
    if (failing > 0) println()
    if (failing > 0) println("  $failing ${failOn!!.name.lowercase()}(s) — failing as asked by --fail-on")
    return if (failing > 0) 1 else 0
}
