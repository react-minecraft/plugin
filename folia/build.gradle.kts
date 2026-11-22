import util.expandPluginDescriptor

plugins {
    id("java-library")
    id("react.base-conventions")
    id("react.shadow-conventions")
    id("react.runtime-libraries")
}

val packetEventsBundle by configurations.creating {
    isTransitive = true
}

dependencies {

    implementation(project(":bukkit"))
    compileOnly(libs.packetevents)
    add("packetEventsBundle", libs.packetevents)

    compileOnly(libs.folia.api)

    compileOnly(libs.schedula.folia)

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

        expandPluginDescriptor(rootProject.version.toString(), "com.g4vrk.react.folia.FoliaPlugin")
    }

    named<Jar>("jar") {
        enabled = false
    }

}
