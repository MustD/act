@file:OptIn(ExperimentalWasmJsInterop::class)

package io.challenge_workshop.mal_ui.mal

import kotlin.js.ExperimentalWasmJsInterop

/**
 * The page's own origin, such as `https://mal-ui.localhost` or `http://localhost:18020`.
 *
 * Read with `js()` rather than via `kotlinx.browser`, which the Wasm stdlib does not carry — this
 * avoids adding a dependency just to read one string.
 */
internal fun browserOrigin(): String = js("window.location.origin")

/**
 * Browsers must go through the same-origin `:server` relay — see [platformMalEndpoints].
 * Start it with `./gradlew :server:run` before attempting to log in on web.
 */
actual fun platformMalEndpoints(): MalEndpoints = relayEndpointsFor(browserOrigin())

/**
 * The callback route on whichever origin this build is being served from, so one build works behind
 * the reverse proxy and on the direct dev-server port. Both origins are registered on the MAL app;
 * an unregistered one fails as a 401 `invalid_client`.
 */
actual fun platformRedirectUri(): String = redirectUriFor(browserOrigin())
