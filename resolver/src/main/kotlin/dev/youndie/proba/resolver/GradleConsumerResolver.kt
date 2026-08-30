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
    private val timeoutSeconds: Long = 300,
    private val maxHeap: String = "1g",
    /**
     * Where the consumer build keeps its caches, or null for whatever the environment already uses.
     *
     * Null by default, and that is the important half. Forcing a home of our own bounds the disk a
     * long-running service spends — but on a CI runner it also bypasses the cache the runner just
     * restored, so every run downloads the Gradle distribution again and the first one timed out at
     * three minutes. A service passes a directory; a one-shot run should not.
     */
    private val gradleHome: File? = null,
    /**
     * Repositories the modelled consumer declares beside the one under test.
     *
     * Google's is here by default because every Compose Multiplatform publication depends on
     * `compose.ui`, which depends on `androidx.lifecycle` and `androidx.savedstate`, which live only
     * there — so without it this tier could never run on any of that family and answered
     * "undetermined" for a reason that was about the harness rather than about the library.
     *
     * Each repository is an assumption about who the consumer is, which is why they are a parameter
     * and not a literal: a caller checking a library whose consumers have neither can say so.
     */
    private val consumerRepositories: List<String> =
        listOf(
            "https://repo1.maven.org/maven2",
            "https://dl.google.com/dl/android/maven2",
        ),
) {
    fun resolve(
        coordinate: Coordinate,
        repository: MavenRepository,
    ): ResolutionOutcome {
        val project = File(workspace, "consumer").apply { mkdirs() }
        copyWrapper(project)
        write(project, coordinate, repository)

        val command =
            buildList {
                add(File(project, "gradlew").absolutePath)
                addAll(listOf("--quiet", "--console=plain", "--no-configuration-cache", "--max-workers=2"))
                gradleHome?.let { addAll(listOf("-g", it.apply { mkdirs() }.absolutePath)) }
                add("-Dorg.gradle.jvmargs=-Xmx$maxHeap")
                add("probaClasspath")
            }
        val process =
            ProcessBuilder(command)
                .directory(project)
                .redirectErrorStream(true)
                .start()

        val output = process.inputStream.bufferedReader()
        val lines = mutableListOf<String>()
        val reader =
            Thread { output.forEachLine { lines += it } }.apply {
                isDaemon = true
                start()
            }

        // The bound is on the process and not inside the build: a build that wedges does not reach
        // any timeout it was asked to honour, which is exactly when a bound is needed.
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ResolutionOutcome.Failed(
                "the consumer build did not finish in ${timeoutSeconds}s",
                lines.joinToString("\n"),
            )
        }
        reader.join(5_000)

        if (process.exitValue() != 0) {
            return ResolutionOutcome.Failed(
                "the consumer build failed (exit ${process.exitValue()})",
                lines.joinToString("\n"),
            )
        }

        val compile = lines.mapNotNull { it.parse("COMPILE") }
        val runtime = lines.mapNotNull { it.parse("RUNTIME") }
        if (compile.isEmpty()) {
            return ResolutionOutcome.Failed(
                "the consumer build reported no compile classpath",
                lines.joinToString("\n"),
            )
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

    private fun write(
        project: File,
        coordinate: Coordinate,
        repository: MavenRepository,
    ) {
        val declarations =
            (listOf(repository.baseUrl) + consumerRepositories)
                .distinct()
                .joinToString("\n") { url -> "                    maven { url = uri(\"" + url + "\") }" }

        File(project, "settings.gradle.kts").writeText(
            """
            rootProject.name = "proba-consumer"
            dependencyResolutionManagement {
                repositories {
$declarations
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
    data class Resolved(
        val view: ResolvedConsumerView,
    ) : ResolutionOutcome

    data class Failed(
        val reason: String,
        val output: String,
    ) : ResolutionOutcome {
        /** The reason, as the failure itself states it. See [causeOf]. */
        val cause: List<String> get() = causeOf(output)
    }
}

/**
 * The part of a failed build that says what went wrong.
 *
 * Gradle ends every failure with the same four lines of advice and `BUILD FAILED`, so a report that
 * keeps the tail keeps the boilerplate and drops the cause. From those four lines a reader cannot
 * tell an unresolvable library — which would be a defect in it, and the most valuable thing this
 * tool could say — from a repository this harness failed to declare, from a network that was down.
 * Three different actions, and none of them supported.
 *
 * Gradle marks the cause itself, between `* What went wrong:` and `* Try:`. Bounded, because it
 * prints one such block, and capped anyway so a pathological failure cannot become the report.
 */
fun causeOf(
    output: String,
    limit: Int = 24,
): List<String> {
    val lines = output.lines()
    val start = lines.indexOfFirst { it.trimStart().startsWith("* What went wrong:") }
    if (start < 0) {
        // No marked cause: keep the head rather than the tail, which is where a stack trace or a
        // toolchain complaint says its piece before Gradle's closing advice.
        return lines.filter { it.isNotBlank() }.take(limit)
    }
    val end = lines.drop(start + 1).indexOfFirst { it.trimStart().startsWith("* Try:") }
    val block = if (end < 0) lines.drop(start + 1) else lines.subList(start + 1, start + 1 + end)
    return block.filter { it.isNotBlank() }.take(limit)
}
