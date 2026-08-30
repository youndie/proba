package dev.youndie.proba.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Recorded from an actual failed consumer run, closing boilerplate and all. */
private val GRADLE_FAILURE =
    """
    > Task :probaClasspath FAILED

    FAILURE: Build failed with an exception.

    * What went wrong:
    Execution failed for task ':probaClasspath'.
    > Could not resolve all dependencies for configuration ':compileClasspath'.
       > Could not find androidx.lifecycle:lifecycle-common:2.9.4.
         Required by:
             root project : > io.github.youndie:kompot-client:0.29.0.56

    * Try:
    > Run with --stacktrace option to get the stack trace.
    > Run with --info or --debug option to get more log output.
    > Run with --scan to get full insights from a Build Scan (powered by Develocity).
    > Get more help at https://help.gradle.org.

    BUILD FAILED in 1s
    """.trimIndent()

class CauseOfTest {
    @Test
    fun `keeps what went wrong rather than the advice that follows it`() {
        val cause = causeOf(GRADLE_FAILURE)

        assertTrue(cause.any { it.contains("Could not find androidx.lifecycle") }, cause.toString())
        assertTrue(cause.any { it.contains("Required by") }, "and who required it")
        // The four lines a reader of this report cannot act on: they are advice to somebody holding
        // the build, and the reader of a proba report is not.
        assertTrue(cause.none { it.contains("--stacktrace") }, cause.toString())
        assertTrue(cause.none { it.contains("BUILD FAILED") }, cause.toString())
    }

    @Test
    fun `falls back to the head, not the tail, when nothing is marked`() {
        // The tail of an unmarked failure is the last thing printed before exit, which is rarely the
        // reason; the first thing usually is.
        val output = (1..40).joinToString("\n") { "line $it" }

        val cause = causeOf(output, limit = 3)

        assertEquals(listOf("line 1", "line 2", "line 3"), cause)
    }

    @Test
    fun `is bounded, so a pathological failure cannot become the report`() {
        val huge = "* What went wrong:\n" + (1..500).joinToString("\n") { "detail $it" }

        assertEquals(24, causeOf(huge).size)
    }
}
