plugins {
    id("java-library")
    id("react.base-conventions")
}

repositories {
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

tasks.named<ProcessResources>("processResources") {
    enabled = false
}

dependencies {

    api(project(":api"))

    compileOnly(libs.paper.api)

    compileOnly(libs.placeholderapi)
    compileOnly(libs.packetevents)


    compileOnly(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    compileOnly(libs.mongodb) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    compileOnly(libs.configurate.yaml)

    compileOnly(libs.cloud.paper)

    compileOnly(libs.adventure.minimessage) {
        isTransitive = false
    }

    compileOnly(libs.textserializer.legacy)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)

    testImplementation(libs.paper.api)
    testImplementation(libs.configurate.yaml)
    testImplementation(libs.cloud.core)
    testImplementation(libs.hikari)
    testImplementation(libs.mongodb)
    testImplementation(libs.textserializer.legacy)
    testImplementation(libs.actions)
    testImplementation(libs.schedula.common)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.mysql)
    testRuntimeOnly(libs.sqlite)

}

sourceSets.test {
    resources.srcDir("src/main/resources")
}
