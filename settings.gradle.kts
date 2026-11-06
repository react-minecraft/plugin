rootProject.name = "react"

include(
    "api",
    "common",
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
