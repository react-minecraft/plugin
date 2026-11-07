plugins {
    id("java-library")
    id("react.base-conventions")
}

dependencies {

    compileOnly(libs.paper.api)

    compileOnly(libs.schedula.common)
    compileOnly(libs.configurate.yaml)

    testImplementation(libs.paper.api)
    testImplementation(libs.schedula.common)
    testImplementation(libs.configurate.yaml)

}
