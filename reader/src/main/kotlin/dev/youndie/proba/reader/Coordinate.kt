package dev.youndie.proba.reader

/** A Maven coordinate, and the paths a repository keeps its files under. */
data class Coordinate(val group: String, val artifact: String, val version: String) {

    val directory: String get() = "${group.replace('.', '/')}/$artifact/$version"

    /** The file a publication of this coordinate is expected to carry, by extension. */
    fun file(extension: String): String = "$directory/$artifact-$version.$extension"

    /** A snapshot is addressed differently from a release; see ReadOutcome.UnsupportedLayout. */
    val isSnapshot: Boolean get() = version.endsWith("-SNAPSHOT")

    override fun toString(): String = "$group:$artifact:$version"

    companion object {
        fun parse(text: String): Coordinate {
            val parts = text.split(':')
            require(parts.size == 3 && parts.none { it.isBlank() }) {
                "a coordinate is group:artifact:version, got \"$text\""
            }
            return Coordinate(parts[0].trim(), parts[1].trim(), parts[2].trim())
        }
    }
}

/** A repository, addressed the way a consumer's build addresses it: a base URL and nothing else. */
data class MavenRepository(val name: String, val baseUrl: String) {

    fun url(path: String): String = "${baseUrl.trimEnd('/')}/$path"

    companion object {
        val MavenCentral = MavenRepository("Maven Central", "https://repo1.maven.org/maven2")
    }
}

/** The repository a build publishes into before anything leaves the machine. */
fun mavenLocal(): MavenRepository =
    MavenRepository("mavenLocal", java.io.File(System.getProperty("user.home"), ".m2/repository").toURI().toString())
