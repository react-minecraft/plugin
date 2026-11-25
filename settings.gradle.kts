pluginManagement {

    repositories {

        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()

    }

}

rootProject.name = "react"

include(
    "api",
    "common",
    "platform:bukkit",
    "platform:folia",
    "platform:fabric",
    "platform:minestom",
    "platform:sponge"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
