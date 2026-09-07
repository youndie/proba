package dev.youndie.proba.resolver

import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.MavenRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WHICH REPOSITORIES THE MODELLED CONSUMER DECLARES, pinned.
 *
 * Every entry in that list is there because without it the deep tier answered "undetermined" about
 * the harness rather than about the library — Google's for the Compose family, the plugin portal for
 * anything that applies a Gradle plugin. That is a reason easy to delete while tidying, and the
 * result of deleting it is not a red build: it is a check that stops answering and says so politely.
 */
class ConsumerRepositoriesTest {
    @Test
    fun `the modelled consumer declares the three repositories a real one has`() {
        assertEquals(
            listOf(
                "https://repo1.maven.org/maven2",
                "https://dl.google.com/dl/android/maven2",
                "https://plugins.gradle.org/m2",
            ),
            GradleConsumerResolver.DEFAULT_CONSUMER_REPOSITORIES,
        )
    }

    @Test
    fun `the generated build names the repository under test first, then those`() {
        val project = File.createTempFile("proba-consumer", "")
        project.delete()
        project.mkdirs()
        val underTest = MavenRepository("under test", "file:///tmp/local-repo")

        GradleConsumerResolver(project, File("."))
            .write(project, Coordinate("io.github.youndie.sborka", "conventions", "0.3.0"), underTest)

        val settings = File(project, "settings.gradle.kts").readText()

        // ORDER MATTERS AND IS NOT COSMETIC: Gradle asks the repositories in the order they are
        // declared, so the one under test has to come first — otherwise a coordinate that also
        // exists upstream is checked in its published form somewhere else and the answer is about
        // that copy.
        val declared =
            settings
                .lineSequence()
                .mapNotNull { line -> Regex("""uri\("([^"]+)"\)""").find(line)?.groupValues?.get(1) }
                .toList()

        assertEquals("file:///tmp/local-repo", declared.first())
        assertTrue(
            declared.containsAll(GradleConsumerResolver.DEFAULT_CONSUMER_REPOSITORIES),
            "the defaults are missing from the generated build: $declared",
        )
        project.deleteRecursively()
    }
}
