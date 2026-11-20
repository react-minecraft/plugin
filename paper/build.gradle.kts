import util.expandPluginDescriptor

plugins {
    id("java")
    id("react.base-conventions")
    id("react.shadow-conventions")
    id("react.runtime-libraries")
}

dependencies {

    implementation(project(":common"))

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

        expandPluginDescriptor(rootProject.version.toString(), "com.g4vrk.react.paper.PaperReactPlugin")
    }

    named<Jar>("jar") {
        enabled = false
    }

}
