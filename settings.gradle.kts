rootProject.name = "proba"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // The build conventions. Written out by hand, and it has to be: `pluginManagement` is
        // evaluated before any settings plugin is applied — including this one, which is fetched
        // through it. Filtered, like the repository below and for the same reason.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content { includeGroupByRegex("ru\\.workinprogress.*") }
        }
    }
}

plugins {
    // mavenCentral() and the snapshot repository kompot publishes to, both filtered — an outage of
    // one host must not make a dependency from the other look unresolvable, which is what an
    // unfiltered repository buys you. This file declared the same two itself.
    //
    // It also brings the check that this repository's `.editorconfig` is the one the rest of the
    // portfolio uses, which is the other half of pinning the formatter's version.
    id("ru.workinprogress.sborka.settings") version "0.1.0.20"
}

include(":reader")
include(":checks")
include(":resolver")
include(":server")
