package io.challenge_workshop.mal_ui

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MalRelayTest {

    @Test
    fun relayAnswersCorsPreflightSoBrowsersCanPost() = testApplication {
        application { module() }

        val response: HttpResponse = client.request("/mal/oauth2/token") {
            method = HttpMethod.Options
            header(HttpHeaders.Origin, "http://localhost:18020")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }

        // This is exactly what MAL itself refuses to do (it answers 405), and why the relay exists.
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "http://localhost:18020",
            response.headers[HttpHeaders.AccessControlAllowOrigin],
        )
    }

    @Test
    fun relayAllowsTheProxiedHostnameOverHttps() = testApplication {
        application { module() }

        val response: HttpResponse = client.request("/mal/oauth2/token") {
            method = HttpMethod.Options
            header(HttpHeaders.Origin, "https://mal-ui.localhost")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }

        assertEquals(
            "https://mal-ui.localhost",
            response.headers[HttpHeaders.AccessControlAllowOrigin],
        )
    }

    @Test
    fun relayRejectsPlaintextOnTheProxiedHostname() = testApplication {
        application { module() }

        // The proxy sets HSTS, so http:// is never a legitimate origin for this host.
        val response: HttpResponse = client.request("/mal/oauth2/token") {
            method = HttpMethod.Options
            header(HttpHeaders.Origin, "http://mal-ui.localhost")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }

        assertTrue(response.headers[HttpHeaders.AccessControlAllowOrigin] == null)
    }

    @Test
    fun relayRejectsUnknownOrigin() = testApplication {
        application { module() }

        val response: HttpResponse = client.request("/mal/oauth2/token") {
            method = HttpMethod.Options
            header(HttpHeaders.Origin, "https://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }

        assertTrue(
            response.headers[HttpHeaders.AccessControlAllowOrigin] == null,
            "must not hand out a wildcard to arbitrary origins",
        )
    }

    @Test
    fun apiRelayRequiresAPath() = testApplication {
        application { module() }

        val response = client.get("/mal/v2/")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun rootRouteStillWorks() = testApplication {
        application { module() }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.bodyAsText().ifBlank { null })
    }

    @Test
    fun patchRouteForwardsMethodBodyAndAuthorizationAndRelaysTheAnswer() = testApplication {
        var seen: io.ktor.client.request.HttpRequestData? = null
        var seenBody = ""
        val upstream = HttpClient(MockEngine) {
            expectSuccess = false
            engine {
                addHandler { request ->
                    seen = request
                    seenBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = """{"status":"watching","num_episodes_watched":3}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
        application { module(relayClient = upstream) }

        val response = client.request("/mal/v2/anime/52991/my_list_status") {
            method = HttpMethod.Patch
            header(HttpHeaders.Authorization, "Bearer token")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("num_watched_episodes=3&start_date=")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"watching","num_episodes_watched":3}""", response.bodyAsText())
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(HttpMethod.Patch, seen?.method)
        assertEquals("https://api.myanimelist.net/v2/anime/52991/my_list_status", seen?.url.toString())
        assertEquals("Bearer token", seen?.headers?.get(HttpHeaders.Authorization))
        assertEquals("num_watched_episodes=3&start_date=", seenBody)
    }

    @Test
    fun patchToAnyOtherPathIsNotRouted() = testApplication {
        var calls = 0
        val upstream = HttpClient(MockEngine) {
            engine { addHandler { calls++; respond("", HttpStatusCode.OK) } }
        }
        application { module(relayClient = upstream) }

        listOf(
            "/mal/v2/anime/abc/my_list_status",
            "/mal/v2/anime/1/my_list_status/extra",
            "/mal/v2/anime/1",
            "/mal/v2/users/@me/animelist",
            "/mal/oauth2/token",
        ).forEach { path ->
            val response = client.request(path) {
                method = HttpMethod.Patch
                header(HttpHeaders.Authorization, "Bearer token")
                setBody("score=1")
            }
            assertTrue(
                response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed,
                "PATCH $path was answered ${response.status}",
            )
        }
        assertEquals(0, calls, "nothing may reach MAL")
    }

    @Test
    fun corsPreflightAllowsPatch() = testApplication {
        application { module() }

        val response = client.request("/mal/v2/anime/1/my_list_status") {
            method = HttpMethod.Options
            header(HttpHeaders.Origin, "http://localhost:18020")
            header(HttpHeaders.AccessControlRequestMethod, "PATCH")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
