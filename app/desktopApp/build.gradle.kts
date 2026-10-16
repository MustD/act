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
            // The display name. The package, install folder and launcher are `io-workshop-act` (ADR-0009), set
            // below for Linux, where the generic name would otherwise become all three.
            packageName = "ACT"
            // From act.version, as :server's version and Android's versionName are: no second number to bump.
            packageVersion = providers.gradleProperty("act.version").get()

            linux { packageName = "io-workshop-act" }

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

// On the tasks that write an installer, not only on a lifecycle task above them, for the same reason as the Android
// release tasks: no installer without a Client ID is ever written. The check itself lives in :core.
tasks.matching { it.name.matches(Regex("package(Release)?(Deb|Msi|Dmg)")) }.configureEach {
    dependsOn(":core:requireMalClientId")
}
