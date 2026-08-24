import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":resolver"))

    // The platform, so kompot-core cannot resolve beside a kompot-standard from another publish.
    implementation(platform(libs.kompot.bom))
    implementation(libs.kompot.core)
    implementation(libs.kompot.standard)
    implementation(libs.kompot.ktor)

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

// 25, and not by choice: kompot publishes class file version 69, so nothing older can even load it.
// Its metadata does not say so — no org.gradle.jvm.version anywhere — which is why the refusal
// arrives as UnsupportedClassVersionError at class loading rather than as an unresolvable dependency.
kotlin { jvmToolchain(25) }

application { mainClass.set("dev.youndie.proba.server.MainKt") }

tasks.test { useJUnitPlatform() }

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
                appendLine("enum class SeverityLook(val word: String, val shape: String, val color: ColorToken, val surface: ColorToken) {")
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
