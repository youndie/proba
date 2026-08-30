package dev.youndie.proba.checks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun finding(severity: Severity) = Finding(checkId = "c", severity = severity, subject = "s", message = "m")

class BadgeTest {
    @Test
    fun `the state is a word, and the colour only agrees with it`() {
        // The one place colour is most tempting to lean on, and the one where it survives least: a
        // README is read in terminals, in plaintext mirrors, and by people who cannot tell red from
        // ochre. Whatever else changes, the word has to be there.
        assertEquals("clean", Badge.message(emptyList()))
        assertEquals("1 defect", Badge.message(listOf(finding(Severity.Defect))))
        assertEquals("2 defects", Badge.message(List(2) { finding(Severity.Defect) }))
        assertEquals("1 suspicion", Badge.message(listOf(finding(Severity.Suspicion))))
        assertEquals(
            "1 defect, 1 suspicion",
            Badge.message(listOf(finding(Severity.Defect), finding(Severity.Suspicion))),
        )
    }

    @Test
    fun `a check that could not run does not read as a check that passed`() {
        // The first version of this test was named this and asserted "clean", which is a confession
        // rather than a test. A badge is seen most and inspected least: "clean" on a library whose
        // questions were never answered is the exact confusion this tool exists to remove.
        assertEquals("1 unchecked", Badge.message(listOf(finding(Severity.Undetermined))))
        assertEquals("2 unchecked", Badge.message(List(2) { finding(Severity.Undetermined) }))

        // A defect outranks it: what is known comes first.
        assertEquals(
            "1 defect",
            Badge.message(listOf(finding(Severity.Defect), finding(Severity.Undetermined))),
        )
    }

    @Test
    fun `the sentence survives the image not loading`() {
        val svg = Badge.of(listOf(finding(Severity.Defect)))

        assertTrue(svg.contains("""aria-label="proba: 1 defect""""), svg.take(200))
        assertTrue(svg.contains("<title>proba: 1 defect</title>"))
    }
}
