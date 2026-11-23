package util

import org.gradle.language.jvm.tasks.ProcessResources

fun ProcessResources.expandPluginDescriptor(version: String, mainClass: String) {
    val values = mapOf("version" to version, "mainClass" to mainClass)
    inputs.properties(values)
    filesMatching("plugin.yml") {
        expand(values)
    }
}

fun ProcessResources.expandFabricMetadata(version: String) {
    val values = mapOf("version" to version)
    inputs.properties(values)
    filesMatching("fabric.mod.json") {
        expand(values)
    }
}

fun ProcessResources.expandSpongeMetadata(version: String) {
    val values = mapOf("version" to version)
    inputs.properties(values)
    filesMatching("META-INF/sponge_plugins.json") {
        expand(values)
    }
}
