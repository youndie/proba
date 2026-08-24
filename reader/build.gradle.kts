plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    testFixturesApi(libs.ktor.client.mock)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dev.youndie.proba.reader.MainKt") }

tasks.test { useJUnitPlatform() }

// The CLI reports its own outcome and uses the exit code to say which one. Letting Gradle turn a
// refusal into a build failure buries the report under a stack trace, which is the opposite of the
// point: the run exists to be read.
tasks.named<JavaExec>("run") { isIgnoreExitValue = true }

// The fixtures are shared with the checks: both modules stand on the same recorded publications, so
// a document cannot be true for one of them and stale for the other.
sourceSets.test { resources.srcDir(rootProject.file("fixtures")) }
sourceSets.testFixtures { resources.srcDir(rootProject.file("fixtures")) }
