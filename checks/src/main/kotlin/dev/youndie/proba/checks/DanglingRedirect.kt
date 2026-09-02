package dev.youndie.proba.checks

/**
 * The root module points at one module per target. When one of them is not there, the publication
 * still reads — with fewer targets and no complaint. Fewer targets is what a version published
 * halfway looks like, so it must not arrive as a smaller healthy picture.
 */
object DanglingRedirect : Check {
    override val id = "dangling-redirect"
    override val title = "every target the root points at is published"

    override suspend fun run(context: CheckContext): List<Finding> =
        context.publication.unreachable.map { missing ->
            Finding(
                checkId = id,
                severity = Severity.Defect,
                subject = missing.coordinate.toString(),
                message =
                    "the root names this module as the home of a target, and the repository answered " +
                        "${missing.status} for it — a consumer asking for that target gets nothing",
                evidence = listOf(missing.url, "status ${missing.status}"),
            )
        }
}
