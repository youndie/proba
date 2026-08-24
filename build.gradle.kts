plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    group = "dev.youndie.proba"
    version = providers.gradleProperty("proba.version").getOrElse("0.1.0")
}
