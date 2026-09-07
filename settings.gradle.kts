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
                // Both groups on purpose. The portfolio is moving to `io.github.youndie` and sborka
                // is already there — the plugin marker and the jar behind it are under the new one.
                // The old one is held by the library versions published before the move: they are
                // still on the server and resolve as before.
                includeGroupByRegex("io\\.github\\.youndie.*")
                includeGroupByRegex("ru\\.workinprogress.*")
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
