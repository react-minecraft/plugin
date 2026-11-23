import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer
import util.BundlePacketEventsFabricTask
import util.expandFabricMetadata

plugins {
    alias(libs.plugins.fabric.loom)
    id("react.base-conventions")
    id("react.shadow-conventions")
    id("react.runtime-libraries")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.fabric.minecraft.get()}")
    compileOnly(libs.fabric.loader)
    compileOnly(libs.fabric.entity.events)
    compileOnly(libs.fabric.lifecycle.events)
    compileOnly(libs.packetevents.api.fabric)
    compileOnly(libs.packetevents.fabric)
    compileOnly(libs.fabric.adventure.platform)

    api(project(":common"))

    compileOnly(libs.fabric.adventure.api)
    compileOnly(libs.fabric.adventure.legacy)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.cloud.core)
    compileOnly(libs.schedula.common)
    compileOnly(libs.schedula.fabric)
    compileOnly(libs.textserializer.legacy)
    compileOnly(libs.actions)
    compileOnly(libs.jetbrains.annotations)
    compileOnly("com.google.guava:guava:31.1-jre")
    compileOnly("com.google.code.gson:gson:2.8.9")
    compileOnly(libs.fastutil)
    compileOnly(libs.hikari)
    compileOnly(libs.mongodb)

}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

sourceSets {
    main {
        resources {
            srcDir(project(":common").file("src/main/resources"))
        }
    }
}

val commonOutput = project(":common").extensions.getByType<SourceSetContainer>().named("main").map { it.output }
val apiOutput = project(":api").extensions.getByType<SourceSetContainer>().named("main").map { it.output }
val reactVersion = project.version.toString()

tasks.named<ProcessResources>("processResources") {
    expandFabricMetadata(reactVersion)
}

tasks.named<Jar>("jar") {
    enabled = false
}

val includedFabricMods = configurations.create("shadowFabricMods") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val packetEventsFabricRoot = configurations.create("packetEventsFabricRoot") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val packetEventsAdventureSerializers = configurations.create("packetEventsAdventureSerializers") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val fabricAdventureLegacy = configurations.create("fabricAdventureLegacy") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    add(includedFabricMods.name, libs.fabric.adventure.platform)
    add(packetEventsFabricRoot.name, libs.packetevents.fabric)
    add(packetEventsAdventureSerializers.name, libs.packetevents.adventure.gson)
    add(packetEventsAdventureSerializers.name, libs.packetevents.adventure.json)
    add(packetEventsAdventureSerializers.name, libs.packetevents.adventure.commons)
    add(packetEventsAdventureSerializers.name, libs.packetevents.adventure.option)
    add(fabricAdventureLegacy.name, libs.fabric.adventure.legacy)
}

val packetEventsWithAdventureSerializers = tasks.register<BundlePacketEventsFabricTask>("packetEventsWithAdventureSerializers") {
    inputJars.from(packetEventsFabricRoot, packetEventsAdventureSerializers)
    outputJar.set(layout.buildDirectory.file("generated/packet-events/packetevents-fabric-2.14.0-react.jar"))
}
tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(fabricAdventureLegacy)
    from(commonOutput)
    from(apiOutput)
    from(includedFabricMods) {
        into("META-INF/jars")
    }
    from(packetEventsWithAdventureSerializers.flatMap { it.outputJar }) {
        into("META-INF/jars")
    }
}
