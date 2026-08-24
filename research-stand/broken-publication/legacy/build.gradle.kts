// Published against Java 8 on purpose, to be the other half of a pair.
//
// A check about the Java a publication requires needs a real artefact on each side: one that excludes
// consumers and one that excludes nobody. Both come out of this stand, so neither can drift into
// being something else.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
