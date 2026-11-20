package util

import org.gradle.language.jvm.tasks.ProcessResources

fun ProcessResources.expandPluginDescriptor(version: String, mainClass: String) {
    val values = mapOf("version" to version, "mainClass" to mainClass)
    inputs.properties(values)
    filesMatching("plugin.yml") {
        expand(values)
    }
}
