package io.challenge_workshop.mal_ui

import io.challenge_workshop.mal_ui.mal.MAL_RELAY_PATH_PREFIX
import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.application.install
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlin.time.Duration.Companion.minutes
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
 * It passes bearer tokens and the token exchange through to MAL, so it is built to sit behind a
 * trusted reverse proxy rather than to be exposed bare. What protects it: the client IP is the
 * socket peer unless that peer is a configured trusted proxy ([clientIp]); each IP is rate
 * limited (429) separately on the token exchange and on `/v2`; token and `PATCH` bodies are
 * capped (413) without reading past the cap; the CORS list names the page origins; and
 * nothing here logs tokens or bodies. It has no authentication of its own — MAL's own token
 * check is the authorisation.
 */
fun Application.malRelay(route: Route, client: HttpClient, config: RelayConfig = RelayConfig()) {
    monitor.subscribe(ApplicationStopped) { client.close() }

    val trusted = config.trustedProxies.map(Cidr::parse)
    install(RateLimit) {
        val key: (ApplicationCall) -> Any = { call ->
            rateLimitKey(clientIp(call.request.local.remoteAddress, call.request.headers["X-Forwarded-For"], trusted))
        }
        register(TOKEN_LIMIT) {
            rateLimiter(limit = config.tokenLimitPerMinute, refillPeriod = 1.minutes)
            requestKey(key)
        }
        register(API_LIMIT) {
            rateLimiter(limit = config.apiLimitPerMinute, refillPeriod = 1.minutes)
            requestKey(key)
        }
    }

    with(route) {
        // The prefix is the one `platformMalEndpoints()` puts on every web request.
        rateLimit(TOKEN_LIMIT) { post("$MAL_RELAY_PATH_PREFIX/oauth2/token") {
            val body = call.receiveCapped(config.maxBodyBytes) ?: return@post
            val form = body.parseUrlEncodedParameters()
            val upstream = client.submitForm(
                url = MalAuthConfig.DEFAULT_TOKEN_ENDPOINT,
                formParameters = form,
            )
            call.respondWith(upstream)
        } }

        rateLimit(API_LIMIT) {

        // The only write route, and only this path: `{id}` must be numeric, so nothing else under
        // `/v2` can be reached with a PATCH.
        patch(Regex("$MAL_RELAY_PATH_PREFIX/v2/anime/(?<id>\\d+)/my_list_status")) {
            val id = call.parameters["id"]
            val body = call.receiveCapped(config.maxBodyBytes) ?: return@patch
            val upstream = client.patch("${MalAuthConfig.DEFAULT_API_BASE_URL}/anime/$id/my_list_status") {
                call.request.headers[HttpHeaders.Authorization]?.let {
                    header(HttpHeaders.Authorization, it)
                }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
            }
            call.respondWith(upstream)
        }

        get("$MAL_RELAY_PATH_PREFIX/v2/{path...}") {
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
            call.respondWith(upstream)
        }
        }
    }
}

private val TOKEN_LIMIT = RateLimitName("token")
private val API_LIMIT = RateLimitName("api")

/**
 * The request body as text, or null after answering 413. A declared `Content-Length` over the cap
 * is refused before reading; otherwise at most one byte past the cap is read, so a chunked
 * upload cannot make the relay buffer more than that.
 */
private suspend fun ApplicationCall.receiveCapped(max: Int): String? {
    val declared = request.contentLength()
    if (declared != null && declared > max) {
        response.headers.append(HttpHeaders.Connection, "close") // the unread body must not be parsed as a next request
        respondText("Request body too large", status = HttpStatusCode.PayloadTooLarge)
        return null
    }
    val bytes = receiveChannel().readRemaining(max.toLong() + 1).readByteArray()
    if (bytes.size > max) {
        respondText("Request body too large", status = HttpStatusCode.PayloadTooLarge)
        return null
    }
    return bytes.decodeToString()
}

/** Hands MAL's answer back as it came: body, content type and status, a 4xx or 5xx included. */
private suspend fun ApplicationCall.respondWith(upstream: HttpResponse) {
    respondText(
        text = upstream.bodyAsText(),
        contentType = upstream.contentType() ?: ContentType.Application.Json,
        status = upstream.status,
    )
}
