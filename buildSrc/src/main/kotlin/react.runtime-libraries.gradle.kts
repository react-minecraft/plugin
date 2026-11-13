import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    java
}

val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
val libraryGroups = mapOf(
    "core" to listOf(
        "adventure-minimessage", "configurate-yaml", "cloud-paper", "textserializer-legacy", "actions",
        "schedula-common", if (project.name == "folia") "schedula-folia" else "schedula-bukkit"
    ),
    "database-sqlite" to listOf("hikari", "sqlite"),
    "database-h2" to listOf("hikari", "h2"),
    "database-mysql" to listOf("hikari", "mysql"),
    "database-mongodb" to listOf("mongodb")
)
val generated = layout.buildDirectory.dir("generated/react-runtime-libraries")
val locks = tasks.register("generateReactLibraryLocks")

libraryGroups.forEach { (groupName, aliases) ->
    val libraries = configurations.create("reactLibraries${groupName.replace("-", "_")}") {
        isCanBeConsumed = false
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    aliases.forEach { alias ->
        val dependency = dependencies.create(catalog.findLibrary(alias).get().get().toString()) as ExternalModuleDependency
        if (alias == "adventure-minimessage") {
            dependency.isTransitive = false
        }
        dependencies.add(libraries.name, dependency)
    }
    val jars = libraries.incoming.artifactView {
        attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
    }.artifacts
    val entries = jars.resolvedArtifacts.map { artifacts ->
        artifacts.map { artifact ->
            val component = artifact.id.componentIdentifier as ModuleComponentIdentifier
            val base = if (component.group.startsWith("com.github.g4vrk")) "https://jitpack.io" else "https://repo.maven.apache.org/maven2"
            val url = "$base/${component.group.replace('.', '/')}/${component.module}/${component.version}/${artifact.file.name}"
            val digest = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes())
            "${HexFormat.of().formatHex(digest)} $url"
        }.sorted()
    }
    val lock = generated.map { it.file("META-INF/react-libraries/$groupName.lock") }
    val generate = tasks.register("generateReactLibraryLock${groupName.replace("-", "_")}") {
        inputs.files(jars.artifactFiles)
        inputs.property("entries", entries)
        outputs.file(lock)
        doLast {
            val file = lock.get().asFile
            file.parentFile.mkdirs()
            file.writeText(entries.get().joinToString("\n", postfix = "\n"))
        }
    }
    locks.configure { dependsOn(generate) }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(locks)
    from(generated)
}
