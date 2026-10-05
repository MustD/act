package io.challenge_workshop.mal_ui

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RelayHardeningTest {
    private val upstream = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) })

    private fun ApplicationTestBuilder.relay(config: RelayConfig) =
        application { module(upstream, config) }

    private suspend fun ApplicationTestBuilder.token(body: String = "grant_type=x", xff: String? = null) =
        client.post("/mal/oauth2/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            xff?.let { header("X-Forwarded-For", it) }
            setBody(body)
        }

    // The test host's peer is loopback, so trusting it stands in for "behind the proxy".
    private val loopback = listOf("127.0.0.1/32", "::1/128").map(Cidr::parse)

    @Test
    fun the_token_endpoint_trips_at_the_configured_rate_with_429() = testApplication {
        relay(RelayConfig(tokenLimitPerMinute = 2))
        assertEquals(HttpStatusCode.OK, token().status)
        assertEquals(HttpStatusCode.OK, token().status)
        assertEquals(HttpStatusCode.TooManyRequests, token().status)
    }

    @Test
    fun the_api_limit_is_separate_from_the_token_limit() = testApplication {
        relay(RelayConfig(tokenLimitPerMinute = 1, apiLimitPerMinute = 2))
        token()
        assertEquals(HttpStatusCode.TooManyRequests, token().status)
        assertEquals(HttpStatusCode.OK, client.get("/mal/v2/anime?q=x").status)
        assertEquals(HttpStatusCode.OK, client.get("/mal/v2/anime?q=x").status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/mal/v2/anime?q=x").status)
    }

    @Test
    fun a_spoofed_forwarded_for_from_an_untrusted_peer_does_not_get_its_own_bucket() = testApplication {
        relay(RelayConfig(tokenLimitPerMinute = 1))
        assertEquals(HttpStatusCode.OK, token(xff = "1.1.1.1").status)
        assertEquals(HttpStatusCode.TooManyRequests, token(xff = "2.2.2.2").status)
    }

    @Test
    fun a_trusted_peers_forwarded_for_does_get_its_own_bucket() = testApplication {
        relay(RelayConfig(tokenLimitPerMinute = 1, trustedProxies = loopback))
        assertEquals(HttpStatusCode.OK, token(xff = "1.1.1.1").status)
        assertEquals(HttpStatusCode.OK, token(xff = "2.2.2.2").status)
        assertEquals(HttpStatusCode.TooManyRequests, token(xff = "1.1.1.1").status)
    }

    @Test
    fun an_oversized_token_body_is_413_and_one_at_the_cap_is_not() = testApplication {
        relay(RelayConfig(maxBodyBytes = 32))
        assertEquals(HttpStatusCode.OK, token("a=" + "x".repeat(30)).status)
        assertEquals(HttpStatusCode.PayloadTooLarge, token("a=" + "x".repeat(31)).status)
    }

    @Test
    fun an_oversized_chunked_body_is_413_without_a_content_length() = testApplication {
        relay(RelayConfig(maxBodyBytes = 32))
        fun chunked(body: String) = object : OutgoingContent.WriteChannelContent() {
            override val contentType = ContentType.Application.FormUrlEncoded
            override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeStringUtf8(body)
        }
        val big = client.post("/mal/oauth2/token") { setBody(chunked("a=" + "x".repeat(100))) }
        assertEquals(HttpStatusCode.PayloadTooLarge, big.status)
        val small = client.post("/mal/oauth2/token") { setBody(chunked("grant_type=x")) }
        assertEquals(HttpStatusCode.OK, small.status)
    }

    @Test
    fun an_oversized_patch_body_is_413() = testApplication {
        relay(RelayConfig(maxBodyBytes = 32))
        val big = client.patch("/mal/v2/anime/1/my_list_status") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("a=" + "x".repeat(100))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, big.status)
        val small = client.patch("/mal/v2/anime/1/my_list_status") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("status=watching")
        }
        assertEquals(HttpStatusCode.OK, small.status)
    }
}
