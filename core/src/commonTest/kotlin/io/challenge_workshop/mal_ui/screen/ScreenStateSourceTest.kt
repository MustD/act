package io.challenge_workshop.mal_ui.screen

import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.Anime
import io.challenge_workshop.mal_ui.animelist.AnimeListContent
import io.challenge_workshop.mal_ui.animelist.AnimeListLayout
import io.challenge_workshop.mal_ui.animelist.AnimeListState
import io.challenge_workshop.mal_ui.animelist.AnimeListTail
import io.challenge_workshop.mal_ui.animelist.WatchStatus
import io.challenge_workshop.mal_ui.animepage.AnimePage
import io.challenge_workshop.mal_ui.animepage.AnimePageHistory
import io.challenge_workshop.mal_ui.animepage.AnimePageLoad
import io.challenge_workshop.mal_ui.auth.SignInPhase
import io.challenge_workshop.mal_ui.auth.SignInState
import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.challenge_workshop.mal_ui.mal.MalEndpoints
import io.challenge_workshop.mal_ui.mal.MalUser
import io.challenge_workshop.mal_ui.mal.platformMalEndpoints
import io.challenge_workshop.mal_ui.session.PendingAuthorization
import io.challenge_workshop.mal_ui.session.SessionControlsState
import io.challenge_workshop.mal_ui.session.SessionDiagnostics
import io.challenge_workshop.mal_ui.session.SessionOperation
import io.challenge_workshop.mal_ui.session.SessionState
import io.challenge_workshop.mal_ui.session.SignedOutReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mapping from seven flows to one [ScreenState], on every Target.
 *
 * Driven by seven [MutableStateFlow]s and no fakes at all: there is no repository here, no store, no
 * HTTP engine and no port to bind, which is the entire point of the seam. Everything a screen can be
 * is a literal, and every assertion below used to need a rendered Compose tree on jvm.
 *
 * [Dispatchers.Unconfined] rather than a test dispatcher, so an assignment to one of the seven inputs
 * has reached [ScreenStateSource.state] by the time the next line runs. The combine is pure, so
 * running it inline on the caller's thread is exactly what it does in the app on
 * `Dispatchers.Main.immediate`.
 */
class ScreenStateSourceTest {

    private val session = MutableStateFlow<SessionState>(SessionState.Restoring)
    private val config = MutableStateFlow(MalAuthConfig(clientId = "a-client-id", redirectUri = REDIRECT_URI))
    private val animeList = MutableStateFlow(AnimeListState())
    private val layout = MutableStateFlow(AnimeListLayout.Cards)
    private val animePages = MutableStateFlow(AnimePageHistory())
    private val signIn = MutableStateFlow(SignInState())
    private val controls = MutableStateFlow(SessionControlsState())

