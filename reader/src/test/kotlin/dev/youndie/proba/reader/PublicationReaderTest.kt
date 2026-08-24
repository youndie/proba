package dev.youndie.proba.reader

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PublicationReaderTest {

    private fun read(client: io.ktor.client.HttpClient, coordinate: Coordinate = Fixtures.KompotCore) =
        PublicationReader(client)

    @Test
    fun `follows the redirector and finds every target`() = runTest {
        // The root module of a multiplatform library carries no dependencies of its own. A reader that
        // stops at it sees an empty publication and cannot tell it from a library that declares nothing.
        val outcome = read(Fixtures.serving(*Fixtures.KompotCoreDocuments))
            .read(Fixtures.KompotCore, Fixtures.Repository)

        val publication = (outcome as? ReadOutcome.Read)?.publication ?: fail("not read: $outcome")

        assertEquals(
            listOf("common", "iosArm64", "iosSimulatorArm64", "iosX64", "jvm", "wasmJs"),
            publication.targets.map { it.name },
        )
        assertEquals(6, publication.documents.size, "one document per target plus the root")
        assertTrue(publication.unreachable.isEmpty())
    }

    @Test
    fun `reads what a consumer compiles against apart from what it runs against`() = runTest {
        // kompot-standard advertises three dependencies to a compiler and four to a run time: the extra
        // one is the annotations module. If the two were read as one, the difference — the whole point
        // of asking — would disappear.
        val outcome = PublicationReader(Fixtures.serving("kompot-standard-jvm-0.27.0.46.module"))
            .read(Coordinate("io.github.youndie", "kompot-standard-jvm", "0.27.0.46"), Fixtures.Repository)

        val target = (outcome as ReadOutcome.Read).publication.targets.single { it.name == "jvm" }

        assertEquals(3, assertNotNull(target.apiVariant).dependencies.size)
        assertEquals(4, assertNotNull(target.runtimeVariant).dependencies.size)
        assertEquals(
            listOf("io.github.youndie:kompot-registry-annotations"),
            (target.runtimeVariant!!.dependencies - target.apiVariant!!.dependencies.toSet())
                .map { "${it.group}:${it.module}" },
        )
    }

    @Test
    fun `common code compiles against the metadata variant, which is not an api variant`() = runTest {
        // A shared source set is published with usage kotlin-metadata. Asking a common target for its
        // api variant answers "none" while its dependencies sit in the metadata one, so the reader has
        // to name both or a caller will read absence where there is content.
        val outcome = PublicationReader(Fixtures.serving(*Fixtures.KompotCoreDocuments))
            .read(Fixtures.KompotCore, Fixtures.Repository)

        val common = (outcome as ReadOutcome.Read).publication.targets.single { it.name == "common" }

        assertEquals(null, common.apiVariant)
        assertEquals(2, assertNotNull(common.metadataVariant).dependencies.size)
    }

    @Test
    fun `a coordinate that is not published is refused rather than reported empty`() = runTest {
        val outcome = PublicationReader(Fixtures.serving()) // serves nothing
            .read(Coordinate("io.example", "absent", "1.0.0"), Fixtures.Repository)

        val refusal = outcome as? ReadOutcome.NotFound ?: fail("expected a refusal, got $outcome")
        assertEquals(
            listOf("absent-1.0.0.module", "absent-1.0.0.pom"),
            refusal.tried.map { it.url.substringAfterLast('/') },
            "the refusal has to say what was looked for, or it cannot be acted on",
        )
        assertTrue(refusal.tried.all { it.status == 404 })
    }

    @Test
    fun `an artefact published without module metadata is named as such`() = runTest {
        // Absent metadata and an absent artefact are different answers. Collapsing them would let a
        // check report "no variants declared" about a library that never declared variants at all.
        val client = Fixtures.answering { path ->
            if (path.endsWith(".pom")) HttpStatusCode.OK to "<project/>" else HttpStatusCode.NotFound to ""
        }

        val outcome = PublicationReader(client)
            .read(Coordinate("io.example", "old", "1.0.0"), Fixtures.Repository)

        assertTrue(outcome is ReadOutcome.WithoutModuleMetadata, "got $outcome")
    }

    @Test
    fun `a root pointing at a module that is not there says so`() = runTest {
        // The failure this guards against is silent: drop the target documents and the publication
        // still reads, with fewer targets and no complaint. Fewer targets is exactly what a half
        // published version looks like, so it must not arrive as a smaller healthy picture.
        val outcome = PublicationReader(Fixtures.serving("kompot-core-0.27.0.46.module")) // root only
            .read(Fixtures.KompotCore, Fixtures.Repository)

        val publication = (outcome as ReadOutcome.Read).publication

        assertEquals(5, publication.unreachable.size, "every target the root points at is missing")
        assertTrue(publication.unreachable.all { it.status == 404 })
        assertEquals(listOf("common"), publication.targets.map { it.name })
    }

    @Test
    fun `metadata that is not module metadata is refused, not silently empty`() = runTest {
        val client = Fixtures.answering { HttpStatusCode.OK to "<html>404 not found</html>" }

        val outcome = PublicationReader(client)
            .read(Coordinate("io.example", "proxied", "1.0.0"), Fixtures.Repository)

        assertTrue(outcome is ReadOutcome.Unreadable, "got $outcome")
    }
}

