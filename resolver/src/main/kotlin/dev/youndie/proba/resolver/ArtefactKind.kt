package dev.youndie.proba.resolver

import java.io.File
import java.util.jar.JarFile

/**
 * Whether an artefact is one a tool loads rather than one a compiler resolves.
 *
 * Read out of what the jar declares about itself rather than guessed from its name or its package:
 * a service file and a plugin descriptor are how these artefacts announce what loads them, and they
 * are there precisely so a tool can find them.
 */
object ArtefactKind {

    private val services = mapOf(
        "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider" to "a KSP symbol processor",
        "META-INF/services/javax.annotation.processing.Processor" to "an annotation processor",
    )

    fun of(jar: File): String? {
        if (!jar.isFile) return null
        return runCatching {
            JarFile(jar).use { archive ->
                val names = archive.entries().asSequence().map { it.name }.toList()
                services.entries.firstOrNull { it.key in names }?.value
                    ?: names.firstOrNull { it.startsWith("META-INF/gradle-plugins/") && it.endsWith(".properties") }
                        ?.let { "a Gradle plugin" }
            }
        }.getOrNull()
    }
}
