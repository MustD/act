# Web Session in `localStorage`

Status: accepted — supersedes [ADR-0001](0001-refresh-token-in-web-session-storage.md)

The web targets keep the Session, the Layout and the Theme in `localStorage`, so opening the page in a new tab or after
a browser restart does not mean signing in again. The Pending Authorization stays in `sessionStorage`. ADR-0001 chose
`sessionStorage` for the whole Session and accepted "closing the tab ends the Session"; in use that turned out to mean
signing in on every visit, and the Layout and Theme — which the glossary says outlive a Session — were lost with it.

## Considered options

- **`sessionStorage` for everything** — ADR-0001. Rejected: every visit is a fresh sign-in.
- **IndexedDB** — async, which fits the `suspend` `KeyValueStore`, but it is no safer than `localStorage` against script
  on the origin and costs roughly 60–100 lines of Wasm promise interop where `localStorage` costs a few `js()` calls.
- **IndexedDB with the token pair encrypted under a non-extractable WebCrypto key** — protects a copied browser profile
  or a leaked backup, and nothing else: injected script can ask the page to decrypt. ADR-0002 already found encryption
  at rest worth about nothing on Android, and a lost key is a silent sign-out. Rejected.
- **Pending Authorization in `localStorage` too** — rejected. A popup receives a *copy* of `sessionStorage`, and the rule
  that it never reads or clears the Pending Authorization is built on that; shared between tabs, two simultaneous
  sign-ins would overwrite each other's PKCE verifier.
- **BFF** — still the actual fix, as in ADR-0001: `:server` already proxies `/mal` same-origin and could hold tokens
  behind an `HttpOnly` cookie. Still not worth it for a personal project.

## Decision

- `JsonTokenStore` takes **two** `KeyValueStore`s: a durable one and a tab-scoped one, and routes `mal.pending.v1` to the
  tab-scoped one. Android and desktop pass the same instance for both. On web the durable one is
  `LocalStorageKeyValueStore` and the tab-scoped one is the existing `SessionStorageKeyValueStore`. This replaces
  ADR-0002's note that the Layout "deliberately" is not split off.
- On startup the web target deletes the Session, Layout and Theme records from `sessionStorage` (as
  `discardLegacyClientId` does for the old Client ID). They are not copied: each open tab signs in once more. This
  cleanup is web-only code, or is guarded on the two stores being different instances: on Android and desktop the
  tab-scoped store *is* the durable one, and a generic "remove these from the tab-scoped store" would delete the real
  Session on every launch.
- `KeyValueStore` gains `fun changes(key: String): Flow<String?>`, **defaulting to `emptyFlow()`**. It emits the new raw
  value, null for removal, for changes made by someone else and never for the caller's own writes. Only
  `LocalStorageKeyValueStore` overrides it, from the browser's `storage` event.
- `MalSessionRepository` collects `changes(mal.session.v1)`. A removal while it holds a Session drops it to `SignedOut`
  with the new Signed Out Reason `SignedOutElsewhere`. A removal in `Restoring`, `Authorizing` or `SignedOut`, and any
  non-null value, is ignored. Dropping to `SignedOutElsewhere` also clears Ktor's cached bearer tokens, as `signOut`
  and `completeAuthorization` do.
- The collector needs a scope the repository does not have today: `MalSessionRepository` takes a `CoroutineScope` in
  its constructor, as `LayoutPreference` and `ThemePreference` do, provided by `appModule`.
- A refresh that finds nothing stored — the existing "refresh racing a sign-out", where `updateTokens` returns null —
  returns null to Ktor, so the fresh pair is neither cached nor retried with. It moves the state to
  `SignedOut(SignedOutElsewhere)` **only if the state is still `SignedIn`**: in a single process the race is with this
  repository's own `signOut()`, which has already set `UserSignedOut`, and that reason must stand.
- A Session written by another tab is not picked up until a reload. A refresh in one tab is harmless to another because
  MAL rotates refresh tokens and leaves the old one valid, so no cross-tab lock (Web Locks) is added.
- The Session has no lifetime of its own beyond MAL's: a refresh MAL rejects ends it, with its existing reason.
- **Desktop takes a single-instance lock** (`FileChannel.tryLock` beside the state directory, taken in
  `:app:desktopApp`'s `main` before the window or `LoopbackRedirectListener`). The second launch shows an "already
  running" dialog and exits; headless it writes to stderr and exits non-zero. Two processes would otherwise share one
  file with only an in-process `Mutex` between them, and could not both sign in anyway since the loopback port is
  fixed (18040). A `WatchService`-based `changes` override was considered and not taken, as no second process now
  exists to observe.

## Consequences

- A refresh token — about a month, as in ADR-0001 — now sits on disk in the browser profile and outlives the browsing
  session. Any script on the origin can read it, as before; additionally it survives until sign-out or MAL rejects it,
  so a shared machine has to sign out explicitly.
- Two tabs can both hold the Session. Sign-out in one signs out the others via `SignedOutElsewhere`; sign-in in one does
  not sign in the others.
- A refresh MAL rejects in one tab clears the shared store, so it ends the Session in every tab: the tab that refreshed
  shows `RefreshRejected`, the others `SignedOutElsewhere`. Accepted — the Session is gone either way, and only the
  tab that saw MAL's answer can name it.
- Web now has two stores behind one `JsonTokenStore`, which is the second persistence rule ADR-0002 avoided; the rule is
  one line (the Pending Authorization is tab-scoped) and lives in `:core` next to the key constants.
- `KeyValueStore` is no longer three methods, though the added one is optional.
- Tests: the store contract runs against `LocalStorageKeyValueStore`; a routing test asserts which backend each record
  lands in; `changes` is driven by a synthetic `StorageEvent`, since a real one never fires in the document that made the
  change; the repository's cases run in `:core` against a fake store. Two real tabs and a browser restart are a manual
  check on `run:web`.
