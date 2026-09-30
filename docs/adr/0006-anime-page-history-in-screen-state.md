# The Anime Page's history is a value in Screen State, not a navigation library

Status: accepted

The Anime Page is the app's first destination that the Session does not decide, and it can go several deep through
Related Anime. It is held as a **history of open Anime Pages inside `ScreenState.SignedIn`**, produced in `:core` like
everything else on that screen — not as a `NavHost` from `androidx.navigation`, and not as `remember`ed state in a
composable. A reader coming from Android will expect a navigation library. This file is here so they don't add one.

## Why

- **The same value has two presentations.** At 840dp and wider the Anime Page is a side panel beside the Anime List; below
  that it replaces the list. A window resized across the line has to keep the same page open. With the history as data,
  that is one `BoxWithConstraints` choosing how to draw it; a nav graph would have to model a two-pane destination that
  is also a full-screen one.
- **Its lifetime is the Session's, by rule.** The pages close when the Session ends, and only then — not on a filter
  change, a Reload or a back stack cleared by a configuration change. That is the rule `AnimeListRepository` already
  keeps for the list (ADR-0004), and a navigation library's back stack would be a second owner of the same lifetime.
- **It stays testable on every Target.** Opening, going back, closing and "the Session ended" are assertions over a value
  in `:core:allTests`, the argument ADR-0004 and ADR-0005 already make.

## Considered options

- **`androidx.navigation` for Compose Multiplatform.** Rejected for the reasons above. It would also bring browser-history
  integration, which was deliberately left out: the Anime Page has no URL for now.
- **State local to the signed-in composable.** Rejected. It would be lost on recreation, it could not be tested outside
  jvm, and it would put an editable page's source of truth below the tier that saves the edits.

## Consequences

Back is handled by hand: Android's back gesture and desktop's Esc go one page back, and ✕ closes them all. Browser Back
is not intercepted. A web URL for an Anime Page can be added later by mapping to and from this value; nothing here
precludes it.
