import util.expandPluginDescriptor
import util.expandSpongeMetadata

plugins {
    id("java-library")
    id("react.base-conventions")
    id("react.shadow-conventions")
    id("react.runtime-libraries")
}

repositories {
    maven {
        url = uri("https://repo.spongepowered.org/maven/")
    }
}

val packetEventsBundle by configurations.creating {
    isTransitive = true
}

val packetEventsAdventureApi by configurations.creating {
    isTransitive = false
}

dependencies {
    api(project(":common"))

    compileOnly(libs.schedula.sponge)
    compileOnly(libs.cloud.core)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.sponge.api)
    compileOnly(libs.packetevents.sponge)
    add("packetEventsBundle", libs.packetevents.sponge)
    add(packetEventsAdventureApi.name, libs.packetevents.adventure.api)

    testImplementation(libs.sponge.api)
    testImplementation(libs.packetevents.sponge)
    testImplementation(libs.slf4j.api)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    main {
        resources {
            srcDir(project(":common").file("src/main/resources"))
        }
    }
}

tasks {
    named<ProcessResources>("processResources") {
        expandPluginDescriptor(rootProject.version.toString(), "com.g4vrk.react.sponge.SpongePlugin")
        expandSpongeMetadata(rootProject.version.toString())
    }

    named<Jar>("jar") {
        enabled = false
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        from({ packetEventsAdventureApi.files.map { zipTree(it) } }) {
            include("net/kyori/adventure/text/ObjectComponent*.class")
            include("net/kyori/adventure/text/object/*.class")
            into("META-INF/react-platform")
        }
    }
}
