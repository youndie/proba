plugins {
    alias(wip.plugins.kotlinJvm)
    application
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
}

dependencies {
    api(project(":checks"))
    implementation(libs.asm)
    implementation(libs.asm.tree)
    // Reads the metadata the compiler writes, so its version IS the compiler's — but only
    // this module needs it, so it is named here against `wip` rather than added to the
    // shared catalog: that file admits what going out of step BETWEEN repositories would
    // break, and one consumer has nothing to be out of step with.
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:${wip.versions.kotlin.get()}")
    implementation(libs.ktor.client.cio)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

application { mainClass.set("dev.youndie.proba.resolver.MainKt") }

sourceSets.test { resources.srcDir(rootProject.file("fixtures")) }

tasks.named<JavaExec>("run") { isIgnoreExitValue = true }
