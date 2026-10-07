package io.challenge_workshop.mal_ui.auth

/**
 * The four destinations of the routing `when` in [io.challenge_workshop.mal_ui.SessionRoute], named so
 * a test can ask which one is on screen.
 *
 * These live in `commonMain` rather than in the test source set on purpose: they are the contract
 * between the routing `when` and the test that asserts that `when` is total. One enum entry per
 * [io.challenge_workshop.mal_ui.screen.ScreenState] variant — the `when` is over `ScreenState` now,
 * not over `SessionState` — which is what lets the test compare its own coverage against the sealed
 * interface instead of trusting a hand-written list.
 */
enum class SessionScreenTag {
    Restoring,
    SignIn,
    Authorizing,
    SignedIn,
    ;

    val tag: String get() = "sessionScreen.$name"
}

/**
 * The [io.challenge_workshop.mal_ui.session.SignedOutReason] explanation on the sign-in screen.
 *
 * Tagged separately because the thing worth asserting about it is its *text* — the whole point of
 * `SignedOutReason` is that an expired session does not read like a deliberate sign-out — and there
 * is no other way to pick one paragraph out of a screen without pinning the test to its copy.
 */
val SIGNED_OUT_REASON_TAG: String = "${SessionScreenTag.SignIn.tag}.reason"

/**
 * The Anime List on the signed-in screen — the lazy list itself, which is also the signed-in
 * screen's one scroll container.
 *
 * Derived from [SessionScreenTag.SignedIn] rather than being a scheme of its own: the thing worth
 * asserting is that the signed-in branch renders the list, so the two names should not be able to
 * drift apart.
 *
 * On the *scroll container* rather than on a wrapper, so a test can scroll it. Paging is triggered
 * by proximity to the end, so a test that cannot scroll cannot reach the behaviour at all.
 */
val ANIME_LIST_TAG: String = "${SessionScreenTag.SignedIn.tag}.animeList"

/**
 * The row at the bottom of the Anime List that says whether more is coming.
 *
 * Its own tag because it is the only part of the list that is about *paging* rather than about an
 * entry, and both of the things it can say — loading more, and the retry after a failed later page
 * — are invisible in an assertion over entries.
 */
val ANIME_LIST_MORE_TAG: String = "$ANIME_LIST_TAG.more"

/**
 * The first-page placeholder: the Anime List's own shape, drawn with nothing in it.
 *
 * Its own tag because "is the skeleton on screen" is a question about the drawing, not the value —
 * `FirstPageLoading` says the screen should be a skeleton, not that it drew one rather than a
 * spinner, and the difference between the two is the whole of the state.
 *
 * **On every placeholder, not on a wrapper around them**, so a test asks `onAllNodesWithTag`. The
 * placeholders are grid cells like the entries that replace them — which is the only way they can
 * be laid out at the same column width — and a wrapper is exactly the thing a grid cannot lay out.
 */
val ANIME_LIST_SKELETON_TAG: String = "$ANIME_LIST_TAG.skeleton"

/**
 * The message shown when the Anime List has nothing in it.
 *
 * One tag for both of the empty states rather than two, because what a test needs to assert is
 * exactly the thing that separates them — the *words* — and a second tag would let the two branches
 * be asserted without ever comparing their copy. Collapsing "your list is empty" into "this filter
 * matched nothing" is the easiest mistake in this feature.
 */
val ANIME_LIST_EMPTY_TAG: String = "$ANIME_LIST_TAG.empty"

/**
 * The full-width error shown when the *first* page failed — nothing loaded, so the error is the
 * screen.
 *
 * Distinct from [ANIME_LIST_MORE_TAG], which is the other failure: entries on screen, retry at the
 * bottom, nothing discarded. Two tags because a test that could not tell them apart is the same test
 * the pager keeps two error fields to make possible.
 */
val ANIME_LIST_ERROR_TAG: String = "$ANIME_LIST_TAG.error"

/**
 * The Watch Status filter row above the Anime List.
 *
 * Sibling of the list rather than an item in it: the filter is a control over the list and has to
 * stay reachable from wherever the user has scrolled to, and a control that scrolls away from a list
 * fifty entries at a time is one the user cannot get back to.
 */
val ANIME_LIST_FILTERS_TAG: String = "${SessionScreenTag.SignedIn.tag}.filters"

/**
 * The Sort Order control above the Anime List — the button that names the current ordering.
 *
 * A sibling of the list for the same reason the filter row is: it is a control over the list, and
 * one that scrolls away from it fifty entries at a time is one the user cannot get back to.
 */
