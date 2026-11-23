plugins {
    id("java-library")
    id("react.base-conventions")
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(16)
}

tasks.named<ProcessResources>("processResources") {
    enabled = false
}

dependencies {

    compileOnly(libs.schedula.common)

    api(project(":api"))

    compileOnly("com.google.guava:guava:31.1-jre")
    compileOnly("com.google.code.gson:gson:2.8.9")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.10.0")
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage) {
        isTransitive = false
    }
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.fastutil)

    compileOnly(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    compileOnly(libs.mongodb) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    compileOnly(libs.configurate.yaml)
    compileOnly(libs.cloud.core)
    compileOnly(libs.textserializer.legacy)
    compileOnly(libs.actions)

    testImplementation(libs.schedula.common)
    testImplementation("com.google.code.gson:gson:2.8.9")
    testImplementation("net.kyori:adventure-text-serializer-legacy:4.10.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.10.0")
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.fastutil)
    testImplementation(libs.configurate.yaml)
    testImplementation(libs.cloud.core)
    testImplementation(libs.hikari)
    testImplementation(libs.mongodb)
    testImplementation(libs.textserializer.legacy)
    testImplementation(libs.actions)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.mysql)
    testRuntimeOnly(libs.sqlite)

}

sourceSets.test {
    resources.srcDir("src/main/resources")
}
