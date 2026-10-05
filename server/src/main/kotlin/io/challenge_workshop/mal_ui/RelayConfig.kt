package io.challenge_workshop.mal_ui

/** Env var names, documented in `CLAUDE.md`. */
const val ENV_RELAY_HOST = "ACT_RELAY_HOST"
const val ENV_RELAY_PORT = "ACT_RELAY_PORT"
const val ENV_RELAY_CORS_ORIGINS = "ACT_RELAY_CORS_ORIGINS"
const val ENV_RELAY_TRUSTED_PROXIES = "ACT_RELAY_TRUSTED_PROXIES"
const val ENV_RELAY_TOKEN_LIMIT = "ACT_RELAY_TOKEN_LIMIT_PER_MIN"
const val ENV_RELAY_API_LIMIT = "ACT_RELAY_API_LIMIT_PER_MIN"
const val ENV_RELAY_MAX_BODY_BYTES = "ACT_RELAY_MAX_BODY_BYTES"

/**
 * Where the relay binds and which page origins it answers. The defaults are the development
 * setup: loopback only, and the hostnames the web target is served on locally.
 *
 * [corsOrigins] are full origins (`https://host[:port]`), so the scheme is part of the match.
 * [trustedProxies] are the hops whose `X-Forwarded-For` is believed — every hop in front of the
 * relay, see [clientIp]; empty means the socket address is always the client. The limits are per
 * client IP per minute.
 */
data class RelayConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val corsOrigins: List<String> = DEFAULT_CORS_ORIGINS,
    val trustedProxies: List<Cidr> = emptyList(),
    val tokenLimitPerMinute: Int = DEFAULT_TOKEN_LIMIT,
    val apiLimitPerMinute: Int = DEFAULT_API_LIMIT,
    val maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
) {
    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 18010
        const val DEFAULT_TOKEN_LIMIT = 10
        const val DEFAULT_API_LIMIT = 120
        const val DEFAULT_MAX_BODY_BYTES = 16 * 1024

        /** The localhost dev-server ports are plain `http`; the proxied hostnames are `https`. */
        val DEFAULT_CORS_ORIGINS: List<String> =
            listOf("https://act.io-workshop.localhost", "https://act.io-workshop.net") +
                listOf(18020, 18030).flatMap { port -> listOf("http://localhost:$port", "http://127.0.0.1:$port") }

        /** Scheme, host and optional port, nothing else — what a browser sends as `Origin`. */
        private val ORIGIN = Regex("""https?://([A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*|\[[0-9A-Fa-f:.]+])(:\d{1,5})?""")

        /** Unset or blank variables fall back to the default; a malformed value fails startup. */
        fun fromEnv(env: Map<String, String> = System.getenv()): RelayConfig {
            fun value(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun list(name: String) = value(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            fun number(name: String, default: Int, valid: IntRange) = value(name)?.let {
                it.toIntOrNull()?.takeIf { n -> n in valid } ?: error("$name must be a number in $valid, was '$it'")
            } ?: default
            fun positive(name: String, default: Int) = number(name, default, 1..Int.MAX_VALUE)
            return RelayConfig(
                host = value(ENV_RELAY_HOST) ?: DEFAULT_HOST,
                port = number(ENV_RELAY_PORT, DEFAULT_PORT, 1..65535),
                corsOrigins = list(ENV_RELAY_CORS_ORIGINS)?.map { origin ->
                    origin.trimEnd('/').also {
                        check(ORIGIN.matches(it)) { "$ENV_RELAY_CORS_ORIGINS entries must be http(s)://host[:port], was '$origin'" }
                    }
                } ?: DEFAULT_CORS_ORIGINS,
                trustedProxies = list(ENV_RELAY_TRUSTED_PROXIES)?.map(Cidr::parse) ?: emptyList(),
                tokenLimitPerMinute = positive(ENV_RELAY_TOKEN_LIMIT, DEFAULT_TOKEN_LIMIT),
                apiLimitPerMinute = positive(ENV_RELAY_API_LIMIT, DEFAULT_API_LIMIT),
                maxBodyBytes = positive(ENV_RELAY_MAX_BODY_BYTES, DEFAULT_MAX_BODY_BYTES),
            )
        }
    }
}
