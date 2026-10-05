package io.challenge_workshop.mal_ui

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class RelayConfigTest {

    @Test
    fun nothing_set_is_todays_behaviour() {
        val config = RelayConfig.fromEnv(emptyMap())
        assertEquals("127.0.0.1", config.host)
        assertEquals(18010, config.port)
        assertEquals(RelayConfig.DEFAULT_CORS_ORIGINS, config.corsOrigins)
        assertEquals(config, RelayConfig())
    }

    @Test
    fun blank_values_fall_back_to_defaults() {
        val config = RelayConfig.fromEnv(
            mapOf(ENV_RELAY_HOST to " ", ENV_RELAY_PORT to "", ENV_RELAY_CORS_ORIGINS to ""),
        )
        assertEquals(RelayConfig(), config)
    }

    @Test
    fun host_port_and_origins_are_read() {
        val config = RelayConfig.fromEnv(
            mapOf(
                ENV_RELAY_HOST to "0.0.0.0",
                ENV_RELAY_PORT to "8080",
                ENV_RELAY_CORS_ORIGINS to "https://a.example, https://b.example/",
            ),
        )
        assertEquals(RelayConfig("0.0.0.0", 8080, listOf("https://a.example", "https://b.example")), config)
    }

    @Test
    fun a_malformed_port_fails_startup() {
        assertFailsWith<IllegalStateException> {
            RelayConfig.fromEnv(mapOf(ENV_RELAY_PORT to "http"))
        }
    }

    @Test
    fun a_custom_origin_list_allows_that_origin_and_rejects_localhost() = testApplication {
        application { module(config = RelayConfig(corsOrigins = listOf("https://act.io-workshop.net"))) }

        val allowed = client.get("/") { header(HttpHeaders.Origin, "https://act.io-workshop.net") }
        assertEquals(HttpStatusCode.OK, allowed.status)
        assertEquals("https://act.io-workshop.net", allowed.headers[HttpHeaders.AccessControlAllowOrigin])

        val rejected = client.get("/") { header(HttpHeaders.Origin, "http://localhost:18020") }
        assertNull(rejected.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
