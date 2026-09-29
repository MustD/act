package io.challenge_workshop.mal_ui.mal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs under `wasmJsTest`, in a real browser, against the real `window.location`. */
class MalEndpointsWebTest {

    @Test
    fun theBrowserRedirectUriIsDerivedFromTheLiveOrigin() {
        // Not hardcoded, so one build works behind the reverse proxy and on the direct dev-server
        // port. The origin under test is whatever Karma is serving from, which is precisely the point
        // — none of the registered web origins is named here.
        val origin = browserOrigin()
        assertTrue(origin.startsWith("http"), "the test page has a real origin to derive from: $origin")
        assertEquals("$origin/oauth/callback", platformRedirectUri())
    }

    @Test
    fun theBrowserRedirectUriSharesTheOriginOfTheRelay() {
        // Same origin as the page, and therefore as the relay: a callback served from anywhere else
        // could neither postMessage back to the opener nor reach the relay without CORS.
        val origin = browserOrigin()
        assertEquals(origin, platformRedirectUri().removeSuffix(OAUTH_CALLBACK_PATH))
        assertEquals("$origin/mal/oauth2/token", platformMalEndpoints().tokenEndpoint)
    }
}
