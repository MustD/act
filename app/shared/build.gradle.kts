import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        // Not an app — there is no `main` here. Compose's `checkComposeUiTestConfigurationForWasmJs`
        // fails `wasmJsBrowserTest` for any test compilation that reaches Skiko without an
        // executable, because only an executable gets its tests bundled by webpack, which is
        // what loads the Skiko runtime. Compose UI arrives through commonMain, so that is every
        // web test here. See https://youtrack.jetbrains.com/issue/CMP-4906.
        binaries.executable()
    }

    android {
        namespace = "io.challenge_workshop.mal_ui.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.koin.android)
            // Auth Tab, plus the plain Custom Tab it degrades to. See `AuthTabRedirectChannel`.
            implementation(libs.androidx.browser)
            // `rememberLauncherForActivityResult`, which is the only way to register the Auth Tab's
            // `ActivityResultLauncher` from a composable — and the reason
            // `rememberAuthRedirectChannel()` is a `@Composable` at all.
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.clientOkhttp)
        }
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // `BackHandler`: the Android back gesture that closes an Anime Page.
            implementation(libs.compose.uiBackhandler)
            implementation(libs.compose.components.resources)
            // Cover art. Coil 3 is the only image loader with all of this project's targets;
            // `coil-network-ktor3` is what makes it fetch over Ktor rather than over a
            // platform-specific stack, so one `MalImageLoader` covers every Target.
            implementation(libs.coil.compose)
            implementation(libs.coil.networkKtor3)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // `api` because the app modules call `initKoin()` and Koin's own APIs from
            // their entry points; `implementation` would hide `Module` from them.
            api(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.clientMock)
            implementation(libs.koin.test)
        }
        // Robolectric so `AndroidKeyValueStore` is exercised against a real `SharedPreferences`
        // on the host. `withHostTest { isIncludeAndroidResources = true }` above is what makes
        // it work; the alternative was a device test, which needs an emulator to mean anything.
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.robolectric)
        }
        // The Compose UI tests run on this target only. The routing they exercise is common code
        // with no expect/actual in it, and the other three targets would each need a second test
        // harness — Robolectric, karma — to prove the same thing.
        jvmTest.dependencies {
            implementation(libs.compose.uiTest)
            // Skiko's host-native binaries. Without them a UI test has nothing to draw on.
            implementation(compose.desktop.currentOs)
            // `SessionState::class.sealedSubclasses`, so the test's own coverage is checked against
            // the sealed interface rather than against a list someone has to remember to update.
            implementation(libs.kotlin.reflect)
        }
        // Coil's Ktor fetcher builds an engine-less `HttpClient()` of its own, so — exactly as in
        // `:core` — an engine has to be on each target's runtime classpath for it to resolve one.
        // Declared here rather than leaned on transitively through `:core`, where they are
        // `implementation` details that are free to change.
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        webMain.dependencies {
            implementation(libs.ktor.clientJs)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Robolectric runs on compileSdk's android-all here (a library has no targetSdk), and from 37 that
// pokes FileDescriptor through jdk.internal.access, which JDK 21 keeps closed: every test fails in
// setup with "Failed to interact with raw FileDescriptor internals". Same flags as :app:androidApp.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
}
