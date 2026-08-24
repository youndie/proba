package dev.youndie.proba.checks

import dev.youndie.proba.reader.Coordinate
import java.io.File

/**
 * A consumer build that already happened, kept as data.
 *
 * What this holds is the check's decision — absence on the compile classpath becomes a defect — and
 * nothing else. Reading a public API out of bytecode and getting a classpath out of Gradle are held
 * where they are done, against real jars and a real build; asking one test to stand for all three
 * would leave each of them proved by the other two.
 */
class RecordedConsumer(
    override val target: String,
    override val compileClasspath: List<ResolvedArtifact>,
    private val surface: Map<String, Set<String>>,
    private val present: Set<String>,
) : ConsumerView {

    override val runtimeClasspath: List<ResolvedArtifact> get() = compileClasspath

    override fun apiSurface(artifact: ResolvedArtifact): Set<String> = surface[artifact.file.name].orEmpty()

    override fun onCompileClasspath(className: String): Boolean = className in present

    companion object {
        private val Lib = Coordinate("dev.youndie.proba.sample", "lib", "1.0.0")
        private val Support = Coordinate("dev.youndie.proba.sample", "support", "1.0.0")
        private const val TOKEN = "dev.youndie.proba.sample.support.Token"

        /** What the consumer build of the sample published with `implementation` actually reported. */
        fun withoutSupport() = RecordedConsumer(
            target = "jvm",
            compileClasspath = listOf(ResolvedArtifact(Lib, File("lib-1.0.0.jar"))),
            surface = mapOf("lib-1.0.0.jar" to setOf(TOKEN)),
            present = setOf("dev.youndie.proba.sample.lib.Gate"),
        )

        /**
         * A consumer build that reached everything the public API mentions.
         *
         * The target is named `jvm` because a suspicion is only resolved for the target the build
         * actually ran for; the others were not answered and keep it.
         */
        fun reachingEverything(of: Coordinate, target: String = "jvm"): RecordedConsumer {
            // The artefact has to be the publication under test: a consumer build that resolved
            // somebody else's library answers nothing about this one, and saying so is what
            // ApiUnreachable.reach does when they do not match.
            val file = "${of.artifact}-${of.version}.jar"
            return RecordedConsumer(
                target = target,
                compileClasspath = listOf(ResolvedArtifact(of, File(file))),
                surface = mapOf(file to setOf(TOKEN)),
                present = setOf(TOKEN),
            )
        }

        /** The same, from the version that declares the dependency `api`. */
        fun withSupport() = RecordedConsumer(
            target = "jvm",
            compileClasspath = listOf(
                ResolvedArtifact(Lib, File("lib-1.0.0.jar")),
                ResolvedArtifact(Support, File("support-1.0.0.jar")),
            ),
            surface = mapOf("lib-1.0.0.jar" to setOf(TOKEN)),
            present = setOf("dev.youndie.proba.sample.lib.Gate", TOKEN),
        )
    }
}
