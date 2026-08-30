package dev.youndie.proba.reader

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Prints what a consumer would find at a coordinate. This exists so the reader's answer can be
 * looked at rather than described: a claim about a publication is worth what reproducing it costs.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage: proba-reader <group:artifact:version> [--repo <base-url>] [--repo-name <name>]")
        exitProcess(64)
    }

    val coordinate = Coordinate.parse(args[0])
    val repository = repositoryFrom(args)

    val outcome =
        HttpClient(CIO).use { http ->
            runBlocking { PublicationReader(http).read(coordinate, repository) }
        }

    exitProcess(report(outcome, repository))
}

private fun repositoryFrom(args: Array<String>): MavenRepository {
    val url = args.option("--repo") ?: return MavenRepository.MavenCentral
    return MavenRepository(args.option("--repo-name") ?: url, url)
}

private fun Array<String>.option(name: String): String? =
    indexOf(name).takeIf { it >= 0 && it + 1 < size }?.let { this[it + 1] }

private fun report(
    outcome: ReadOutcome,
    repository: MavenRepository,
): Int =
    when (outcome) {
        is ReadOutcome.Read -> {
            val publication = outcome.publication
            println("${publication.coordinate}")
            println(
                "  read from ${repository.baseUrl} — ${publication.documents.size} document(s), ${publication.targets.size} target(s)",
            )
            publication.targets.forEach { target ->
                println()
                println("  ${target.name.padEnd(20)} ${target.coordinate}")
                line("api", target.apiVariant)
                line("metadata", target.metadataVariant)
                line("runtime", target.runtimeVariant)
                line("sources", target.sourcesVariant)
                target.variants.flatMap { it.files }.distinctBy { it.declaredName }.forEach { file ->
                    // Only the version is compared, not the whole name. Every multiplatform publication
                    // renames the shared jar on the way out — kotlinx-coroutines ships
                    // `kotlinx-coroutines-core-metadata-1.11.0.jar` from `kotlinx-coroutines-core-1.11.0.jar`
                    // — so a rule about the whole name would fire on healthy libraries. The rule itself
                    // belongs to the checks; this marker is here so the gate can be looked at.
                    val mark = if (publication.coordinate.version in file.declaredName) " " else "!"
                    println("    $mark file      ${file.declaredName}  <-  ${file.url.substringAfterLast('/')}")
                }
            }
            publication.unreachable.forEach {
                println()
                println("  ! the root points at ${it.coordinate}, which answered ${it.status}")
                println("      ${it.url}")
            }
            0
        }

        is ReadOutcome.WithoutModuleMetadata -> {
            println("${outcome.coordinate} — published, but without Gradle module metadata")
            println("  ${outcome.pomUrl} is there; what variants exist is not knowable from the repository alone")
            3
        }

        is ReadOutcome.NotFound -> {
            println("${outcome.coordinate} — nothing published here")
            outcome.tried.forEach {
                println(
                    "  tried ${it.url} → ${if (it.status == 0) "no answer" else it.status.toString()}",
                )
            }
            2
        }

        is ReadOutcome.UnsupportedLayout -> {
            println("${outcome.coordinate} — not read")
            println("  ${outcome.reason}")
            5
        }

        is ReadOutcome.Unreadable -> {
            println("${outcome.coordinate} — the module metadata could not be read")
            println("  ${outcome.url}")
            println("  ${outcome.reason}")
            4
        }
    }

private fun line(
    label: String,
    variant: Variant?,
) {
    if (variant == null) {
        println("    - ${label.padEnd(9)} none")
        return
    }
    val dependencies = variant.dependencies
    println("      ${label.padEnd(9)} ${dependencies.size}: ${dependencies.joinToString(", ").ifEmpty { "nothing" }}")
}
