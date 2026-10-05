package dev.youndie.proba.resolver

import dev.youndie.proba.checks.Finding
import dev.youndie.proba.checks.Severity
import dev.youndie.proba.reader.Coordinate
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WHAT `--fail-on` FAILS ON. A threshold, not a match: `suspicion` is the stricter setting, so it has
 * to refuse everything `defect` refuses and suspicions besides. Counted by equality it did the
 * opposite of what its name promises — a defect alone passed under `suspicion` while `defect`
 * refused it (#29).
 */
class FailOnTest {
    private val coordinate = Coordinate.parse("dev.youndie.proba.sample:lib:1.0.0")

    private fun finding(severity: Severity) = Finding("some-check", severity, "jvm", "what was found")

    private fun run(
        findings: List<Finding>,
        failOn: Severity?,
    ): Pair<Int, String> {
        val captured = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        val code =
            try {
                report(coordinate, deep = false, findings = findings, failOn = failOn)
            } finally {
                System.setOut(original)
            }
        return code to captured.toString(Charsets.UTF_8)
    }

    @Test
    fun `a defect fails a run that asked to fail on suspicions`() {
        val (code, output) = run(listOf(finding(Severity.Defect)), Severity.Suspicion)

        assertEquals(1, code, output)
        // The line says what it counted, not the name of the threshold it counted against.
        assertTrue(output.contains("1 defect(s)"), output)
        assertFalse(output.contains("suspicion(s)"), output)
    }

    @Test
    fun `a suspicion does not fail a run that asked to fail on defects`() {
        val (code, output) = run(listOf(finding(Severity.Suspicion)), Severity.Defect)

        assertEquals(0, code, output)
    }

    @Test
    fun `under suspicion each severity counted is named with its own count`() {
        val findings = listOf(finding(Severity.Defect), finding(Severity.Suspicion), finding(Severity.Suspicion))
        val (code, output) = run(findings, Severity.Suspicion)

        assertEquals(1, code, output)
        assertTrue(output.contains("1 defect(s), 2 suspicion(s)"), output)
    }

    // The other side of the threshold: "worse than" runs one way, and undetermined is never worse.
    // A comparison turned around would fail a build for "I could not check", which is what teaches
    // people to pass --fail-on none.
    @Test
    fun `undetermined fails nothing, even under suspicion`() {
        val (code, output) = run(listOf(finding(Severity.Undetermined)), Severity.Suspicion)

        assertEquals(0, code, output)
    }

    @Test
    fun `none fails nothing, a defect included`() {
        val (code, output) = run(listOf(finding(Severity.Defect)), null)

        assertEquals(0, code, output)
    }
}
