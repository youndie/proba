plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dev.youndie.proba.reader.MainKt") }

tasks.test { useJUnitPlatform() }

// The CLI reports its own outcome and uses the exit code to say which one. Letting Gradle turn a
// refusal into a build failure buries the report under a stack trace, which is the opposite of the
// point: the run exists to be read.
tasks.named<JavaExec>("run") { isIgnoreExitValue = true }
