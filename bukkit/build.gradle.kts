plugins {
    id("java-library")
    id("react.base-conventions")
}

repositories {
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

dependencies {

    api(project(":common"))

    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.packetevents)
    compileOnly(libs.cloud.paper)
    compileOnly(libs.brigadier)
    compileOnly(libs.xseries)
    compileOnly(libs.schedula.common)

}
