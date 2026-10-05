import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.net.URLClassLoader
import java.util.zip.ZipFile

plugins {
    alias(wip.plugins.kotlinJvm)
    alias(wip.plugins.kotlinSerialization)
    application
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
}

dependencies {
    implementation(project(":resolver"))

    // The platform, so kompot-core cannot resolve beside a kompot-standard from another publish.
    implementation(platform(libs.kompot.bom))
    implementation(libs.kompot.core)
    implementation(libs.kompot.standard)
    implementation(libs.kompot.ktor)
    implementation(libs.kompot.forms)
    implementation(libs.form.core)
    implementation(libs.kompot.realtime)
    implementation(libs.kompot.realtime.server)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(testFixtures(project(":reader")))
    testImplementation(libs.kotlinx.coroutines.test)
}

// 21, with its neighbours. It was 25, and not by choice: kompot published class file version 69 and
// said so nowhere. That was reported by this repository's own check, fixed upstream, and 0.29.0.56
// publishes class file 61 with `org.gradle.jvm.version: 17` beside it — so the constraint is gone and
// carrying it forward would only impose proba's floor on whoever uses the action.

application { mainClass.set("dev.youndie.proba.server.MainKt") }

sourceSets.test { resources.srcDir(rootProject.file("fixtures")) }

/**
 * The token names the server is allowed to say, generated from the one file that defines them.
 *
 * The server names tokens and the web client resolves them, so the vocabulary lives in two languages
 * and must not live in two lists: a name typed out again here would go on compiling after the design
 * renamed it, and the screen would quietly lose its colours instead of failing to build.
 */
val generateTokens by tasks.registering {
    val source = rootProject.file("design/tokens.json")
    val outputDir = layout.buildDirectory.dir("generated/tokens")
    inputs.file(source)
    outputs.dir(outputDir)
    doLast {
        val json = groovy.json.JsonSlurper().parse(source) as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val colors = ((json["colors"] as Map<String, *>)["light"] as Map<String, String>).keys.sorted()

        @Suppress("UNCHECKED_CAST")
        val typography = (json["typography"] as Map<String, *>).keys.sorted()

        @Suppress("UNCHECKED_CAST")
        val severity = (json["severity"] as Map<String, *>).filterKeys { !it.startsWith("$") }

        fun konst(name: String) = name.split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) }

        val file = outputDir.get().file("dev/youndie/proba/server/ProbaTokens.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("// Generated from design/tokens.json. Do not edit.")
                appendLine("package dev.youndie.proba.server")
                appendLine()
                appendLine("import io.github.youndie.kompot.ColorToken")
                appendLine("import io.github.youndie.kompot.TypographyToken")
                appendLine()
                appendLine("object Color {")
                colors.forEach { appendLine("    val ${konst(it)} = ColorToken(\"$it\")") }
                appendLine("}")
                appendLine()
                appendLine("object Type {")
                typography.forEach { appendLine("    val ${konst(it)} = TypographyToken(\"$it\")") }
                appendLine("}")
                appendLine()
                appendLine(
                    "enum class SeverityLook(val word: String, val shape: String, val color: ColorToken, val surface: ColorToken) {",
                )
                severity.forEach { (name, value) ->
                    @Suppress("UNCHECKED_CAST")
                    val it = value as Map<String, String>
                    appendLine(
                        "    ${konst(name)}(\"${it["word"]}\", \"${it["shape"]}\", " +
                            "ColorToken(\"${it["color"]}\"), ColorToken(\"${it["surface"]}\")),",
                    )
                }
                appendLine("    ;")
                appendLine("}")
            },
        )
    }
}

kotlin.sourceSets.main { kotlin.srcDir(generateTokens) }

