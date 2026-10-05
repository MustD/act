package io.challenge_workshop.mal_ui

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The privacy policy promises the relay neither stores nor logs tokens or bodies. This pins it:
 * the shipped `logback.xml`, every logger, every route.
 */
class RelayLoggingTest {

    private val secrets = listOf("SECRET-ACCESS-TOKEN", "SECRET-AUTH-CODE", "SECRET-VERIFIER", "SECRET-REFRESH")

    @Test
    fun theShippedRootLoggerIsNotTraceOrDebug() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        assertTrue(root.level.isGreaterOrEqual(Level.INFO), "root logger is ${root.level}")
    }

    @Test
    fun noTokenOrBodyReachesTheLogOnAnyRelayRoute() = testApplication {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val captured = ListAppender<ILoggingEvent>().apply { start() }
        root.addAppender(captured)
        try {
            application {
                module(
                    HttpClient(
                        MockEngine {
                            respond(
                                """{"access_token":"SECRET-REFRESH"}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    ),
                )
            }
            client.post("/mal/oauth2/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("code=SECRET-AUTH-CODE&code_verifier=SECRET-VERIFIER")
            }
            client.get("/mal/v2/users/@me") { header(HttpHeaders.Authorization, "Bearer SECRET-ACCESS-TOKEN") }
            client.patch("/mal/v2/anime/1/my_list_status") {
                header(HttpHeaders.Authorization, "Bearer SECRET-ACCESS-TOKEN")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("status=SECRET-VERIFIER")
            }
            // An oversized body is the path that writes its own response by hand.
            client.post("/mal/oauth2/token") { setBody("SECRET-AUTH-CODE".repeat(5000)) }
        } finally {
            root.detachAppender(captured)
        }

        val text = captured.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        secrets.forEach { assertEquals(false, text.contains(it), "log contains $it:\n$text") }
    }
}
