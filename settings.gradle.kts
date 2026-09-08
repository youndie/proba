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
            content {
                // One group, and it is the only one there can be. The portfolio's move to
                // `io.github.youndie` is finished: nothing this build resolves is under
                // `ru.workinprogress` any more, and a filter naming a group the server is never asked
                // about reads as a dependency that is still there.
                includeGroupByRegex("io\\.github\\.youndie.*")
            }
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
    id("io.github.youndie.sborka.settings") version "0.3.0.41"
}

include(":reader")
include(":checks")
include(":resolver")
include(":server")
