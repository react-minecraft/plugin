rootProject.name = "react"

include(
    "api",
    "common",
    "bukkit",
    "paper",
    "folia"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
