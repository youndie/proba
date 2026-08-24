package dev.youndie.proba.resolver

import dev.youndie.proba.checks.ResolvedArtifact
import dev.youndie.proba.reader.Coordinate
import dev.youndie.proba.reader.MavenRepository
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs a real consumer build and reports what it received.
 *
 * Gradle is run rather than re-implemented on purpose. Resolving variants by their attributes is not
 * reading JSON, it is the very set of rules whose outcome is the question — a second implementation
 * would only ever prove that two copies of my understanding agree with each other.
 */
class GradleConsumerResolver(
    private val workspace: File,
    /** A directory holding `gradlew` and `gradle/wrapper` — the distribution the consumer build uses. */
    private val wrapperSource: File,
    private val timeoutSeconds: Long = 180,
    private val maxHeap: String = "1g",
) {

    fun resolve(coordinate: Coordinate, repository: MavenRepository): ResolutionOutcome {
        val project = File(workspace, "consumer").apply { mkdirs() }
        copyWrapper(project)
        write(project, coordinate, repository)

        val gradleHome = File(workspace, "gradle-home").apply { mkdirs() }
        val process = ProcessBuilder(
            File(project, "gradlew").absolutePath,
            "--quiet", "--console=plain", "--no-configuration-cache", "--max-workers=2",
            "-g", gradleHome.absolutePath,
            "-Dorg.gradle.jvmargs=-Xmx$maxHeap",
            "probaClasspath",
        )
            .directory(project)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader()
        val lines = mutableListOf<String>()
        val reader = Thread { output.forEachLine { lines += it } }.apply { isDaemon = true; start() }

        // The bound is on the process and not inside the build: a build that wedges does not reach
        // any timeout it was asked to honour, which is exactly when a bound is needed.
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ResolutionOutcome.Failed("the consumer build did not finish in ${timeoutSeconds}s", lines.joinToString("\n"))
        }
        reader.join(5_000)

        if (process.exitValue() != 0) {
            return ResolutionOutcome.Failed("the consumer build failed (exit ${process.exitValue()})", lines.joinToString("\n"))
        }

        val compile = lines.mapNotNull { it.parse("COMPILE") }
        val runtime = lines.mapNotNull { it.parse("RUNTIME") }
        if (compile.isEmpty()) {
            return ResolutionOutcome.Failed("the consumer build reported no compile classpath", lines.joinToString("\n"))
        }
        return ResolutionOutcome.Resolved(ResolvedConsumerView("jvm", compile, runtime))
    }

    private fun String.parse(tag: String): ResolvedArtifact? {
        val parts = split('\t')
        if (parts.size != 3 || parts[0] != tag) return null
        val coordinate = parts[1].split(':').takeIf { it.size == 3 }?.let { Coordinate(it[0], it[1], it[2]) }
        return ResolvedArtifact(coordinate, File(parts[2]))
    }

    private fun copyWrapper(project: File) {
        File(wrapperSource, "gradlew").copyTo(File(project, "gradlew"), overwrite = true).setExecutable(true)
        File(wrapperSource, "gradle/wrapper").copyRecursively(File(project, "gradle/wrapper"), overwrite = true)
    }

    private fun write(project: File, coordinate: Coordinate, repository: MavenRepository) {
        File(project, "settings.gradle.kts").writeText(
            """
            rootProject.name = "proba-consumer"
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri("${repository.baseUrl}") }
                    mavenCentral()
                }
            }
            """.trimIndent(),
        )
        File(project, "build.gradle.kts").writeText(
            """
            plugins { `java-library` }

            dependencies { implementation("$coordinate") }

            // Reported per component rather than per file: knowing which jar is the module being asked
            // about is the whole difference between reading its public API and reading a dependency's.
            tasks.register("probaClasspath") {
                val compile = configurations.named("compileClasspath")
                val runtime = configurations.named("runtimeClasspath")
                doLast {
                    compile.get().incoming.artifacts.artifacts.forEach {
                        println("COMPILE\t" + it.id.componentIdentifier + "\t" + it.file.absolutePath)
                    }
                    runtime.get().incoming.artifacts.artifacts.forEach {
                        println("RUNTIME\t" + it.id.componentIdentifier + "\t" + it.file.absolutePath)
                    }
                }
            }
            """.trimIndent(),
        )
    }
}

sealed interface ResolutionOutcome {
    data class Resolved(val view: ResolvedConsumerView) : ResolutionOutcome
    data class Failed(val reason: String, val output: String) : ResolutionOutcome
}
