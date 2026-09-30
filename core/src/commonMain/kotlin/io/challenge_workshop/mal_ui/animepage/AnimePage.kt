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

/**
 * One of an anime's Related Anime: enough to draw a row, and to open its Anime Page drawn at once.
 *
 * [onList] is whether the user already has a List Entry for it, from the same fetch as the page
 * itself (`related_anime{my_list_status}`), so no follow-up request is made for the mark.
 */
data class RelatedAnime(
    val animeId: Long,
    val title: String,
    val picture: AnimePicture?,
    /** MAL's `relation_type_formatted`: "Sequel", "Side Story", ... */
    val relation: String,
    val onList: Boolean,
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
    /** In MAL's order. */
    val related: List<RelatedAnime> = emptyList(),
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
 * Where saving the user's edits to one Anime Page has got to.
 *
 * [target] is what the user wants the entry to be, and **is what the page shows** while it is set.
 * [inFlight] is the target as it stood when the PATCH now at MAL was sent: at most one exists per
 * anime, so saves cannot arrive out of order, and edits made meanwhile only move [target]. When the
 * PATCH is answered, another is sent if [target] has moved on from [inFlight]. It is **not**
 * compared with MAL's answer, which can legitimately differ (progress is clamped, a score rounded)
 * and would otherwise be chased for ever.
 *
 * [error] is why the last save was refused. The page has by then gone back to MAL's last confirmed
 * values, and the next edit clears it.
 */
data class PageSave(
    val target: MyListStatus? = null,
    val inFlight: MyListStatus? = null,
    val error: String? = null,
)

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
    val save: PageSave = PageSave(),
    /** Empty until the fetch lands. */
    val related: List<RelatedAnime> = emptyList(),
) {
    /** What to draw: the pending target if there is one, otherwise what MAL last confirmed. */
    val shownListStatus: MyListStatus? get() = save.target ?: listStatus

    /** Whether a change of the user's is not yet confirmed by MAL. */
    val isSaving: Boolean get() = save.target != null || save.inFlight != null

    /**
     * Whether the List Entry may be edited: only once the fetch has succeeded, because its dates
     * decide whether an automatic rule may fill them and the list row it opened from may be stale.
     * An anime that is not on the list has no entry to edit.
     */
    val canEdit: Boolean get() = load == AnimePageLoad.Loaded && listStatus != null

    /** This page with the fetch's answer in place of what it opened with. */
    internal fun loadedWith(details: AnimeDetails): AnimePage = copy(
        title = details.title,
        picture = details.picture ?: picture,
        totalEpisodes = details.totalEpisodes,
        mediaType = details.mediaType,
        airingStatus = details.airingStatus,
        listStatus = details.listStatus,
        synopsis = details.synopsis,
        related = details.related,
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

        /**
         * A page opened from a Related Anime: only its cover and title are known, and whether it is
         * on the list is left to the fetch, so there is no List Entry to show until it lands.
         */
        internal fun from(related: RelatedAnime): AnimePage = AnimePage(
            animeId = related.animeId,
            title = related.title,
            picture = related.picture,
            totalEpisodes = 0,
            mediaType = null,
            airingStatus = AiringStatus.Unknown,
            listStatus = null,
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
