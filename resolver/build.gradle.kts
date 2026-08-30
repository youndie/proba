plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    id("ru.workinprogress.sborka.jvm")
    id("ru.workinprogress.sborka.lint")
}

dependencies {
    api(project(":checks"))
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.kotlin.metadata.jvm)
    implementation(libs.ktor.client.cio)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

application { mainClass.set("dev.youndie.proba.resolver.MainKt") }

sourceSets.test { resources.srcDir(rootProject.file("fixtures")) }

tasks.named<JavaExec>("run") { isIgnoreExitValue = true }
