import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Dev server port, from this project's 18010-18090 block:
//   18010  :server (Ktor MAL relay)
//   18020  this module
//
// Host binding, allowed hosts, and the /mal proxy live in webpack.config.d/devserver.js.
// The port is set here because it belongs to the target's own webpack config.
//
// The port is written inline rather than held in a script-level `val`: the webpack config
// block would then capture the enclosing script object, which the configuration cache
// cannot serialize.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply { port = 18020 }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:shared"))

            implementation(libs.compose.ui)
        }
    }
}

// The production bundle ships with a Client ID baked in; the dev run (`wasmJsBrowserDevelopmentRun`)
// keeps "empty → prompt". The check itself lives in :core so Android release can reuse it.
tasks.matching { it.name == "wasmJsBrowserDistribution" }.configureEach {
    dependsOn(":core:requireMalClientId")
}