val ANIME_LIST_SORT_TAG: String = "${SessionScreenTag.SignedIn.tag}.sort"

/**
 * The Layout toggle above the Anime List — the control that picks cards or the dense list.
 *
 * A sibling of the list like the other two controls, and for one more reason besides theirs: it is
 * the only control on this screen whose choice is written to the store, so a test that rebuilds the
 * screen has to be able to find it and read which half is selected. (Written, not necessarily
 * durable: on the web Targets the store is `sessionStorage` and goes with the tab — see
 * `LayoutPreference.value`.)
 */
val ANIME_LIST_LAYOUT_TAG: String = "${SessionScreenTag.SignedIn.tag}.layout"

/**
 * The top app bar over the signed-in screen — the chrome that carries the user's name, the Layout
 * toggle and the overflow menu.
 *
 * Derived from [SessionScreenTag.SignedIn] like the Anime List's own tags, because it is part of the
 * same screen: the signed-in branch **is** the Anime List, and the bar is what stops that costing
 * the profile row and the debug panel.
 */
val SESSION_TOP_BAR_TAG: String = "${SessionScreenTag.SignedIn.tag}.topBar"

/** The Theme button on the prompt row. */
val SESSION_THEME_TAG: String = "$SESSION_TOP_BAR_TAG.theme"

/** The screen title under the prompt row: the bare filter label. */
val ANIME_LIST_TITLE_TAG: String = "${SessionScreenTag.SignedIn.tag}.title"

/**
 * The user's name in the top app bar.
 *
 * Its own tag because the thing worth asserting about it is its *shape* rather than its text: a name
 * has no length limit and this bar has one line, so a test measures the node and compares it against
 * a short name's. Truncation is not in the semantics tree — a wrapped name and an ellipsised one
 * both read back as the same string — so there is nothing else to ask.
 */
val SESSION_USER_NAME_TAG: String = "$SESSION_TOP_BAR_TAG.userName"

/**
 * The button that opens the overflow menu.
 *
 * Its own tag because the menu is only reachable through it: the three entries do not exist in the
 * tree until it is clicked, so a test that could not find the button could not assert on any of
 * them. By tag and not by its label, because the label is the one part of it with no meaning — it
 * says "More" only because this project pulls in no Material icon dependency to draw three dots
 * with.
 */
val SESSION_MENU_BUTTON_TAG: String = "$SESSION_TOP_BAR_TAG.moreButton"

/**
 * The overflow menu itself, once opened: Reload, Sign out, Session diagnostics.
 *
 * Its own tag because what is worth asserting is *what
 * is in it*, and the three entries in it are the three things the Anime List taking over this screen
 * could otherwise have cost.
 */
val SESSION_MENU_TAG: String = "$SESSION_TOP_BAR_TAG.menu"

/**
 * The dialog the Session diagnostics menu entry opens, holding [SessionDebugPanel].
 *
 * **Not a third destination.** `App.kt`'s `when` over `ScreenState` stays a four-branch switch and
 * no navigation library is added — a dialog is drawn over the signed-in screen, which is still the
 * screen underneath it.
 */
val SESSION_DIAGNOSTICS_TAG: String = "${SessionScreenTag.SignedIn.tag}.diagnostics"

/** Each of Watch Status, progress and score on a wide List row. A stacked row has none. */
val ANIME_LIST_ROW_COLUMN_TAG: String = "$ANIME_LIST_TAG.rowColumn"

/**
 * The Anime Page, on the signed-in screen. Derived from [SessionScreenTag.SignedIn] like the list's
 * own tags: an open page shares the screen with the list or replaces it, so the two are answers to the same question.
 */
val ANIME_PAGE_TAG: String = "${SessionScreenTag.SignedIn.tag}.animePage"

/** The page's ← button on a phone, where it also closes the last page; in the side panel, only with a page to go back to. */
val ANIME_PAGE_BACK_TAG: String = "$ANIME_PAGE_TAG.back"

/** The side panel's ✕ button, which closes the whole history. */
val ANIME_PAGE_CLOSE_TAG: String = "$ANIME_PAGE_TAG.close"

/** The page's open-on-myanimelist.net button. */
val ANIME_PAGE_OPEN_TAG: String = "$ANIME_PAGE_TAG.open"

/** The page's path in its top bar. */
val ANIME_PAGE_PATH_TAG: String = "$ANIME_PAGE_TAG.path"

