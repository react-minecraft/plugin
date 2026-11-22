rootProject.name = "react"

include(
    "api",
    "common",
    "platform:bukkit",
    "platform:paper",
    "platform:folia"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
