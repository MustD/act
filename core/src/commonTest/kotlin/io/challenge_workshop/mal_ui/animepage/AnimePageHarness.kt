@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AnimeListContent
import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.animelist.AnimeListResponse
import io.challenge_workshop.mal_ui.animelist.FakeEntry
import io.challenge_workshop.mal_ui.session.FakeClock
import io.challenge_workshop.mal_ui.session.FakeKeyValueStore
import io.challenge_workshop.mal_ui.session.FakeMal
import io.challenge_workshop.mal_ui.session.JsonTokenStore
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.TEST_CONFIG
import io.challenge_workshop.mal_ui.session.TEST_USER
import io.challenge_workshop.mal_ui.session.VALID_TOKENS
import io.ktor.http.Parameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate

/**
 * The Anime Page history over a **real** [MalSessionRepository] and [AnimeListRepository] and the
 * fake MAL behind them, for the reason `AnimeListRepositoryTest` is. What is asserted is what a
 * caller can see: the history, the list's entries, and the requests MAL was asked.
 */
internal class Harness(
    val mal: FakeMal,
    val session: MalSessionRepository,
    val list: AnimeListRepository,
    val pages: AnimePageRepository,
) {
    val history: AnimePageHistory get() = pages.state.value

    suspend fun awaitPage(predicate: (AnimePage) -> Boolean): AnimePage =
        pages.state.first { it.current?.let(predicate) == true }.current!!

    suspend fun awaitLoaded(): AnimePage = awaitPage { it.load == AnimePageLoad.Loaded }

    suspend fun awaitEntries(): List<AnimeListEntry> =
        (list.state.first { s -> s.content.let { it is AnimeListContent.Entries && !it.replacing } }.content
            as AnimeListContent.Entries).entries

    fun entry(id: Long): AnimeListEntry = awaitedEntries.first { it.animeId == id }

    var awaitedEntries: List<AnimeListEntry> = emptyList()
}

internal suspend fun TestScope.harness(
    listEntries: List<FakeEntry> = listOf(
        FakeEntry(1, "One", watchStatus = "watching", score = 5, watched = 3),
        FakeEntry(2, "Two"),
        FakeEntry(3, "Three"),
    ),
    animeDetails: (Long) -> AnimeDetailsResponse = {
        AnimeDetailsResponse.Found(FakeAnimeDetails(it, "Anime $it"))
    },
    holdAnimeDetails: suspend (Long) -> Unit = {},
    updateListStatus: (Long, Parameters) -> ListStatusResponse = { _, _ -> ListStatusResponse.Failure() },
    holdListStatusUpdate: suspend (Long) -> Unit = {},
    today: LocalDate = LocalDate(2026, 9, 30),
): Harness {
    val mal = FakeMal(
        acceptedAccessToken = VALID_TOKENS.accessToken,
        animeList = { AnimeListResponse.Page(listEntries, hasMore = false) },
        animeDetails = animeDetails,
        holdAnimeDetails = holdAnimeDetails,
        updateListStatus = updateListStatus,
        holdListStatusUpdate = holdListStatusUpdate,
    )
    val store = JsonTokenStore(FakeKeyValueStore(), clock = FakeClock())
    store.writeSession(VALID_TOKENS, TEST_USER)
    val session = MalSessionRepository(
        store = store,
        clock = FakeClock(),
        initialConfig = TEST_CONFIG,
        clientFactory = mal.clientFactory,
    )
    val list = AnimeListRepository(session, backgroundScope)
    val pages = AnimePageRepository(session, list, backgroundScope, today = { today })
    session.restore()
    val h = Harness(mal, session, list, pages)
    h.awaitedEntries = h.awaitEntries()
    return h
}
