import util.expandPluginDescriptor

plugins {
    id("java")
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

    compileOnly(libs.paper.api)

    compileOnly(libs.schedula.bukkit)

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

        expandPluginDescriptor(rootProject.version.toString(), "com.g4vrk.react.paper.PaperPlugin")
    }

    named<Jar>("jar") {
        enabled = false
    }

}
