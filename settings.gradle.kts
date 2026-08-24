rootProject.name = "proba"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // kompot publishes here. Filtered to its own group so an outage of this host cannot make a
        // dependency from Maven Central look unresolvable.
        maven("https://reposilite.kotlin.website/snapshots") {
            mavenContent { includeGroup("io.github.youndie") }
        }
    }
}

include(":reader")
include(":checks")
include(":resolver")
include(":server")
