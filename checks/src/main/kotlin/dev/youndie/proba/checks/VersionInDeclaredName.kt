package dev.youndie.proba.checks

/**
 * The name a file arrives under must carry the version that was asked for.
 *
 * Not "the declared name equals the url": every multiplatform publication renames the shared jar on
 * the way out — kotlinx-coroutines ships `kotlinx-coroutines-core-metadata-1.11.0.jar` from
 * `kotlinx-coroutines-core-1.11.0.jar` — so a rule about the whole name fires on healthy libraries.
 * Only the version is the publication's promise.
 */
object VersionInDeclaredName : Check {

    override val id = "version-in-declared-name"
    override val title = "the file a consumer receives is named with the version that was published"

    override suspend fun run(context: CheckContext): List<Finding> {
        val version = context.publication.coordinate.version
        return context.publication.targets.flatMap { target ->
            target.variants.flatMap { it.files }
                .distinctBy { it.declaredName }
                .filter { version !in it.declaredName }
                .map { file ->
                    Finding(
                        checkId = id,
                        severity = Severity.Defect,
                        subject = "${target.name}: ${file.declaredName}",
                        message = "arrives as \"${file.declaredName}\", which carries no version $version — " +
                            "anything that tells artefacts apart by file name records a version that was never published",
                        evidence = listOf("declared name ${file.declaredName}", "fetched from ${file.url}"),
                    )
                }
        }
    }
}
