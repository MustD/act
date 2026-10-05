package io.challenge_workshop.mal_ui

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/** Bound to loopback only: the relay forwards credentials upstream and is a dev tool. */
private const val PORT = 18010

fun main() {
    embeddedServer(Netty, port = PORT, host = "127.0.0.1", module = Application::module)
        .start(wait = true)
}

fun Application.module(
    relayClient: HttpClient = HttpClient(CIO) { expectSuccess = false },
) {
    // Load-bearing even though relay calls are same-origin: browsers send `Origin` on every
    // POST and PATCH, and behind a reverse proxy this plugin cannot tell that origin is the
    // page's own, so it answers an unlisted one with an empty 403. Every hostname the app is
    // served on must be listed here, or the token exchange fails after a successful login.
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHost("act.io-workshop.localhost", schemes = listOf("https"))
        allowHost("act.io-workshop.net", schemes = listOf("https"))
        listOf(18020, 18030).forEach { port ->
            allowHost("localhost:$port")
            allowHost("127.0.0.1:$port")
        }
    }
    routing {
        // A liveness ping, so `curl 127.0.0.1:18010` distinguishes "relay is up"
        // from "nothing is listening" without going through `/mal`.
        get("/") {
            call.respondText("mal_ui relay")
        }
        malRelay(this, relayClient)
    }
}
