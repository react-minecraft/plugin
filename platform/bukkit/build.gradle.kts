import util.expandPluginDescriptor

plugins {
    id("java-library")
    id("react.base-conventions")
    id("react.shadow-conventions")
    id("react.runtime-libraries")
}

repositories {
    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }

    maven {
        url = uri("https://libraries.minecraft.net/")
        content {
            includeModule("com.mojang", "brigadier")
        }
    }

    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

val packetEventsBundle by configurations.creating {
    isTransitive = true
}

dependencies {

    api(project(":common"))

    compileOnly(libs.spigot.api)
    compileOnly(libs.adventure.api)
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.10.0")
    compileOnly(libs.slf4j.api)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.packetevents)
    compileOnly(libs.cloud.paper)
    compileOnly(libs.brigadier)
    compileOnly(libs.xseries)
    compileOnly(libs.schedula.bukkit)
    add("packetEventsBundle", libs.packetevents)
    add("packetEventsBundle", libs.adventure.api)
    add("packetEventsBundle", "net.kyori:adventure-text-serializer-legacy:4.10.0")

    testImplementation(libs.spigot.api)
    testImplementation(libs.adventure.api)
    testImplementation("net.kyori:adventure-text-serializer-legacy:4.10.0")

}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(16)
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
        expandPluginDescriptor(rootProject.version.toString(), "com.g4vrk.react.bukkit.SpigotPlugin")
    }

    named<Jar>("jar") {
        enabled = false
    }

}