/**
 * The renderer's TypeScript types, printed by kompot's own generator from the schemas in the
 * published `kompot-spec` jar — the version the BOM above pins, so there is no second number to drift.
 *
 * Until kompot 0.38 proba generated them itself with json-schema-to-typescript. Then kompot started
 * printing declarations of its own (kompot#172) — open hierarchies as a union of the known variants
 * plus a branch for a type the reader has never seen (SPEC.md §2.1) — and two generators for one
 * contract is two sources, whose disagreement nobody is placed to notice. So this runs kompot's. The
 * `.d.ts` files are not in the jar, only in kompot's repository; the generator is
 * (`TypeScriptDeclarations`, public API), and so are the schemas it reads, which is everything a
 * published coordinate needs to print the same file: the output equals kompot's `types/kompot.d.ts`
 * at the commit that version was built from, byte for byte, below the first line.
 *
 * Running the generator is the workaround for the files not being in the jar (kompot#208); once they
 * are, the web package fetches `kompot-spec/types/kompot.d.ts` the way it fetches the corpus, and this
 * goes.
 *
 * It lives in this module because this is where the BOM is applied and where the Kotlin plugin picks
 * the JVM variant of a multiplatform dependency; nothing here is on the server's classpath.
 */
val kompotSpec: Configuration =
    configurations.create("kompotSpec") {
        isCanBeConsumed = false
        isCanBeResolved = true
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        }
    }

dependencies {
    kompotSpec(platform(libs.kompot.bom))
    kompotSpec(libs.kompot.spec)
}

val kompotTypesFile = rootProject.file("packages/kompot-web/src/generated/kompot.ts")

/** Renders the open declarations (the reading side's file) out of the resolved kompot-spec jar. */
fun renderKompotTypes(classpath: Set<File>): String {
    val specJar =
        classpath.singleOrNull { it.name.startsWith("kompot-spec-") }
            ?: error("kompot-spec is not on its own classpath: ${classpath.map { it.name }}")
    val version = specJar.name.removePrefix("kompot-spec-").removeSuffix(".jar")
    val schemaNames =
        ZipFile(specJar).use { zip ->
            zip
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith("kompot-spec/schema/") && it.endsWith(".schema.json") }
                .map { it.removePrefix("kompot-spec/schema/") }
                .toList()
        }
    check(schemaNames.isNotEmpty()) { "${specJar.name} carries no schemas" }

    // An isolated loader: Gradle has a kotlinx-serialization of its own, and the generator must run
    // against the one kompot-spec was built with.
    val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
    val loader = URLClassLoader(urls, ClassLoader.getPlatformClassLoader())
    val body =
        loader.use {
            val json: Any = loader.loadClass("kotlinx.serialization.json.Json").getField("Default").get(null)
            val parse = json::class.java.getMethod("parseToJsonElement", String::class.java)
            val documents: Map<String, Any> =
                schemaNames.associateWith { name: String ->
                    val text = loader.getResource("kompot-spec/schema/$name")!!.readText()
                    parse.invoke(json, text)
                }
            val generator = loader.loadClass("io.github.youndie.kompot.spec.TypeScriptDeclarations")
            generator
                .getMethod("render", Map::class.java, Boolean::class.javaPrimitiveType)
                .invoke(generator.getField("INSTANCE").get(null), documents, false) as String
        }
    val provenance =
        "// kompot-spec $version, TypeScriptDeclarations over the schemas in its jar. " +
            "Regenerate: ./gradlew :server:kompotTypes"
    return "$provenance\n$body"
}

tasks.register("kompotTypes") {
    group = "kompot"
    description = "Writes packages/kompot-web/src/generated/kompot.ts from the pinned kompot-spec"
    inputs.files(kompotSpec)
    outputs.file(kompotTypesFile)
    doLast { kompotTypesFile.writeText(renderKompotTypes(kompotSpec.files)) }
}

// In `check`, so the jvm job holds it: the web job has no JVM, and the types are the JVM's output.
val checkKompotTypes =
    tasks.register("checkKompotTypes") {
        group = "verification"
        description = "Fails if packages/kompot-web/src/generated/kompot.ts is not what the pinned kompot-spec prints"
        inputs.files(kompotSpec)
        inputs.file(kompotTypesFile)
        doLast {
            check(kompotTypesFile.readText() == renderKompotTypes(kompotSpec.files)) {
                "${kompotTypesFile.relativeTo(rootDir)} is not what the pinned kompot-spec prints. " +
                    "Run ./gradlew :server:kompotTypes"
            }
        }
    }

tasks.named("check") { dependsOn(checkKompotTypes) }
