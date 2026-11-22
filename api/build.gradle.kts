plugins {
    id("java-library")
    id("react.base-conventions")
}

dependencies {

    compileOnly(libs.schedula.common)

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.cloud.core)

    compileOnly(libs.configurate.yaml)
    compileOnly(libs.actions)

    testImplementation(libs.schedula.common)
    testImplementation(libs.adventure.api)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.jetbrains.annotations)
    testImplementation(libs.cloud.core)
    testImplementation(libs.configurate.yaml)
    testRuntimeOnly(libs.actions)

}
