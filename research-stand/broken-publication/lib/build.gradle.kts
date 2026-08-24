// The defect, and its absence, from one stand: the public API below hands out a Token, so declaring
// the dependency `implementation` tells a consumer they need nothing but the standard library.
//
//   ./gradlew publishToMavenLocal -PsampleVersion=1.0.0            → the defect
//   ./gradlew publishToMavenLocal -PsampleVersion=1.0.1 -Pfixed    → the same library, correct
//
// One switch, so the pair cannot drift apart into two different libraries.
dependencies {
    if (providers.gradleProperty("fixed").isPresent) {
        api(project(":support"))
    } else {
        implementation(project(":support"))
    }
}
