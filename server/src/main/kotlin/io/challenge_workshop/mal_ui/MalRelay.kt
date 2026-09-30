package io.challenge_workshop.mal_ui

import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post

/**
 * A minimal relay that lets the **web** target complete the MAL login.
 *
 * MyAnimeList sends no CORS headers on its token or API endpoints and rejects preflight
 * `OPTIONS` with 405, so a browser cannot call them at all — the request fails as an opaque
 * `TypeError: Failed to fetch`. Desktop and Android are unaffected and bypass this entirely.
 *
 * Scope is deliberately narrow: only MAL's token endpoint, read-only `GET`s under `/v2`
 * and the one write the Anime Page needs, `PATCH /v2/anime/{id}/my_list_status`, are
 * reachable, and the upstream hosts are hardcoded rather than taken from the request, so this cannot be turned into an open forwarding proxy.
 *
 * It does pass client secrets and bearer tokens through to MAL, which is fine on
 * localhost but means this should not be exposed publicly without authentication of
 * its own.
 */
fun Application.malRelay(
    route: Route,
    client: HttpClient = HttpClient(CIO) { expectSuccess = false },
) {
    monitor.subscribe(ApplicationStopped) { client.close() }

    with(route) {
        // Mirrors MalAuthConfig.DEFAULT_RELAY_BASE_URL, which the web target points at.
        post("/mal/oauth2/token") {
            val form = call.receiveParameters()
            val upstream = client.submitForm(
                url = MalAuthConfig.DEFAULT_TOKEN_ENDPOINT,
                formParameters = form,
            )
            call.respondText(
                text = upstream.bodyAsText(),
                contentType = upstream.contentType() ?: ContentType.Application.Json,
                status = upstream.status,
            )
        }

        // The only write route, and only this path: `{id}` must be numeric, so nothing else under
        // `/v2` can be reached with a PATCH.
        patch(Regex("/mal/v2/anime/(?<id>\\d+)/my_list_status")) {
            val id = call.parameters["id"]
            val upstream = client.patch("${MalAuthConfig.DEFAULT_API_BASE_URL}/anime/$id/my_list_status") {
                call.request.headers[HttpHeaders.Authorization]?.let {
                    header(HttpHeaders.Authorization, it)
                }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(call.receiveText())
            }
            call.respondText(
                text = upstream.bodyAsText(),
                contentType = upstream.contentType() ?: ContentType.Application.Json,
                status = upstream.status,
            )
        }

        get("/mal/v2/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/").orEmpty()
            if (path.isEmpty()) {
                call.respondText("Missing API path", status = HttpStatusCode.BadRequest)
                return@get
            }
            val upstream = client.get("${MalAuthConfig.DEFAULT_API_BASE_URL}/$path") {
                // The browser holds the token; the relay only forwards it.
                call.request.headers[HttpHeaders.Authorization]?.let {
                    header(HttpHeaders.Authorization, it)
                }
                call.request.queryParameters.forEach { key, values ->
                    values.forEach { parameter(key, it) }
                }
            }
            call.respondText(
                text = upstream.bodyAsText(),
                contentType = upstream.contentType() ?: ContentType.Application.Json,
                status = upstream.status,
            )
        }
    }
}