    private val source = ScreenStateSource(
        session = session,
        config = config,
        animeList = animeList,
        layout = layout,
        animePages = animePages,
        signIn = signIn,
        controls = controls,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private val state: ScreenState get() = source.state.value

    /**
     * Every `SessionState` subtype maps to a [ScreenState], and no two map to the same one.
     *
     * The cases are [SESSION_STATE_CASES], and `ScreenStateCoverageTest` in `jvmTest` is what holds
     * that list to the sealed interface — `KClass.sealedSubclasses` exists only on jvm, so the
     * *enumeration* is one Target's and the *mapping* asserted here is every Target's.
     */
    @Test
    fun every_session_state_produces_a_screen_state() {
        val produced = SESSION_STATE_CASES.associate { given ->
            session.value = given
            given::class.simpleName to state::class.simpleName
        }

        assertEquals(
            SESSION_STATE_CASES.size,
            produced.values.toSet().size,
            "Two SessionStates produced the same ScreenState: $produced",
        )
    }

    /**
     * The explanations differ from each other — the whole reason [SignedOutReason] is carried
     * rather than collapsed into a bare "signed out" — the expired one says so, and a first visit
     * has none.
     */
    @Test
    fun each_signed_out_reason_explains_itself_differently() {
        val explanations = SignedOutReason.entries.associateWith { reason ->
            session.value = SessionState.SignedOut(reason)
            assertIs<ScreenState.SignedOut>(state).explanation
        }

        assertNull(explanations.getValue(SignedOutReason.NeverSignedIn))
        val shown = explanations.filterKeys { it != SignedOutReason.NeverSignedIn }.values
        assertTrue(shown.all { it != null })
        assertEquals(
            SignedOutReason.entries.size - 1,
            shown.toSet().size,
            "Two reasons share the same copy, which is the bare 'signed out' this enum exists to " +
                "avoid: $explanations",
        )
        val expired = explanations.getValue(SignedOutReason.RefreshRejected)
        assertTrue("expired" in expired.orEmpty().lowercase(), expired)
    }

    /**
     * The authorization URL Paste-the-code offers, rebuilt from the Pending Authorization rather than
     * stored — which works at all only because MAL supports `plain` PKCE, so the challenge *is* the
     * verifier.
     *
     * The verifier being visible in the string is asserted on purpose: it is what makes this URL a
     * thing that must never be logged, and a future change that started hashing the challenge would
     * break the sign-in and this line together.
     */
    @Test
    fun authorizing_carries_the_url_paste_the_code_offers() {
        session.value = SessionState.Authorizing(pendingAuthorization())

        val url = assertIs<ScreenState.Authorizing>(state).authorizationUrl

        assertTrue(url.startsWith(MalAuthConfig.DEFAULT_AUTHORIZE_ENDPOINT), url)
        for (expected in listOf(
            "client_id=a-client-id",
            "code_challenge=a-verifier",
            "code_challenge_method=plain",
            "state=a-state",
        )) {
            assertTrue(expected in url, "missing `$expected` in $url")
        }
        assertTrue("oauth%2Fcallback" in url || "oauth/callback" in url, url)
    }

    /**
     * The Pending Authorization's own Client ID wins over whatever is in the config now.
     *
     * A user who started signing in, updated the app to a build with another Client ID and came
     * back would otherwise be offered a URL for the *new* one — which MAL would reject against a code
     * minted under the old.
     */
    @Test
    fun the_authorization_url_uses_the_client_id_the_sign_in_started_with() {
        session.value = SessionState.Authorizing(pendingAuthorization())
        config.value = config.value.copy(clientId = "a-client-id-typed-later")

        assertTrue(
            "client_id=a-client-id&" in assertIs<ScreenState.Authorizing>(state).authorizationUrl,
        )
    }

    /**
     * The history reaches the signed-in screen as it is — open, deeper, and closed again — and
     * *deciding* when it closes is `AnimePageRepository`'s. Nothing here re-decides it.
     */
    @Test
    fun the_anime_page_history_reaches_the_signed_in_screen_as_it_is() {
        session.value = SessionState.SignedIn(MalUser(1, "someone"))
        assertEquals(AnimePageHistory(), signedIn().animePages, "nothing is open to begin with")

        val first = animePage(1)
        animePages.value = AnimePageHistory(listOf(first))
        assertEquals(listOf(first), signedIn().animePages.pages)
        assertEquals(first, signedIn().animePages.current)

        val second = animePage(2).copy(load = AnimePageLoad.Failed("boom"))
        animePages.value = AnimePageHistory(listOf(first, second))
        assertEquals(second, signedIn().animePages.current, "the newest page is the one on screen")

        animePages.value = AnimePageHistory()
        assertEquals(false, signedIn().animePages.isOpen)
    }

    /** A Reload is the list's, so the list changing under an open page leaves the history alone. */
    @Test
    fun the_history_stays_open_while_the_list_reloads() {
        session.value = SessionState.SignedIn(MalUser(1, "someone"))
        animePages.value = AnimePageHistory(listOf(animePage(1)))

        animeList.value = AnimeListState(content = AnimeListContent.FirstPageLoading)
        animeList.value = AnimeListState(content = AnimeListContent.Empty, revision = 1)

        assertEquals(1L, signedIn().animePages.current?.animeId)
    }

    /**
     * The other three variants have no history to carry: a Session that ended has none, and the
     * repository has by then cleared it. What matters is that a stale one cannot leak onto them.
     */
    @Test
    fun only_the_signed_in_screen_carries_a_history() {
        animePages.value = AnimePageHistory(listOf(animePage(1)))

        for (given in SESSION_STATE_CASES.filter { it !is SessionState.SignedIn }) {
            session.value = given
            assertEquals(false, state is ScreenState.SignedIn, "for $given")
        }
    }

    /**
     * Which Anime List screen the signed-in screen shows is entirely the list's: every variant reaches
     * the screen verbatim, with the query beside it. *Deciding* the variant is the pager's, and
     * `AnimeListPagerTest` covers that; what is asserted here is that nothing on the way re-decides it.
     */
    @Test
    fun every_anime_list_screen_reaches_the_signed_in_screen_as_it_is() {
        session.value = SessionState.SignedIn(MalUser(1, "someone"))
        val variants = listOf(
            AnimeListContent.NotRequested,
            AnimeListContent.FirstPageLoading,
            AnimeListContent.FirstPageFailed("boom"),
            AnimeListContent.Empty,
            AnimeListContent.Entries(emptyList(), AnimeListTail.MoreFailed("boom"), replacing = false),
            AnimeListContent.Entries(emptyList(), AnimeListTail.Idle, replacing = true),
        )

        for (content in variants) {
            // Under a filter, because an empty slice must not read as an empty account: the Watch
            // Status is what separates them, and it travels beside the variant rather than inferred.
            val list = AnimeListState(content = content, watchStatus = WatchStatus.OnHold)
            animeList.value = list
            assertEquals(list, signedIn().list, "for $content")
        }
    }

    /** A Layout change reaches the screen and touches nothing else. */
    @Test
    fun the_layout_reaches_the_signed_in_screen_and_changes_nothing_else() {
        session.value = SessionState.SignedIn(MalUser(1, "someone"))
        animeList.value = AnimeListState(content = AnimeListContent.Empty)
        val before = signedIn()

        layout.value = AnimeListLayout.List

        assertEquals(AnimeListLayout.List, signedIn().layout)
        assertEquals(before, signedIn().copy(layout = AnimeListLayout.Cards))
    }

    /**
     * The refresh flag is the Session's and does not unmount the screen: the list survives it, which
     * is what "a refresh must not blank the signed-in screen" means as a value.
     */
    @Test
    fun a_session_refresh_keeps_the_anime_list() {
        animeList.value = AnimeListState(content = AnimeListContent.Empty, revision = 3)
        session.value = SessionState.SignedIn(MalUser(1, "someone"), refreshing = true)

        assertTrue(signedIn().refreshing)
        assertEquals(3, signedIn().list.revision)
    }

    /**
     * Diagnostics stay out of everything but the signed-in screen, and are absent until something
     * asks for them — the dialog is the only reader, and it only opens deliberately.
     */
    @Test
    fun diagnostics_reach_only_the_signed_in_screen() {
        controls.value = SessionControlsState(
            diagnostics = SessionDiagnostics(
                obtainedAtEpochMs = 1,
                ageMillis = 2,
                accessTokenLength = 3,
                hasRefreshToken = true,
                accessTokenIsDeliberatelyInvalid = false,
            ),
        )

        session.value = SessionState.SignedOut(SignedOutReason.NeverSignedIn)
        assertIs<ScreenState.SignedOut>(state)

        session.value = SessionState.SignedIn(MalUser(1, "someone"))
        assertEquals(3, signedIn().diagnostics?.accessTokenLength)
    }

    /**
     * The Sign-in reaches the two screens that show it, and the signed-in operation reaches the third:
     * `busy` and `error` on [ScreenState.SignedIn] are the operation's, never the Sign-in's.
     */
    @Test
    fun each_screen_reads_its_own_input() {
        signIn.value = SignInState(phase = SignInPhase.Failed("boom"))
        controls.value = SessionControlsState(operation = SessionOperation.Failed("sign-out broke"))

        session.value = SessionState.SignedOut(SignedOutReason.NeverSignedIn)
        assertEquals(signIn.value, assertIs<ScreenState.SignedOut>(state).signIn)

        session.value = SessionState.Authorizing(pendingAuthorization())
        assertEquals(signIn.value, assertIs<ScreenState.Authorizing>(state).signIn)

        session.value = SessionState.SignedIn(MalUser(1, "someone"))
        assertEquals(false to "sign-out broke", signedIn().let { it.busy to it.error?.message })

        controls.value = SessionControlsState(operation = SessionOperation.Running)
        assertEquals(true to null, signedIn().let { it.busy to it.error?.message })
    }

    /**
     * What blocks each of the two things the Sign-in can be asked to do.
     *
     * Both are derived from the phase rather than stored, because a screen that could disagree with
     * the state about whether its own button is enabled is the bug this replaces. Paste stays
     * available while the user is away — that is a rule in the type.
     */
    @Test
    fun a_missing_client_id_blocks_signing_in_and_a_blank_paste_blocks_completing_it() {
        val busy = listOf(SignInPhase.Arming, SignInPhase.Exchanging)
        val free = listOf(SignInPhase.Idle, SignInPhase.AwaitingRedirect, SignInPhase.Failed("x"))

        assertEquals(false, SignInState(clientIdMissing = true).canStart)
        assertEquals(true, SignInState().canStart)
        busy.forEach { assertEquals(false, SignInState(phase = it).canStart, "$it") }
        free.forEach { assertEquals(true, SignInState(phase = it).canStart, "$it") }

        assertEquals(false, SignInState(pastedRedirect = " ").canComplete)
        assertEquals(true, SignInState(pastedRedirect = "x").canComplete)
        busy.forEach { assertEquals(false, SignInState(pastedRedirect = "x", phase = it).canComplete, "$it") }
        free.forEach { assertEquals(true, SignInState(pastedRedirect = "x", phase = it).canComplete, "$it") }
    }

    /**
     * Where this build sends MAL traffic, which the sign-in screen says up front and the debug panel
     * repeats: a browser with no relay running fails at its first request with a bare "Failed to
     * fetch", and nothing else in the app can tell the user why.
     *
     * Asserted against [platformMalEndpoints] rather than against a value pinned by the test, which is
     * what makes this worth running on every Target: it says the browsers are relayed and jvm and
     * android are not, which is the actual claim. A pinned value would have said the same thing three
     * times.
     */
    @Test
    fun the_routing_is_this_targets_own_and_says_whether_it_goes_through_the_relay() {
        session.value = SessionState.SignedOut(SignedOutReason.NeverSignedIn)
        val routing = assertIs<ScreenState.SignedOut>(state).routing

        assertEquals(platformMalEndpoints(), routing.endpoints)
        assertEquals(REDIRECT_URI, routing.redirectUri)
        assertEquals(
            !platformMalEndpoints().tokenEndpoint.startsWith(MalAuthConfig.DEFAULT_TOKEN_ENDPOINT),
            routing.usesRelay,
            "a Target that does not call MAL's own token endpoint is going through the relay",
        )
    }

    /**
     * The relay origin the sign-in screen's warning turns on, from both sides, on whichever Target
     * this is — so the branch is covered even where [platformMalEndpoints] only ever gives one answer.
     */
    @Test
    fun a_relayed_token_endpoint_is_what_makes_a_build_relayed() {
        fun routingFor(tokenEndpoint: String) = MalRouting(
            endpoints = MalEndpoints(tokenEndpoint = tokenEndpoint, apiBaseUrl = "unused"),
            redirectUri = REDIRECT_URI,
        )

        assertEquals(false, routingFor(MalAuthConfig.DEFAULT_TOKEN_ENDPOINT).usesRelay)
        assertEquals(true, routingFor("https://act.io-workshop.localhost/mal/oauth2/token").usesRelay)
    }

    /**
     * The predicate the ViewModel's `withRelayHint` used to be: relayed × fetch-like. Asserted on the
     * routing directly, so all four cells run on every Target, and once through the source for the
     * variant that carries it.
     */
    @Test
    fun a_relay_hint_needs_a_relayed_build_and_an_error_that_reads_like_a_failed_fetch() {
        fun routing(tokenEndpoint: String) = MalRouting(
            endpoints = MalEndpoints(tokenEndpoint = tokenEndpoint, apiBaseUrl = "unused"),
            redirectUri = REDIRECT_URI,
        )
        val relayed = routing("https://act.io-workshop.localhost/mal/oauth2/token")
        val direct = routing(MalAuthConfig.DEFAULT_TOKEN_ENDPOINT)

        assertEquals(true, relayed.suggestsDeadRelay("Failed to fetch"))
        assertEquals(true, relayed.suggestsDeadRelay("Could not reach the server"))
        assertEquals(false, relayed.suggestsDeadRelay("invalid_grant"))
        assertEquals(false, relayed.suggestsDeadRelay(null))
        assertEquals(false, direct.suggestsDeadRelay("Failed to fetch"))
        assertEquals(false, direct.suggestsDeadRelay("invalid_grant"))
    }

    @Test
    fun the_relay_hint_follows_the_error_on_every_variant_that_shows_one() {
        val relayed = platformMalEndpoints().tokenEndpoint != MalAuthConfig.DEFAULT_TOKEN_ENDPOINT

        session.value = SessionState.SignedOut(SignedOutReason.NeverSignedIn)
        signIn.value = SignInState(phase = SignInPhase.Failed("Failed to fetch"))
        assertEquals(relayed, assertIs<ScreenState.SignedOut>(state).signInError?.relayHint)

        session.value = SessionState.Authorizing(pendingAuthorization())
        assertEquals(relayed, assertIs<ScreenState.Authorizing>(state).signInError?.relayHint)

        session.value = SessionState.SignedIn(MalUser(1, "someone"))
        controls.value = SessionControlsState(operation = SessionOperation.Failed("Failed to fetch"))
        assertEquals(relayed, signedIn().error?.relayHint)

        controls.value = SessionControlsState(operation = SessionOperation.Failed("invalid_grant"))
        assertEquals(false, signedIn().error?.relayHint)
    }

    /**
     * The sign-in screen shows two errors in two cards, and each card's advice is about its own text:
     * a dead relay behind the Sign-in's failure says nothing about why the Session ended.
     */
    @Test
    fun each_error_on_the_sign_in_screen_carries_its_own_relay_hint() {
        val relayed = platformMalEndpoints().tokenEndpoint != MalAuthConfig.DEFAULT_TOKEN_ENDPOINT

        session.value = SessionState.SignedOut(SignedOutReason.RefreshRejected, error = "invalid_grant")
        signIn.value = SignInState(phase = SignInPhase.Failed("Failed to fetch"))
        val bothFailed = assertIs<ScreenState.SignedOut>(state)
        assertEquals(false, bothFailed.error?.relayHint)
        assertEquals(relayed, bothFailed.signInError?.relayHint)

        session.value = SessionState.SignedOut(SignedOutReason.RefreshRejected, error = "Failed to fetch")
        signIn.value = SignInState(phase = SignInPhase.Failed("invalid_grant"))
        val swapped = assertIs<ScreenState.SignedOut>(state)
        assertEquals(relayed, swapped.error?.relayHint)
        assertEquals(false, swapped.signInError?.relayHint)
    }

    private fun animePage(id: Long) = AnimePage(
        anime = Anime(
            id,
            "Anime $id",
            picture = null,
            totalEpisodes = 12,
            mediaType = "tv",
            AiringStatus.FinishedAiring
        ),
        listEntry = null,
        synopsis = null,
        load = AnimePageLoad.Loading,
    )

    private fun signedIn(): ScreenState.SignedIn = assertIs<ScreenState.SignedIn>(state)
}

/** Where this Target's tests pretend the desktop listener is. Any registered URI would do. */
internal const val REDIRECT_URI: String = "http://127.0.0.1:18040/oauth/callback"

/**
 * One [SessionState] per subtype, shared between the mapping test and the jvm-only coverage test that
 * holds this list to `SessionState::class.sealedSubclasses`.
 *
 * A `val` outside both, because the thing worth guarding is that the two never diverge: a list the
 * coverage test could not see would be a list nothing holds to the sealed interface.
 */
internal val SESSION_STATE_CASES: List<SessionState> = listOf(
    SessionState.Restoring,
    SessionState.SignedOut(SignedOutReason.NeverSignedIn),
    SessionState.Authorizing(pendingAuthorization()),
    SessionState.SignedIn(MalUser(1, "someone")),
)

internal fun pendingAuthorization() = PendingAuthorization(
    codeVerifier = "a-verifier",
    state = "a-state",
    redirectUri = REDIRECT_URI,
    clientId = "a-client-id",
    startedAtEpochMs = 0L,
)
