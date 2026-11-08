plugins {
    id("java-library")
    id("react.base-conventions")
}

dependencies {

    compileOnly(libs.paper.api)
    compileOnly(libs.cloud.core)

    compileOnly(libs.schedula.common)
    compileOnly(libs.configurate.yaml)

    testImplementation(libs.paper.api)
    testImplementation(libs.cloud.core)
    testImplementation(libs.schedula.common)
    testImplementation(libs.configurate.yaml)

}
