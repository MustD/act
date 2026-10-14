import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}

compose.desktop {
    application {
        mainClass = "net.io_workshop.act.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "net.io_workshop.act"
            packageVersion = "1.0.0"

            // Rendered from docs/play-assets/icon.svg by docs/play-assets/regenerate.sh.
            linux { iconFile.set(project.file("icons/icon.png")) }
            windows { iconFile.set(project.file("icons/icon.ico")) }
            macOS { iconFile.set(project.file("icons/icon.icns")) }

            // `LoopbackRedirectListener` uses `com.sun.net.httpserver`, and Compose's
            // DEFAULT_RUNTIME_MODULES is only java.base, java.desktop, java.logging and
            // jdk.crypto.ec. Without this the app runs fine under `:run` — a toolchain JDK has
            // every module — and NoClassDefFoundErrors in a packaged build, where the jlink image
            // does not.
            modules("jdk.httpserver")
        }
    }
}