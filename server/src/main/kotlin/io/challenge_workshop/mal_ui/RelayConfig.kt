package io.challenge_workshop.mal_ui

/** Env var names, documented in `CLAUDE.md`. */
const val ENV_RELAY_HOST = "ACT_RELAY_HOST"
const val ENV_RELAY_PORT = "ACT_RELAY_PORT"
const val ENV_RELAY_CORS_ORIGINS = "ACT_RELAY_CORS_ORIGINS"

/**
 * Where the relay binds and which page origins it answers. The defaults are the development
 * setup: loopback only, and the hostnames the web target is served on locally.
 *
 * [corsOrigins] are full origins (`https://host[:port]`), so the scheme is part of the match.
 */
data class RelayConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val corsOrigins: List<String> = DEFAULT_CORS_ORIGINS,
) {
    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 18010

        val DEFAULT_CORS_ORIGINS: List<String> =
            listOf("https://act.io-workshop.localhost", "https://act.io-workshop.net") +
                listOf(18020, 18030).flatMap { port ->
                    listOf("localhost", "127.0.0.1").flatMap { host ->
                        listOf("http", "https").map { scheme -> "$scheme://$host:$port" }
                    }
                }

        /** Unset or blank variables fall back to the default; a malformed port fails startup. */
        fun fromEnv(env: Map<String, String> = System.getenv()): RelayConfig {
            fun value(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            return RelayConfig(
                host = value(ENV_RELAY_HOST) ?: DEFAULT_HOST,
                port = value(ENV_RELAY_PORT)?.let {
                    it.toIntOrNull()?.takeIf { p -> p in 1..65535 }
                        ?: error("$ENV_RELAY_PORT must be a port number, was '$it'")
                } ?: DEFAULT_PORT,
                corsOrigins = value(ENV_RELAY_CORS_ORIGINS)
                    ?.split(',')?.map { it.trim().trimEnd('/') }?.filter { it.isNotEmpty() }
                    ?: DEFAULT_CORS_ORIGINS,
            )
        }
    }
}
