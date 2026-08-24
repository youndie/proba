plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

kotlin { jvmToolchain(21) }

application { mainClass.set("dev.youndie.proba.resolver.MainKt") }

tasks.test { useJUnitPlatform() }

sourceSets.test { resources.srcDir(rootProject.file("fixtures")) }

tasks.named<JavaExec>("run") { isIgnoreExitValue = true }