class TargetKeyTest {

    @Test
    fun `a native target is named from the attribute Gradle matches on`() {
        assertEquals("iosArm64", TargetKey("native", "ios_arm64", null).name)
        assertEquals("wasmJs", TargetKey("wasm", null, "js").name)
        assertEquals("jvm", TargetKey("jvm", null, null).name)
        assertEquals("common", TargetKey("common", null, null).name)
    }

    @Test
    fun `the konan target name is not always the name a Kotlin build uses`() {
        // android_arm32 is published as androidNativeArm32 by the Kotlin plugin, so the derived name
        // and the build's name differ here. Pinned rather than fixed: the attribute is the identity,
        // the name is a convenience, and a table of every konan target would drift out of date on its
        // own. The coordinate printed beside the name is what disambiguates.
        assertEquals("androidArm32", TargetKey("native", "android_arm32", null).name)
    }

    @Test
    fun `a publication with no platform attribute is a plain jvm one`() {
        assertEquals("jvm", TargetKey(null, null, null).name)
    }
}

class DocumentationVariantTest {

    @Test
    fun `sources with no platform of their own belong to the target there is`() = runTest {
        // Gradle's Java plugin publishes sourcesElements with no Kotlin platform attribute while
        // apiElements beside it has one. Grouping by attributes alone therefore split this
        // publication into two targets, both called jvm — one with the code, one with the sources —
        // and asking the first for its sources answered "none" about a library that publishes them.
        val outcome = PublicationReader(Fixtures.serving("kompot-client-tck-0.28.0.53.module"))
            .read(Coordinate("io.github.youndie", "kompot-client-tck", "0.28.0.53"), Fixtures.Repository)

        val publication = (outcome as ReadOutcome.Read).publication

        assertEquals(listOf("jvm"), publication.targets.map { it.name })
        assertNotNull(publication.targets.single().sourcesVariant, "the sources variant went missing")
        assertNotNull(publication.targets.single().apiVariant, "and the api variant has to still be there")
    }

    @Test
    fun `a multiplatform publication keeps its sources on the target that names them`() = runTest {
        val outcome = PublicationReader(Fixtures.serving(*Fixtures.KompotCoreDocuments))
            .read(Fixtures.KompotCore, Fixtures.Repository)

        val publication = (outcome as ReadOutcome.Read).publication

        // Six targets and no more: nothing was merged that should have stayed apart.
        assertEquals(6, publication.targets.size)
        assertTrue(publication.targets.all { it.sourcesVariant != null }, "every target here publishes sources")
    }
}

class SnapshotLayoutTest {

    /** A repository serving one snapshot: its version-level metadata and the root module it names. */
    private fun snapshotRepository() = Fixtures.answering { path ->
        when {
            path.endsWith("/maven-metadata.xml") ->
                HttpStatusCode.OK to Fixtures.load("s3-client-snapshot-maven-metadata.xml")

            path.endsWith("s3-client-0.1.0-20260817.123924-1.module") ->
                HttpStatusCode.OK to Fixtures.load("s3-client-0.1.0-20260817.123924-1.module")

            else -> HttpStatusCode.NotFound to ""
        }
    }

    @Test
    fun `a snapshot is read through the timestamp its metadata names`() = runTest {
        // Asking for `s3-client-0.1.0-SNAPSHOT.module` gets a 404 from every repository on earth: the
        // file is called `s3-client-0.1.0-20260817.123924-1.module`, and which timestamp is current is
        // written in a second metadata document. A reader assuming the release layout calls a
        // published version absent, and does it with confidence — which is the failure this whole
        // tool exists to catch, so making it here was worse than embarrassing.
        val outcome = PublicationReader(snapshotRepository())
            .read(Coordinate("io.github.youndie", "s3-client", "0.1.0-SNAPSHOT"), Fixtures.Repository)

        val publication = (outcome as? ReadOutcome.Read)?.publication ?: fail("not read: $outcome")
        assertTrue(publication.targets.isNotEmpty())
    }

    @Test
    fun `the files of a snapshot are named the way they are actually stored`() = runTest {
        val outcome = PublicationReader(snapshotRepository())
            .read(Coordinate("io.github.youndie", "s3-client", "0.1.0-SNAPSHOT"), Fixtures.Repository)

        val urls = (outcome as ReadOutcome.Read).publication.targets.flatMap { it.variants }.flatMap { it.files }.map { it.url }

        assertTrue(urls.isNotEmpty(), "no files at all")
        // Corrected in the reader rather than in every caller: the metadata says -SNAPSHOT and the
        // disk says the timestamp, and anything fetching by the first reads a 404 as a missing file.
        assertTrue(urls.none { it.contains("-SNAPSHOT") }, "still asking for a name nothing is stored under: $urls")
        assertTrue(urls.all { it.contains("20260817.123924-1") }, urls.toString())
    }
}
