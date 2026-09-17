plugins {
    alias(wip.plugins.kotlinJvm) apply false
    alias(wip.plugins.kotlinSerialization) apply false
    // The build conventions, declared here and applied per module: the coordinate, the version, the
    // toolchain, the JUnit platform, the style, and the check that every declared @Test ran.
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaLint) apply false
}

// The `subprojects { }` block that set the group and the version is gone: it configured projects
// from outside, and both numbers are one line each in `gradle.properties` now.
