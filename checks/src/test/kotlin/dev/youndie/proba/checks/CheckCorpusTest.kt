package dev.youndie.proba.checks

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckCorpusTest {

    @Test
    fun `every case gets the answer it is owed`() = runTest {
        val wrong = CheckCorpus.cases.mapNotNull { case ->
            val findings = Checks.byId(case.checkId).run(case.context())
            val got = when {
                findings.isEmpty() -> Expectation.Silent
                findings.any { it.severity == Severity.Undetermined } -> Expectation.Undetermined
                else -> Expectation.Fires
            }
            if (got == case.expectation) null else {
                "${case.checkId} / ${case.name}: expected ${case.expectation}, got $got" +
                    findings.joinToString("") { "\n      ${it.severity} ${it.subject}: ${it.message}" }
            }
        }
        assertTrue(wrong.isEmpty(), "cases answered wrongly:\n  " + wrong.joinToString("\n  "))
    }

    @Test
    fun `no check is registered without a case on each side`() {
        // The guard is per check and not a total, because a total is satisfied by the checks that do
        // have cases while the one that does not slips through under cover of the others.
        val missing = Checks.all.mapNotNull { check ->
            val expectations = CheckCorpus.cases.filter { it.checkId == check.id }.map { it.expectation }.toSet()
            val lacking = buildList {
                if (Expectation.Fires !in expectations) add("case where it must fire")
                if (Expectation.Silent !in expectations) add("case where it must stay quiet")
            }
            if (lacking.isEmpty()) null else "${check.id} has no ${lacking.joinToString(" and no ")}"
        }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }

    @Test
    fun `every case names a check that exists`() {
        val registered = Checks.all.map { it.id }.toSet()
        assertEquals(emptyList(), CheckCorpus.cases.map { it.checkId }.distinct().filterNot { it in registered })
    }
}
