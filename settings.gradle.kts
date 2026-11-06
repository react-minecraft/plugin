rootProject.name = "react"

include(
    "common",
    "paper",
    "folia"
)

// kits and auth are local modules kept out of this repository; a fresh clone builds without them.
listOf("kits", "auth")
    .filter { file("$it/build.gradle.kts").isFile }
    .forEach { include(it) }

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