/** The score's clear action. */
val ANIME_PAGE_SCORE_CLEAR_TAG: String = "$ANIME_PAGE_TAG.scoreClear"

/** The placeholder shown while `GET /anime/{id}` is in flight, over what the page opened with. */
val ANIME_PAGE_LOADING_TAG: String = "$ANIME_PAGE_TAG.loading"

/** The failed-fetch card and its retry. Nothing becomes editable behind it. */
val ANIME_PAGE_ERROR_TAG: String = "$ANIME_PAGE_TAG.error"

/** The `more ▾` / `less ▴` toggle under a clamped synopsis, present only when the text overflows three lines. */
val ANIME_PAGE_SYNOPSIS_TOGGLE_TAG: String = "$ANIME_PAGE_TAG.synopsisToggle"

/** The synopsis text, which is the one thing only the fetch can supply. */
val ANIME_PAGE_SYNOPSIS_TAG: String = "$ANIME_PAGE_TAG.synopsis"

/** The user's List Entry section: the fields, or "Add to list as…" for an anime that is not on their list. */
val ANIME_PAGE_LIST_ENTRY_TAG: String = "$ANIME_PAGE_TAG.listEntry"

/** The Anime Page when it sits beside the list. Absent below the side-panel width, where the page is the screen. */
val ANIME_PAGE_PANEL_TAG: String = "$ANIME_PAGE_TAG.panel"

/**
 * "Saving…" beside one List Entry field — the one whose tag is [fieldTag] — while a change to it is
 * not yet confirmed by MAL. Per field, so a test can tell *which* change is pending.
 */
fun animePageSavingTag(fieldTag: String): String = "$fieldTag.saving"

/** Why the last save was refused. The fields have already gone back to what MAL holds. */
val ANIME_PAGE_SAVE_ERROR_TAG: String = "$ANIME_PAGE_TAG.saveError"

/** The "Add to list as…" button, shown instead of the List Entry's fields for an anime that is not on the list. */
val ANIME_PAGE_ADD_TAG: String = "$ANIME_PAGE_TAG.add"

/** The Watch Status button, which opens the picker. */
val ANIME_PAGE_WATCH_STATUS_TAG: String = "$ANIME_PAGE_TAG.watchStatus"

/** The progress number field, and the − and + beside it. */
val ANIME_PAGE_EPISODES_TAG: String = "$ANIME_PAGE_TAG.episodes"
val ANIME_PAGE_EPISODES_MINUS_TAG: String = "$ANIME_PAGE_TAG.episodesMinus"
val ANIME_PAGE_EPISODES_PLUS_TAG: String = "$ANIME_PAGE_TAG.episodesPlus"

/** The score button, which opens the picker of MAL's labels. */
val ANIME_PAGE_SCORE_TAG: String = "$ANIME_PAGE_TAG.score"

/** Each date's value as MAL holds it, then its `set today` action (which opens the DatePicker) and its `clear` action. */
val ANIME_PAGE_START_DATE_TAG: String = "$ANIME_PAGE_TAG.startDate"
val ANIME_PAGE_START_DATE_SET_TAG: String = "$ANIME_PAGE_TAG.startDateSet"
val ANIME_PAGE_START_DATE_CLEAR_TAG: String = "$ANIME_PAGE_TAG.startDateClear"
val ANIME_PAGE_FINISH_DATE_TAG: String = "$ANIME_PAGE_TAG.finishDate"
val ANIME_PAGE_FINISH_DATE_SET_TAG: String = "$ANIME_PAGE_TAG.finishDateSet"
val ANIME_PAGE_FINISH_DATE_CLEAR_TAG: String = "$ANIME_PAGE_TAG.finishDateClear"

/** The Related Anime section, absent until the fetch has landed and when the anime has none. */
val ANIME_PAGE_RELATED_TAG: String = "$ANIME_PAGE_TAG.related"

/** One Related Anime row, which opens its own Anime Page. */
fun animePageRelatedTag(animeId: Long): String = "$ANIME_PAGE_TAG.related.$animeId"

/** The "On your list" mark on a Related Anime row. */
fun animePageRelatedOnListTag(animeId: Long): String = "$ANIME_PAGE_TAG.related.$animeId.onList"

/** The 208dp sidebar shown from [SIDE_PANEL_MIN_WIDTH]; it replaces the tabs and the More menu. */
val SESSION_SIDEBAR_TAG: String = "${SessionScreenTag.SignedIn.tag}.sidebar"
