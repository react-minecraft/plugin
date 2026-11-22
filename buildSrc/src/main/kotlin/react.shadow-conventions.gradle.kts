import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

apply(plugin = "com.gradleup.shadow")

tasks.named<ShadowJar>("shadowJar") {

    archiveFileName.set(
        "${rootProject.name}-${project.name}-${rootProject.version}.jar"
    )

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    mergeServiceFiles()

    val packetEvents = project.configurations.findByName("packetEventsBundle") ?: return@named
    from({ packetEvents.files.map { zipTree(it) } }) {
        into("META-INF/react-platform")
    }

}