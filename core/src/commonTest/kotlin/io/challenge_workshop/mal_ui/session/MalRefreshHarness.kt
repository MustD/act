package io.challenge_workshop.mal_ui.session

import io.challenge_workshop.mal_ui.animelist.AnimeListResponse
import io.challenge_workshop.mal_ui.animepage.AnimeDetailsResponse
import io.challenge_workshop.mal_ui.animepage.ListStatusResponse
import io.challenge_workshop.mal_ui.mal.HttpClientFactory
import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.challenge_workshop.mal_ui.mal.MalTokens
import io.challenge_workshop.mal_ui.mal.MalUser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.IOException

/** Endpoints fixed here rather than taken from `platformMalEndpoints()`, which differs per target. */
const val TEST_TOKEN_ENDPOINT: String = "https://mal.test/v1/oauth2/token"
const val TEST_API_BASE_URL: String = "https://mal.test/v2"

val TEST_CONFIG: MalAuthConfig = MalAuthConfig(
    clientId = "test-client",
    redirectUri = "http://127.0.0.1:18040/oauth/callback",
    tokenEndpoint = TEST_TOKEN_ENDPOINT,
    apiBaseUrl = TEST_API_BASE_URL,
)

/**
 * A pair the fake MAL already accepts, for tests that are about something other than refresh.
 * Pass its access token as `acceptedAccessToken` — see [FakeMal].
 */
val VALID_TOKENS: MalTokens = MalTokens(
    tokenType = "Bearer",
    expiresIn = 2_415_600,
    accessToken = "good-access",
    refreshToken = "good-refresh",
)

val STALE_TOKENS: MalTokens = MalTokens(
    tokenType = "Bearer",
    expiresIn = 2_415_600,
    accessToken = "stale-access",
    refreshToken = "good-refresh",
)

val TEST_USER: MalUser = MalUser(id = 42, name = "someone")

private val ANIME_DETAILS_PATH = Regex(""".*/anime/\d+""")

private val LIST_STATUS_PATH = Regex(""".*/anime/\d+/my_list_status""")

/** One `PATCH /anime/{id}/my_list_status` the fake MAL received: what was sent, not what it made of it. */
data class ListStatusPatch(val method: HttpMethod, val url: Url, val contentType: String?, val form: Parameters)

private const val USER_JSON = """{"id":42,"name":"someone"}"""

/** How the fake MAL answers a refresh. */
sealed interface RefreshResponse {
    /** 200 with a rotated pair. */
    data class Rotated(val accessToken: String, val refreshToken: String) : RefreshResponse

    /** MAL rejecting the refresh token: the store must be cleared. */
    data class Rejected(val status: HttpStatusCode, val errorCode: String?) : RefreshResponse

    /** 5xx: the refresh token is still good, so the store must survive. */
    data class ServerError(val status: HttpStatusCode = HttpStatusCode.ServiceUnavailable) : RefreshResponse

    /** The request never reaches MAL — offline, DNS, TLS. The store must survive. */
    data object TransportFailure : RefreshResponse
}

/**
 * A fake MAL that 401s any access token it has not issued.
 *
 * The point of the harness is that the 401 is *earned* — the engine really does reject the stale
 * token — so the `Auth` plugin's refresh path is exercised rather than simulated.
 */
