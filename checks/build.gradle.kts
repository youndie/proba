plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    api(project(":reader"))
    implementation(libs.ktor.client.cio)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":reader")))
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dev.youndie.proba.checks.MainKt") }

tasks.test { useJUnitPlatform() }

// The run reports its own outcome; letting Gradle bury it under a stack trace defeats the point.
tasks.named<JavaExec>("run") { isIgnoreExitValue = true }

sourceSets.test { resources.srcDir(rootProject.file("fixtures")) }
