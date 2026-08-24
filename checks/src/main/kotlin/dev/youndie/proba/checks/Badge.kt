package dev.youndie.proba.checks

/**
 * The badge for a README.
 *
 * A badge is the smallest surface the severity language has to survive on, and the one where colour
 * is most tempting to lean on: it is an image in a page that is often read in a terminal, in a
 * plaintext mirror, or by somebody who cannot tell the red from the ochre. So the state is a **word**
 * — "2 defects", "clean" — and the colour only agrees with it. The same sentence goes into the
 * `aria-label`, which is what is left when the image does not load.
 */
object Badge {

    private const val LABEL = "proba"

    fun of(findings: List<Finding>): String = svg(message(findings), colour(findings), message(findings))

    fun refusal(reason: String): String = svg(reason, Neutral, reason)

    fun message(findings: List<Finding>): String {
        val defects = findings.count { it.severity == Severity.Defect }
        val suspicions = findings.count { it.severity == Severity.Suspicion }
        val undetermined = findings.count { it.severity == Severity.Undetermined }
        return when {
            defects > 0 && suspicions > 0 -> "$defects ${plural(defects, "defect")}, $suspicions ${plural(suspicions, "suspicion")}"
            defects > 0 -> "$defects ${plural(defects, "defect")}"
            suspicions > 0 -> "$suspicions ${plural(suspicions, "suspicion")}"
            // A check that could not run is not a check that passed. "clean" here would put a badge on
            // a library whose questions were never answered, which is the exact confusion the whole
            // tool exists to remove — and a badge is where it would be seen most and inspected least.
            undetermined > 0 -> "$undetermined unchecked"
            // "clean" and not "0 defects": the second reads as a count that happens to be zero today.
            else -> "clean"
        }
    }

    private fun plural(count: Int, word: String) = if (count == 1) word else "${word}s"

    private fun colour(findings: List<Finding>): String = when {
        findings.any { it.severity == Severity.Defect } -> Defect
        findings.any { it.severity == Severity.Suspicion } -> Suspicion
        findings.any { it.severity == Severity.Undetermined } -> Neutral
        else -> Clean
    }

    // Taken from design/tokens.json rather than chosen here; an SVG has no CSS variables to resolve,
    // so the light values are baked in and a badge is the one place the palette is not a token.
    private const val Defect = "#a4302a"
    private const val Suspicion = "#8a6212"
    private const val Clean = "#3f7d46"
    private const val Neutral = "#5d6b73"

    private fun svg(message: String, colour: String, label: String): String {
        val left = width(LABEL)
        val right = width(message)
        val total = left + right
        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="$total" height="20" role="img" aria-label="$LABEL: $label">
              <title>$LABEL: $label</title>
              <linearGradient id="s" x2="0" y2="100%">
                <stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/>
              </linearGradient>
              <clipPath id="r"><rect width="$total" height="20" rx="3" fill="#fff"/></clipPath>
              <g clip-path="url(#r)">
                <rect width="$left" height="20" fill="#4b4a48"/>
                <rect x="$left" width="$right" height="20" fill="$colour"/>
                <rect width="$total" height="20" fill="url(#s)"/>
              </g>
              <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
                <text x="${left / 2}" y="14">$LABEL</text>
                <text x="${left + right / 2}" y="14">${message.escaped()}</text>
              </g>
            </svg>
        """.trimIndent()
    }

    /** Verdana at 11px is close enough to 6.6px a character for a badge nobody measures. */
    private fun width(text: String) = (text.length * 6.6).toInt() + 12

    private fun String.escaped() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
