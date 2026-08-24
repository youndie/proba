package dev.youndie.proba.resolver

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Read against the sample jars produced by `research-stand/broken-publication`, which exist so that
 * a defect is present on purpose. The jar is committed as it was built: the surface of a library is
 * a property of its bytecode, and a hand-written stand-in would only agree with whatever this reader
 * already believes bytecode looks like.
 */
class JarApiSurfaceTest {

    private fun fixture(name: String): File =
        File(checkNotNull(javaClass.getResource("/$name")) { "no fixture $name" }.toURI())

    @Test
    fun `a type handed out by a public function is part of the surface`() {
        val surface = JarApiSurface.of(fixture("lib-1.0.0.jar"))

        assertTrue(
            "dev.youndie.proba.sample.support.Token" in surface,
            "Gate.issue returns a Token, so a consumer has to be able to name it; got $surface",
        )
    }

    @Test
    fun `what the jar declares itself is not part of its surface`() {
        // A library naming its own classes needs nothing from a consumer's classpath for them, and
        // reporting them would drown the answer in every class the library has.
        val surface = JarApiSurface.of(fixture("lib-1.0.0.jar"))

        assertFalse(surface.any { it.startsWith("dev.youndie.proba.sample.lib.") }, "got $surface")
    }

    @Test
    fun `the platform is not part of the surface`() {
        // java.lang.String is on no compile classpath jar, so leaving it in would make every library
        // ever published look broken.
        val surface = JarApiSurface.of(fixture("support-1.0.0.jar"))

        assertFalse(surface.any { it.startsWith("java.") }, "got $surface")
    }

    @Test
    fun `a jar that is not there yields nothing rather than throwing`() {
        assertTrue(JarApiSurface.of(File("no-such.jar")).isEmpty())
    }
}
