plugins { `java-library` }

val coordinate: String = (findProperty("coordinate") as String?)
    ?: error("usage: -Pcoordinate=group:artifact:version")

dependencies { implementation(coordinate) }

// What a consumer's compile classpath actually receives, as opposed to what the build declares.
tasks.register("reportCompileClasspath") {
    val files = configurations.named("compileClasspath")
    doLast {
        files.get().files.sortedBy { it.name }.forEach { println("CP ${it.name}") }
    }
}
