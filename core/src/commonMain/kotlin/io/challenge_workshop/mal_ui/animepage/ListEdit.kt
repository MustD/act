package io.challenge_workshop.mal_ui.animepage

import io.challenge_workshop.mal_ui.animelist.WatchStatus
import kotlinx.datetime.LocalDate

/**
 * One change the user made to their List Entry on an Anime Page.
 *
 * An edit is a function from the entry as it is shown (the pending target, if there is one) to the
 * entry that is now wanted, so **three quick "+1"s are three additions to a target and not three
 * requests**. Whatever an edit implies beyond what was asked — the automatic rules — belongs in
 * [applyTo] and nowhere else.
 */
sealed interface ListEdit {
    /** The entry [status] becomes under this edit, for an anime with [totalEpisodes] (0 when unknown). */
    fun applyTo(status: MyListStatus, totalEpisodes: Int): MyListStatus

    data class SetWatchStatus(val watchStatus: WatchStatus) : ListEdit {
        override fun applyTo(status: MyListStatus, totalEpisodes: Int) = status.copy(watchStatus = watchStatus)
    }

    /** Sets progress, held to `0…total`, or to `0…` while the total is unknown. */
    data class SetEpisodes(val count: Int) : ListEdit {
        override fun applyTo(status: MyListStatus, totalEpisodes: Int) =
            status.copy(episodesWatched = count.withinTotal(totalEpisodes))
    }

    /** Moves progress by [delta] from what is shown, held to the same bounds. */
    data class AddEpisodes(val delta: Int) : ListEdit {
        override fun applyTo(status: MyListStatus, totalEpisodes: Int) =
            status.copy(episodesWatched = (status.episodesWatched.toLong() + delta).coerceIn(0, Int.MAX_VALUE.toLong())
                .toInt().withinTotal(totalEpisodes))
    }

    /** 0–10, where 0 is "no score". */
    data class SetScore(val score: Int) : ListEdit {
        override fun applyTo(status: MyListStatus, totalEpisodes: Int) = status.copy(score = score.coerceIn(0, 10))
    }

    /** A null [date] clears it. */
    data class SetStartDate(val date: LocalDate?) : ListEdit {
        override fun applyTo(status: MyListStatus, totalEpisodes: Int) = status.copy(startDate = date?.toString())
    }

    data class SetFinishDate(val date: LocalDate?) : ListEdit {
        override fun applyTo(status: MyListStatus, totalEpisodes: Int) = status.copy(finishDate = date?.toString())
    }
}

private fun Int.withinTotal(totalEpisodes: Int): Int =
    if (totalEpisodes > 0) coerceIn(0, totalEpisodes) else coerceAtLeast(0)

/**
 * The PATCH that takes an entry from [from] to this one: only the fields that differ, since a field
 * left out is kept by MAL and one sent is a write.
 *
 * A date is compared as the string MAL holds, so a partial `2024` MAL sent and the user did not
 * touch is never a difference. One that is set must be a real date to be sent at all — an impossible
 * one is stored by MAL as cleared, without an error — and anything else is treated as unchanged.
 */
internal fun MyListStatus.diffFrom(from: MyListStatus): ListStatusUpdate = ListStatusUpdate(
    watchStatus = watchStatus.takeIf { it != from.watchStatus },
    score = score.takeIf { it != from.score },
    episodesWatched = episodesWatched.takeIf { it != from.episodesWatched },
    startDate = dateUpdate(startDate, from.startDate),
    finishDate = dateUpdate(finishDate, from.finishDate),
)

private fun dateUpdate(wanted: String?, held: String?): DateUpdate? = when {
    wanted == held -> null
    wanted == null -> DateUpdate.Clear
    else -> runCatching { LocalDate.parse(wanted) }.getOrNull()?.let { DateUpdate.Set(it) }
}
