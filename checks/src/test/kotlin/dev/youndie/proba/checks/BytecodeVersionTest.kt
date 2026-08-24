package dev.youndie.proba.checks

import dev.youndie.proba.reader.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The reading half, against real jars.
 *
 * Both come out of `research-stand/broken-publication`: one built by whatever JDK is at hand and one
 * pinned to Java 8, so the pair is two artefacts rather than one artefact and an idea of another.
 */
class BytecodeVersionTest {

    @Test
    fun `reads the version out of the first class a jar carries`() {
        assertEquals(69, BytecodeRequiresJava.firstClassFileVersion(Fixtures.bytes("lib-1.0.0.jar")), "Java 25")
        assertEquals(52, BytecodeRequiresJava.firstClassFileVersion(Fixtures.bytes("legacy-1.0.0.jar")), "Java 8")
    }

    @Test
    fun `answers nothing rather than a number for what it cannot read`() {
        // Null and 0 are different answers: one becomes an undetermined finding, the other would
        // become "Java -44" and be believed.
        assertNull(BytecodeRequiresJava.firstClassFileVersion(ByteArray(0)))
        assertNull(BytecodeRequiresJava.firstClassFileVersion("not a jar at all".toByteArray()))
    }
}
