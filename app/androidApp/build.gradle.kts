import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Only the manifest drift guard — see `AndroidManifestTest`. Robolectric because the facts it
    // asserts (intent-filter matching, launch mode, the backup flag) are properties of the *merged*
    // manifest as Android parses it, not of the XML in this module.
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.robolectric)
}

// `major*1_000_000 + minor*1_000 + patch` of act.version; the formula and why are in ADR-0008.
val appVersionCode: Provider<Int> = providers.gradleProperty("act.version").map { version ->
    val parts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(version)?.destructured?.toList()?.map { it.toInt() }
        ?: error("act.version '$version' is not major.minor.patch")
    check(parts.all { it in 0..999 }) { "act.version '$version' has a part above 999; versionCode would collide" }
    parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
}

val uploadEnv = listOf(
    "ACT_UPLOAD_STORE_FILE",
    "ACT_UPLOAD_STORE_PASSWORD",
    "ACT_UPLOAD_KEY_ALIAS",
    "ACT_UPLOAD_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it) }

android {
    namespace = "net.io_workshop.act"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.io_workshop.act"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // versionCode is set per variant below, from act.version (ADR-0008).
        versionName = providers.gradleProperty("act.version").get()
    }
    testOptions {
        // Robolectric needs the merged manifest and the compiled resources.
        unitTests.isIncludeAndroidResources = true
        // Robolectric runs on targetSdk's android-all, and from 37 that pokes FileDescriptor through
        // jdk.internal.access, which JDK 21 keeps closed: every test fails in setup with
        // "Failed to interact with raw FileDescriptor internals".
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        // Upload key, from the environment only. Values stay null when unset, so debug builds and
        // configuration never need them; `requireUploadSigning` is what fails a release build.
        create("upload") {
            uploadEnv["ACT_UPLOAD_STORE_FILE"]!!.orNull?.let { storeFile = file(it) }
            storePassword = uploadEnv["ACT_UPLOAD_STORE_PASSWORD"]!!.orNull
            keyAlias = uploadEnv["ACT_UPLOAD_KEY_ALIAS"]!!.orNull
            keyPassword = uploadEnv["ACT_UPLOAD_KEY_PASSWORD"]!!.orNull
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { it.versionCode.set(appVersionCode) }
    }
}

val requireUploadSigning by tasks.registering {
    description = "Fails naming the ACT_UPLOAD_* variables that are unset or empty"
    val values = uploadEnv.mapValues { (_, provider) -> provider.orElse("") }
    inputs.property("uploadEnvSet", providers.provider { values.mapValues { it.value.get().isNotEmpty() } })
    doLast {
        val missing = values.filterValues { it.get().isEmpty() }.keys
        check(missing.isEmpty()) {
            "A release build is signed with the upload key and these variables are unset: " +
                "${missing.joinToString()}. Fill in .secure.release.env (see .secure.release.env.example) and run " +
                "`mise run build:android:release`."
        }
    }
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    dependsOn(requireUploadSigning)
}
