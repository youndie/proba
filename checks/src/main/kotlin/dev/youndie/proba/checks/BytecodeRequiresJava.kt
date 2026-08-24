package dev.youndie.proba.checks

import dev.youndie.proba.reader.Target
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * The Java a consumer must be running, and whether anything says so.
 *
 * A class file carries the version of the JVM that can load it, and a publication carries no
 * obligation to mention it. When the two are far apart the failure is as late and as uninformative
 * as failures get: resolution succeeds, compilation succeeds — a newer class file is readable by a
 * compiler targeting an older release — and the consumer meets
 *
 *     java.lang.UnsupportedClassVersionError: … class file version 69.0,
 *     this version of the Java Runtime only recognizes class file versions up to 65.0
 *
 * at class loading, in whatever they were running at the time, with a message about numbers and
 * nothing about the library.
 *
 * A publisher never sees it: their own toolchain is the one that produced the bytecode.
 */
object BytecodeRequiresJava : Check {

    override val id = "bytecode-java-version"
    override val title = "the Java the published classes require is one a consumer can find out about"

    /**
     * Java 8. Below this nothing is excluded and silence in the metadata harms nobody — which is why
     * kotlinx-coroutines and ktor omit the attribute too and are not defective for it.
     */
    private const val BASELINE = 8

    private const val JVM_VERSION = "org.gradle.jvm.version"

    override suspend fun run(context: CheckContext): List<Finding> {
        val artefacts = context.artefacts ?: return emptyList()

        return context.publication.targets
            .filter { it.name == "jvm" }
            .mapNotNull { target -> examine(target, context, artefacts) }
    }

    private suspend fun examine(target: Target, context: CheckContext, artefacts: ArtefactSource): Finding? {
        val variant = target.apiVariant ?: target.runtimeVariant ?: return null
        val jar = variant.files.firstOrNull { it.declaredName.endsWith(".jar") && !it.declaredName.contains("-sources") }
            ?: return null

        val url = context.publication.repository.url("${target.coordinate.directory}/${jar.url}")
        val bytes = artefacts.bytes(url) ?: return undetermined(target, "the jar could not be fetched", url)
        val major = firstClassFileVersion(bytes) ?: return undetermined(target, "no class file was found in it", url)

        val required = major - 44
        if (required <= BASELINE) return null

        val declared = variant.attributes[JVM_VERSION]?.toIntOrNull()
        // Stated is enough. Gradle can then refuse an incompatible consumer at resolution, which is a
        // clear error at the right moment rather than a bytecode number at run time.
        if (declared != null && declared >= required) return null

        return Finding(
            checkId = id,
            severity = Severity.Defect,
            subject = "${target.name}: ${jar.declaredName}",
            message = "the classes require Java $required, and " +
                (if (declared == null) "nothing in the metadata says so" else "the metadata says Java $declared") +
                " — resolution and compilation both succeed for a consumer on anything older, and the " +
                "refusal arrives as UnsupportedClassVersionError at class loading",
            evidence = listOf(
                "class file version $major.0 → Java $required",
                declared?.let { "$JVM_VERSION = $it" } ?: "$JVM_VERSION is not declared",
                url,
            ),
        )
    }

    private fun undetermined(target: Target, reason: String, url: String) = Finding(
        checkId = id,
        severity = Severity.Undetermined,
        subject = target.name,
        message = "the Java version the classes require is not known here: $reason",
        evidence = listOf(url),
    )

    /**
     * The major version out of the first class the jar carries, skipping `META-INF`.
     *
     * Multi-release jars put newer classes under `META-INF/versions/`, and reading one of those would
     * report a floor that only applies to a runtime new enough to look there.
     */
    internal fun firstClassFileVersion(jar: ByteArray): Int? {
        ZipInputStream(ByteArrayInputStream(jar)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.isDirectory || !entry.name.endsWith(".class") || entry.name.startsWith("META-INF")) continue
                val header = ByteArray(8)
                var read = 0
                while (read < 8) {
                    val n = zip.read(header, read, 8 - read)
                    if (n < 0) break
                    read += n
                }
                if (read < 8) return null
                // 0xCAFEBABE, then minor, then major.
                if (header[0] != 0xCA.toByte() || header[1] != 0xFE.toByte()) return null
                return ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
            }
        }
    }
}
