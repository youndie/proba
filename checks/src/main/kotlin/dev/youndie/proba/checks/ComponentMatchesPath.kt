package dev.youndie.proba.checks

/**
 * A module document names the coordinate it belongs to. When that disagrees with the path it was
 * found at, the artefact was published under a coordinate its own build does not know about — the
 * shape a module takes when it is missing its group and inherits somebody else's.
 */
object ComponentMatchesPath : Check {
    override val id = "component-matches-path"
    override val title = "the module document agrees with the coordinate it is published under"

    override suspend fun run(context: CheckContext): List<Finding> {
        val asked = context.publication.coordinate
        val declared =
            context.publication.component ?: return listOf(
                Finding(
                    checkId = id,
                    severity = Severity.Undetermined,
                    subject = asked.toString(),
                    message = "the module document names no component, so there is nothing to compare the path with",
                ),
            )

        // A target module names the component that owns it, not itself, so its module name is expected
        // to differ. What still has to hold is the version: a target published under one version while
        // pointing back at another leaves a consumer resolving across two releases.
        val disagreement =
            when {
                declared.isBackReference -> {
                    if (declared.coordinate.version == asked.version) {
                        null
                    } else {
                        "points back at ${declared.coordinate}, a different version from the $asked it is published under"
                    }
                }

                declared.coordinate == asked -> {
                    null
                }

                else -> {
                    "calls itself ${declared.coordinate}"
                }
            } ?: return emptyList()

        return listOf(
            Finding(
                checkId = id,
                severity = Severity.Defect,
                subject = asked.toString(),
                message = "published at $asked, but the module document $disagreement",
                evidence =
                    listOf(
                        "path $asked",
                        "component ${declared.coordinate}" + if (declared.isBackReference) " (back-reference)" else "",
                    ),
            ),
        )
    }
}
