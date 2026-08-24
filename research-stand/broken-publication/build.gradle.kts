// A library published with the defect on purpose, so a check written to find it has something it is
// known to have to find. Without this, "the check reported nothing" and "the check does not work"
// arrive in the same shape.
plugins {
    kotlin("jvm") version "2.4.10" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    group = "dev.youndie.proba.sample"
    version = providers.gradleProperty("sampleVersion").getOrElse("1.0.0")

    extensions.configure<PublishingExtension> {
        publications { create<MavenPublication>("maven") { from(components["java"]) } }
    }
}