class FakeMal(
    private val refreshResponse: RefreshResponse = RefreshResponse.Rotated("fresh-access", "fresh-refresh"),
    /** Set to withhold `WWW-Authenticate`, which MAL may or may not send. */
    private val sendChallengeHeader: Boolean = false,
    /** Completed by the test to let a refresh finish, so mid-refresh state is observable. */
    private val releaseRefresh: CompletableDeferred<Unit>? = null,
    /** Called on each `/users/@me` hit that carried an accepted token. */
    private val onAuthorizedRequest: suspend (HttpRequestData) -> Unit = {},
    /**
     * An access token to accept from the start, for tests whose subject is not the refresh path.
     * Without one every first request earns a 401, which is the point for [MalSessionRefreshTest]
     * and pure noise for anything else.
     */
    acceptedAccessToken: String? = null,
    /** How the fake answers `/users/@me/animelist`, given the `offset` that was asked for. */
    private val animeList: (Int) -> AnimeListResponse = { AnimeListResponse.Page(emptyList(), hasMore = false) },
    /**
     * Called with the `offset` of each Anime List request before it is answered. Suspend in here to
     * hold that one page in flight: without a way to do that every request completes before the
     * next line of the test runs, and "a page already in flight is not requested twice" has no
     * in-flight page to be asked about twice.
     */
    private val holdAnimeList: suspend (Int) -> Unit = {},
    /** How the fake answers `GET /anime/{id}`, given the id that was asked for. */
    private val animeDetails: (Long) -> AnimeDetailsResponse = { AnimeDetailsResponse.NotFound },
    /** Called with the id of each `GET /anime/{id}` before it is answered, to hold one in flight. */
    private val holdAnimeDetails: suspend (Long) -> Unit = {},
    /** How the fake answers `PATCH /anime/{id}/my_list_status`, given the id and the form fields sent. */
    private val updateListStatus: (Long, Parameters) -> ListStatusResponse = { _, _ ->
        ListStatusResponse.Failure()
    },
    /** Called with the id of each list-status PATCH before it is answered, to hold one in flight. */
    private val holdListStatusUpdate: suspend (Long) -> Unit = {},
    /**
     * How many token-endpoint requests fail in transport before [refreshResponse] applies: a blip,
     * after which the same Pending Authorization is still good.
     */
    private val transportFailuresFirst: Int = 0,
) {
    var tokenEndpointHits: Int = 0
        private set
    var userEndpointHits: Int = 0
        private set

    /**
     * Empty to begin with, so the access token the store starts out holding is genuinely expired as
     * far as this fake MAL is concerned. Only a token the token endpoint has actually issued is
     * accepted, which is what makes the 401 earned rather than staged.
     */
    private val acceptedAccessTokens = mutableSetOf<String>().apply {
        acceptedAccessToken?.let { add(it) }
    }

    /** Every `/users/@me/animelist` request the fake was asked, in order, including retried ones. */
    val animeListRequests: MutableList<Url> = mutableListOf()

    /** The `Authorization` header of each of [animeListRequests], so a retry's token is visible. */
    val animeListAuthorizations: MutableList<String> = mutableListOf()

    /** Every `GET /anime/{id}` the fake was asked, in order. */
    val animeDetailsRequests: MutableList<Url> = mutableListOf()

    /** Every list-status PATCH the fake was asked, in order. */
    val listStatusPatches: MutableList<ListStatusPatch> = mutableListOf()

    val engine: MockEngine = MockEngine { request ->
        when {
            request.url.toString().startsWith(TEST_TOKEN_ENDPOINT) -> {
                tokenEndpointHits++
                releaseRefresh?.await()
                if (tokenEndpointHits <= transportFailuresFirst) throw IOException("connection reset")
                when (val r = refreshResponse) {
                    is RefreshResponse.Rotated -> {
                        acceptedAccessTokens += r.accessToken
                        respond(
                            content = """{"token_type":"Bearer","expires_in":2415600,""" +
                                """"access_token":"${r.accessToken}","refresh_token":"${r.refreshToken}"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    is RefreshResponse.Rejected -> respond(
                        content = r.errorCode?.let { """{"error":"$it","message":"rejected"}""" } ?: "",
                        status = r.status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )

                    is RefreshResponse.ServerError -> respondError(r.status)
                    RefreshResponse.TransportFailure -> throw IOException("connection reset")
                }
            }

            request.url.encodedPath.endsWith("/users/@me/animelist") -> {
                animeListRequests += request.url
                animeListAuthorizations += request.headers[HttpHeaders.Authorization].orEmpty()
                holdAnimeList(request.url.parameters["offset"]?.toInt() ?: 0)
                val presented = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                if (presented == null || presented !in acceptedAccessTokens) {
                    respond(
                        content = """{"error":"invalid_token","message":"expired"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    when (val r = animeList(request.url.parameters["offset"]?.toInt() ?: 0)) {
                        is AnimeListResponse.Page -> respond(
                            content = r.json(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        is AnimeListResponse.Failure -> respond(
                            content = """{"error":"server_error","message":"boom"}""",
                            status = r.status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        AnimeListResponse.TransportFailure -> throw IOException("connection reset")
                    }
                }
            }

            LIST_STATUS_PATH.matches(request.url.encodedPath) -> {
                val id = request.url.encodedPath.removeSuffix("/my_list_status").substringAfterLast('/').toLong()
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                val patch = ListStatusPatch(
                    method = request.method,
                    url = request.url,
                    contentType = request.body.contentType?.toString(),
                    form = body.parseUrlEncodedParameters(),
                )
                listStatusPatches += patch
                holdListStatusUpdate(id)
                val presented = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                if (presented == null || presented !in acceptedAccessTokens) {
                    respond(
                        content = """{"error":"invalid_token","message":"expired"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    when (val r = updateListStatus(id, patch.form)) {
                        is ListStatusResponse.Saved -> respond(
                            content = r.status.json(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        is ListStatusResponse.Failure -> respond(
                            content = """{"message":"invalid score","error":"bad_request"}""",
                            status = r.status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        ListStatusResponse.TransportFailure -> throw IOException("connection reset")
                    }
                }
            }

            ANIME_DETAILS_PATH.matches(request.url.encodedPath) -> {
                animeDetailsRequests += request.url
                val id = request.url.encodedPath.substringAfterLast('/').toLong()
                holdAnimeDetails(id)
                val presented = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                if (presented == null || presented !in acceptedAccessTokens) {
                    respond(
                        content = """{"error":"invalid_token","message":"expired"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    when (val r = animeDetails(id)) {
                        is AnimeDetailsResponse.Found -> respond(
                            content = r.anime.json(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        AnimeDetailsResponse.NotFound -> respond(
                            content = """{"message":"","error":"not_found"}""",
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        is AnimeDetailsResponse.Failure -> respond(
                            content = """{"error":"server_error","message":"boom"}""",
                            status = r.status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        AnimeDetailsResponse.TransportFailure -> throw IOException("connection reset")
                    }
                }
            }

            request.url.encodedPath.endsWith("/users/@me") -> {
                userEndpointHits++
                val presented = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                if (presented != null && presented in acceptedAccessTokens) {
                    onAuthorizedRequest(request)
                    respond(
                        content = USER_JSON,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    // Deliberately *no* WWW-Authenticate by default: with exactly one provider
                    // installed, Ktor refreshes on a bare 401 anyway, and that is the behaviour
                    // this app depends on because MAL's challenge header is not guaranteed.
                    respond(
                        content = """{"error":"invalid_token","message":"expired"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = if (sendChallengeHeader) {
                            headersOf(HttpHeaders.WWWAuthenticate, listOf("""Bearer realm="mal""""))
                        } else {
                            headersOf(HttpHeaders.ContentType, "application/json")
                        },
                    )
                }
            }

            else -> respondError(HttpStatusCode.NotFound, "unexpected ${request.url}")
        }
    }

    val clientFactory: HttpClientFactory = HttpClientFactory { configure -> HttpClient(engine) { configure() } }
}
