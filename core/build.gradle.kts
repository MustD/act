import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "io.challenge_workshop.mal_ui.core"
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
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutinesCore)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serializationJson)
            api(libs.ktor.clientCore)
            implementation(libs.ktor.clientAuth)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.clientMock)
        }
        // A Ktor engine must be on each target's runtime classpath for the
        // engine-less `HttpClient { }` factory in commonMain to resolve one.
        androidMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        // `KClass.sealedSubclasses` is jvm-only and needs the reflection implementation at runtime.
        // One test uses it — `ScreenStateCoverageTest`, which holds the mapping test's list of cases
        // to the sealed interface — and it is the only reason this is here.
        jvmTest.dependencies {
            implementation(libs.kotlin.reflect)
        }
        webMain.dependencies {
            implementation(libs.ktor.clientJs)
        }
    }
}

/**
 * The build-time default Client ID, in precedence order: a `mal.clientId` Gradle property (so it can
 * live in `~/.gradle/gradle.properties`, outside the repo entirely), then a `MAL_CLIENT_ID`
 * environment variable (which `mise run` fills from the gitignored `.secure.build.env`). Absent
 * everywhere, it is `""` and the app prompts — a missing value is not a build failure, except for the
 * release artifacts that [requireMalClientId] guards.
 *
 * Not `ORG_GRADLE_PROJECT_mal_clientId`: Gradle maps that name to the project property `mal_clientId`
 * verbatim, with no underscore-to-dot conversion, so nothing reads it. `MAL_CLIENT_ID` is the CI route.
 *
 * A public client's ID is not a secret, so the goal is convenience and not-in-git — see CONTEXT.md.
 *
 * **Every step is a lazy `Provider`, and every step treats blank as absent.** Reading a file with
 * `Properties().load(...)` here would be an untracked configuration-time read: the generated constant
 * would then go stale instead of invalidating when the file changed. And `-Pmal.clientId=` — set but
 * empty — has to fall through rather than short-circuit, or an empty override silently hides the
 * lower-precedence source.
 */
val malClientId: Provider<String> =
    providers.gradleProperty("mal.clientId").notBlank()
        .orElse(providers.environmentVariable("MAL_CLIENT_ID").notBlank())
        .orElse("")

/** Trimmed, with blank treated as absent, so a set-but-empty value falls through to the next source. */
fun Provider<String>.notBlank(): Provider<String> = map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Writes `MAL_CLIENT_ID` into `commonMain` as a plain `const val`, which needs no expect/actual and
 * works on every target — the whole reason this is a six-line task rather than a BuildKonfig
 * dependency.
 *
 * `inputs.property` over the `Provider` is what makes this both configuration-cache-safe and correctly
 * invalidated: changing the property re-runs the task, and nothing reads the filesystem at
 * configuration time. **Nothing in the test suite can assert that** — this repo has no build-logic test
 * harness — so it was verified by hand, and the check is worth repeating if this task is touched:
 *
 * ```
 * ./gradlew :core:generateMalBuildConfig -Pmal.clientId=one   # writes "one"
 * ./gradlew :core:generateMalBuildConfig -Pmal.clientId=one   # UP-TO-DATE, cache entry reused
 * ./gradlew :core:generateMalBuildConfig -Pmal.clientId=two   # re-runs: input property changed
 * ./gradlew :core:generateMalBuildConfig                      # back to ""
 * ```
 *
 * Replacing the `inputs.property` line with a `clientId.get()` read at configuration time is the drift
 * this guards against, and it would break the configuration cache without failing anything.
 */
val generateMalBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/malConfig/kotlin")
    val clientId = malClientId
    inputs.property("clientId", clientId)
    outputs.dir(outputDir)
    doLast {
        // Escaped rather than interpolated: a stray quote, backslash or `$` in the property would
        // otherwise produce a file that does not compile.
        val literal = clientId.get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
        val packageDir = outputDir.get().asFile.resolve("io/challenge_workshop/mal_ui/mal")
        packageDir.mkdirs()
        packageDir.resolve("MalBuildConfig.kt").writeText(
            """
            |// Generated by the `generateMalBuildConfig` task in core/build.gradle.kts. Do not edit.
            |package io.challenge_workshop.mal_ui.mal
            |
            |/**
            | * The Client ID this build was configured with, or `""` when it was configured with none.
            | *
            | * Set it with a `mal.clientId` Gradle property or a `MAL_CLIENT_ID` environment variable
            | * (`.secure.build.env`, via `mise run`). Only a
            | * build-time *default*: a Client ID the user typed is remembered per device and wins.
            | */
            |const val MAL_CLIENT_ID: String = "$literal"
            |
            """.trimMargin(),
        )
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir(generateMalBuildConfig) }

/**
 * Fails when no build-time Client ID resolved. Release artifacts `dependsOn(":core:requireMalClientId")`
 * rather than re-implementing the check — the production web bundle today, and the Android release build
 * once play-release 05 lands, so the message and the two sources it names live in one place. Dev runs
 * and tests do not depend on it, and keep "empty → prompt".
 *
 * Declares no outputs, so it runs every time; the check is a string comparison. The `Provider` is
 * captured, not read at configuration time, which keeps it configuration-cache-safe.
 */
val requireMalClientId by tasks.registering {
    val clientId = malClientId
    inputs.property("clientId", clientId)
    doLast {
        if (clientId.get().isEmpty()) {
            throw GradleException(
                "No Client ID resolved, and this build ships to users who cannot type one in. Set one of:\n" +
                    "  -Pmal.clientId=<id>            (or in ~/.gradle/gradle.properties)\n" +
                    "  MAL_CLIENT_ID=<id>             (environment variable)\n" +
                    "Register an app of type `other` at myanimelist.net/apiconfig to get one.",
            )
        }
    }
}
