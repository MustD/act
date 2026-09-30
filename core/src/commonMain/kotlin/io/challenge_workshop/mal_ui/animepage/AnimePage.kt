package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.AiringStatus
import io.challenge_workshop.mal_ui.animelist.AnimeListEntry
import io.challenge_workshop.mal_ui.animelist.AnimePicture
import io.challenge_workshop.mal_ui.animelist.WatchStatus

/**
 * The user's List Entry for one anime, as `GET /anime/{id}` reports it: what [AnimeListEntry] carries
 * plus the two dates, which the list endpoint is not asked for.
 *
 * The dates are **strings, verbatim**. MAL sends `yyyy-MM-dd`, but also `yyyy` and `yyyy-MM` for a
 * partial date, and a type that could only hold a full date could not show what is on the account.
 * An unset date is null.
 */
data class MyListStatus(
    val watchStatus: WatchStatus,
    /** 0–10, where 0 is "no score". */
    val score: Int,
    val episodesWatched: Int,
    val startDate: String?,
    val finishDate: String?,
    val updatedAt: String?,
)

/** What `GET /anime/{id}` returned: the anime, and the user's entry for it if there is one. */
data class AnimeDetails(
    val animeId: Long,
    val title: String,
    val picture: AnimePicture?,
    val totalEpisodes: Int,
    val mediaType: String?,
    val airingStatus: AiringStatus,
    val synopsis: String?,
    /** Null when the anime is not on the user's list. */
    val listStatus: MyListStatus?,
)

/** Whether an [AnimePage] has what `GET /anime/{id}` has to say yet. */
sealed interface AnimePageLoad {
    /** The fetch is in flight. What the page shows is only what the caller already had. */
    data object Loading : AnimePageLoad

    /** The fetch landed: the synopsis is here and [AnimePage.listStatus] is MAL's current word. */
    data object Loaded : AnimePageLoad

    /** The fetch failed. Nothing beyond what the caller had is known, and a retry asks again. */
    data class Failed(val message: String) : AnimePageLoad
}

/**
 * One open Anime Page.
 *
 * It **opens drawn from what the caller already had** — the list row — so opening one never looks
 * like a load, and [load] says whether the fetch has landed. Until it has, [listStatus] is the list
 * row's, which may be stale and has no dates, and [synopsis] is null.
 *
 * [listStatus] is null for an anime that is not on the user's list.
 */
data class AnimePage(
    val animeId: Long,
    val title: String,
    val picture: AnimePicture?,
    val totalEpisodes: Int,
    val mediaType: String?,
    val airingStatus: AiringStatus,
    val listStatus: MyListStatus?,
    val synopsis: String?,
    val load: AnimePageLoad,
) {
    /** This page with the fetch's answer in place of what it opened with. */
    internal fun loadedWith(details: AnimeDetails): AnimePage = copy(
        title = details.title,
        picture = details.picture ?: picture,
        totalEpisodes = details.totalEpisodes,
        mediaType = details.mediaType,
        airingStatus = details.airingStatus,
        listStatus = details.listStatus,
        synopsis = details.synopsis,
        load = AnimePageLoad.Loaded,
    )

    companion object {
        /** A page opened from a list row: everything the row knew, and the fetch still to come. */
        internal fun from(entry: AnimeListEntry): AnimePage = AnimePage(
            animeId = entry.animeId,
            title = entry.title,
            picture = entry.picture,
            totalEpisodes = entry.totalEpisodes,
            mediaType = entry.mediaType,
            airingStatus = entry.airingStatus,
            listStatus = MyListStatus(
                watchStatus = entry.watchStatus,
                score = entry.score,
                episodesWatched = entry.episodesWatched,
                startDate = null,
                finishDate = null,
                updatedAt = entry.updatedAt,
            ),
            synopsis = null,
            load = AnimePageLoad.Loading,
        )
    }
}

/**
 * The open Anime Pages, oldest first: what `ScreenState.SignedIn` carries. Empty when none is open.
 *
 * A history rather than one page because Related Anime open on top of the page they were found on,
 * and going back has to land on it. See `docs/adr/0006-anime-page-history-in-screen-state.md`.
 */
data class AnimePageHistory(val pages: List<AnimePage> = emptyList()) {
    /** The page on screen: the newest. */
    val current: AnimePage? get() = pages.lastOrNull()

    val isOpen: Boolean get() = pages.isNotEmpty()
}
