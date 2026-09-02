package dev.youndie.proba.resolver

import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a consumer can actually write, which is narrower than what the JVM calls public.
 *
 * Two of the three shapes here cannot be produced by hand: a generated resource accessor needs
 * Compose Resources, and the classes inlining leaves behind appear at a call site rather than in the
 * library that declares the function. So the artefact that produced the report is committed and read
 * as it was published.
 */
class JarApiSurfaceTest {
    private fun fixture(name: String): File =
        File(checkNotNull(javaClass.getResource("/$name")) { "no fixture $name" }.toURI())

    private val sample by lazy { JarApiSurface.of(fixture("lib-1.0.0.jar")) }
    private val real by lazy { JarApiSurface.of(fixture("kompot-client-desktop-0.29.0.56.jar")) }

    @Test
    fun `a type handed out by a public function is part of the surface`() {
        assertTrue("dev.youndie.proba.sample.support.Token" in sample, "Gate.issue returns one; got $sample")
    }

    @Test
    fun `a type mentioned only by an internal member is not`() {
        // Kotlin `internal` has no JVM counterpart and compiles to public. Reading the class file
        // alone reports Hint as handed out by an API nobody can call, and then suggests widening
        // every consumer's compile classpath for it.
        assertFalse("dev.youndie.proba.sample.support.Hint" in sample, "got $sample")
    }

    @Test
    fun `what a generated accessor mentions is not part of the surface`() {
        // Compose Resources generates a file of accessors for the module's own resources, whose
        // properties are INTERNAL in the Kotlin metadata and public in the class file.
        assertTrue(real.none { it.startsWith("org.jetbrains.compose.resources.") }, real.toString())
    }

    @Test
    fun `and a type a public member really returns still is`() {
        // The other half of the same jar, so the test above cannot pass by filtering everything.
        assertTrue("androidx.compose.foundation.layout.PaddingValues" in real, "got ${real.take(12)}")
    }

    @Test
    fun `what the jar declares itself is not part of its surface`() {
        assertFalse(sample.any { it.startsWith("dev.youndie.proba.sample.lib.") }, "got $sample")
    }

    @Test
    fun `the platform is not part of the surface`() {
        assertFalse(sample.any { it.startsWith("java.") }, "got $sample")
    }

    @Test
    fun `a jar that is not there yields nothing rather than throwing`() {
        assertTrue(JarApiSurface.of(File("no-such.jar")).isEmpty())
    }
}

class ArtefactKindTest {
    private fun jarWith(vararg entries: String): File {
        val file = File(createTempDirectory("proba-kind").toFile(), "artefact.jar")
        JarOutputStream(file.outputStream()).use { out ->
            entries.forEach { name ->
                out.putNextEntry(JarEntry(name))
                out.write(byteArrayOf())
                out.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `an artefact says for itself what loads it`() {
        // Read out of what the jar declares rather than guessed from its name: a service file is how
        // such an artefact announces itself, and it is there so a tool can find it.
        assertTrue(
            jarWith("META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider")
                .let(ArtefactKind::of)!!
                .contains("KSP"),
        )
        assertTrue(jarWith("META-INF/gradle-plugins/some.plugin.properties").let(ArtefactKind::of)!!.contains("Gradle"))
    }

    @Test
    fun `a library says nothing, because nothing loads it but a compiler`() {
        assertTrue(ArtefactKind.of(jarWith("dev/youndie/Thing.class")) == null)
    }
}
