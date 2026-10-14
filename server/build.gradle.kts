plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "net.io_workshop.act"
version = providers.gradleProperty("act.version").get()
application {
    mainClass = "net.io_workshop.act.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientMock)
    testImplementation(libs.kotlin.testJunit)
}