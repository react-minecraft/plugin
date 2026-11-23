plugins {
    id("java-library")
    id("react.base-conventions")
    id("react.shadow-conventions")
    id("react.runtime-libraries")
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    api(project(":common"))

    compileOnly(libs.minestom)
    compileOnly(libs.cloud.core)
    compileOnly(libs.schedula.common)
    compileOnly(libs.schedula.minestom)
    compileOnly(libs.adventure.api)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

sourceSets {
    main {
        resources {
            srcDir(project(":common").file("src/main/resources"))
        }
    }
}

tasks.named<Jar>("shadowJar") {
    manifest {
        attributes["Implementation-Version"] = rootProject.version.toString()
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}
