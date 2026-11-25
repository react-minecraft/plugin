import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.VersionCatalogsExtension

apply(plugin = "com.gradleup.shadow")

val platformLibraries = if (project.path.startsWith(":platform:")) {
    val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    configurations.create("reactPlatformLibraries") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }.also { configuration ->
        dependencies.add(
            configuration.name,
            catalog.findLibrary("adventure-minimessage").get().get()
        )
    }
} else {
    null
}

tasks.named<ShadowJar>("shadowJar") {

    archiveFileName.set(
        "${rootProject.name}-${project.name}-${rootProject.version}.jar"
    )

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    mergeServiceFiles()

    if (project.path == ":platform:bukkit" || project.path == ":platform:folia") {
        relocate("org.bstats", "com.g4vrk.react.bukkit.metrics")
    }

    val packetEvents = project.configurations.findByName("packetEventsBundle") ?: return@named
    from({ packetEvents.files.map { zipTree(it) } }) {
        into("META-INF/react-platform")
    }

    platformLibraries?.let { libraries ->
        from({ libraries.files.map { zipTree(it) } }) {
            into("META-INF/react-platform")
        }
    }

}
