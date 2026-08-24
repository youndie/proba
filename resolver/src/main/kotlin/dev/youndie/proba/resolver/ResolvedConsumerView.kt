package dev.youndie.proba.resolver

import dev.youndie.proba.checks.ConsumerView
import dev.youndie.proba.checks.ResolvedArtifact
import java.util.jar.JarFile

/** A [ConsumerView] backed by an actual resolution and the actual artefacts it produced. */
class ResolvedConsumerView(
    override val target: String,
    override val compileClasspath: List<ResolvedArtifact>,
    override val runtimeClasspath: List<ResolvedArtifact>,
) : ConsumerView {

    private val onClasspath: Set<String> by lazy {
        buildSet {
            compileClasspath.forEach { artefact ->
                if (!artefact.file.isFile) return@forEach
                runCatching {
                    JarFile(artefact.file).use { archive ->
                        archive.entries().asSequence()
                            .filter { it.name.endsWith(".class") }
                            .forEach { add(it.name.removeSuffix(".class").replace('/', '.')) }
                    }
                }
            }
        }
    }

    override fun apiSurface(artifact: ResolvedArtifact): Set<String> = JarApiSurface.of(artifact.file)

    override fun toolKind(artifact: ResolvedArtifact): String? = ArtefactKind.of(artifact.file)

    override fun onCompileClasspath(className: String): Boolean = className in onClasspath
}
