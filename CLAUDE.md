# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Kotlin Multiplatform / Compose Multiplatform project targeting Android, Web (Wasm), Desktop (JVM), and a Ktor server. Package namespace throughout: `net.io_workshop.act`.

## Commands

**Every command is a mise task.** `mise.toml` is the only place a Gradle command line is written down — the deploy
scripts call the tasks too — so the task names are the interface and the build tool behind them can change. Do not
document or script `./gradlew` directly; add a task. `mise tasks` lists them.

mise also provides the tools: `mise install` once per machine fetches the JDK (`java = "zulu-21"`) and Terraform.
`docker` (with buildx), `ssh` and `dpkg-deb` (from `dpkg`; `pacman -S dpkg` on Arch) are **not** managed by mise — the deploy and desktop-installer tasks expect them on the machine.

Build / run:

```bash
mise run build:android      # Android debug APK
mise run build:android:release  # signed, minified .aab; needs .secure.release.env (the upload key)
mise run run:android        # install the debug build on the real device in ANDROID_TEST_DEVICE_ID and launch it
mise run run:android:release  # the same with the signed, minified release build; needs .secure.release.env
mise run run:desktop        # Desktop
mise run run:desktop:hot    # Desktop with Compose Hot Reload
mise run licences           # regenerate the About Screen's licence list (AboutLibraries) after a dependency change; commit it
mise run run:server         # Ktor server on :18010
mise run run:web            # Web, Wasm target, on :18020; no live reload
mise run run:web:hot        # Web with live reload on change
mise run build:web          # Web development bundle only, no server (build:web:dist is the production one)
mise run build              # everything: every target compiled, every test run
```

`run:web` and `run:web:hot` pass Gradle `--continuous`, which is required for the web target, not just for hot reload: the run task
starts webpack-dev-server without blocking, so without it Gradle finishes (`BUILD SUCCESSFUL` in well under a second)
and takes the dev server down with it, leaving nothing on the port.

The `run:desktop*`, `run:web*`, `run:android` and `build*` tasks load `.secure.build.env` (the Client ID); `run:android` reads `ANDROID_TEST_DEVICE_ID` from it too (the serial from `adb devices`); the `test*` tasks load
nothing, because a test must pass the same with or without one — the two that `deploy` runs inherit its Client ID all
the same. See [Deployment](#deployment) for the env files.

`:server` reads its configuration from the environment (`RelayConfig`, parsed once in `main()` and passed to
`Application.module`); with nothing set it binds `127.0.0.1:18010` and allows `RelayConfig.DEFAULT_CORS_ORIGINS` —
`https://act.io-workshop.localhost`, `https://act.io-workshop.net`, and `http://` `localhost`/`127.0.0.1` on 18020 and
18030. A malformed value fails startup rather than falling back:

| Variable                 | Meaning                                                              |
|--------------------------|----------------------------------------------------------------------|
| `ACT_RELAY_HOST`         | bind address, default `127.0.0.1`                                    |
| `ACT_RELAY_PORT`         | port, default `18010`                                                |
| `ACT_RELAY_CORS_ORIGINS` | comma-separated origins (`http(s)://host[:port]`, no path) replacing the defaults |
| `ACT_RELAY_TRUSTED_PROXIES` | comma-separated CIDRs or bare IPs whose `X-Forwarded-For` is believed; default none (socket address is the client) |
| `ACT_RELAY_TOKEN_LIMIT_PER_MIN` / `ACT_RELAY_API_LIMIT_PER_MIN` | per-client-IP rate limits, defaults 10 on the token POST and 120 on `/mal/v2/*`; 429 beyond |
| `ACT_RELAY_MAX_BODY_BYTES` | cap on the token POST and PATCH bodies, default 16384; 413 beyond |

**Every hop in front of the relay must be in `ACT_RELAY_TRUSTED_PROXIES`**, not only the one it talks to. The client
IP is the right-most `X-Forwarded-For` entry that is not trusted, and behind Caddy behind the edge that header reads
`client, edge` — so with only the Compose network trusted, the edge's private IP is every user's, and the per-IP rate
limit becomes one global limit. In prod: the Compose network's CIDR **and** the edge's private IP.

The web target needs `:server` running as well — see [MAL authentication](#mal-authentication).

### Ports

This project owns the **18010–18090** block. Ports are assigned in decade slots; 18030 and 18050 onwards are
unallocated.

| Port  | Service                                              |
|-------|------------------------------------------------------|
| 18010 | `:server` — Ktor, MAL relay, loopback unless `ACT_RELAY_HOST` says otherwise |
| 18020 | `:app:webApp` dev server                             |
| 18040 | Desktop OAuth callback on `127.0.0.1` — `LoopbackRedirectListener`, bound only while signing in |

18040 cannot be made ephemeral: MAL does no RFC 8252 §7.3 port-lenient matching, so the port is part of the byte-exact
Redirect URI registered on the app (`DESKTOP_LOOPBACK_PORT` in `:core`). `LoopbackRedirectListener` in
`:app:shared/jvmMain` binds it for the length of one sign-in and gives it back on success, timeout or cancellation, so
a `BindException` there means either a sign-in is already in progress or something else has taken the port. The
listener uses `com.sun.net.httpserver`, which is why `:app:desktopApp` declares
`nativeDistributions { modules("jdk.httpserver") }` — Compose's default runtime modules do not include it, and the gap
only shows up in a packaged build.

**One desktop process at a time.** `main()` in `:app:desktopApp` takes `SingleInstanceLock` — an OS file lock on
`instance.lock` beside the state file — before Koin, the window or anything that can bind 18040, so a second launch
cannot disturb the first one's sign-in. The OS releases it on process death, so a crash leaves no stale lock. The second
launch shows an "already running" dialog, or writes to stderr when headless, and exits 1. It lives in the entry point,
not `:app:shared`: the UI module does not decide how many processes the app may have.

Production is `https://act.io-workshop.net` — see [Deployment](#deployment). It is not a port; it is the same web
bundle and relay, one origin, behind an edge proxy.

The port is set in `app/webApp/build.gradle.kts`; everything else about the dev server (host binding, `allowedHosts`,
the `/mal` proxy, and the `historyApiFallback` that serves the app on `/oauth/callback`) is in
`app/webApp/webpack.config.d/devserver.js`.

Optionally reachable as `https://act.io-workshop.localhost` through a local reverse proxy that routes `/mal` to 18010
and everything else to the dev server. Same-origin routing is required, not cosmetic — see below. That config lives
outside this repo; in Caddy terms it is:

```caddyfile
act.io-workshop.localhost {
	handle /mal /mal/* {       # first, so the relay is never swallowed by the SPA fallback below
		reverse_proxy 127.0.0.1:18010
	}
	handle {
		reverse_proxy 127.0.0.1:18020
	}
}
```

The dev server's own `historyApiFallback` covers the SPA deep link on both paths, so the proxy needs no `try_files` of
its own — but it does need `/mal` matched **first**, or `/mal/...` reaches the dev server and comes back as
`index.html`. If the app is ever served from a static bundle instead of the dev server, that side needs
`try_files {path} /index.html` added.

The proxy's origin must also be in `:server`'s CORS allow-list (`RelayConfig.DEFAULT_CORS_ORIGINS`, or
`ACT_RELAY_CORS_ORIGINS` where that is set), even though relay calls are same-origin: browsers send `Origin` on every
`POST` and `PATCH`, Ktor's CORS plugin cannot tell a proxied request is same-origin, and an unlisted origin gets an
empty 403 — the login succeeds and the token exchange then fails.

Tests — each platform has its own task, and `test` runs them all in one Gradle invocation:

```bash
mise run test:shared:android   # :app:shared androidHostTest + commonTest
mise run test:shared:jvm       # :app:shared jvmTest + commonTest
mise run test:shared:web       # :app:shared webTest + commonTest
mise run test:android          # :app:androidApp — the manifest drift guard, and nothing else
mise run test:server
mise run test:core             # :core on jvm, wasmJs and android at once
mise run test                  # all of the above
```

Single test — `test:one <suite> <filter>`, where the suite is `core`, `shared`, `server` or `android` and runs on that
module's JVM-hosted test task (the only kind that can filter):

```bash
mise run test:one shared "net.io_workshop.act.auth.LoopbackRedirectListenerTest"
mise run test:one core "*.SignInTest.signing_in_is_blocked_when_the_build_has_no_client_id"
```

## MAL authentication

MAL supports **only** the OAuth2 authorization code grant with PKCE. There is no password grant — `grant_type=password`
returns `unsupported_grant_type`, and the legacy basic-auth
`/api/account/verify_credentials.xml` endpoint is retired (403). A username/password form therefore cannot work; the
password is only ever typed on myanimelist.net.

MAL supports `code_challenge_method=plain` only, so the PKCE challenge equals the verifier and no SHA-256 is involved
(`Pkce` in `:core`).

Register the app at myanimelist.net/apiconfig with **App Type `other`** — that issues a Client ID and no secret, which
is correct for a public client.

The Client ID is never committed; it comes from a `mal.clientId` build property or the environment, and a build with
neither cannot start a Sign-in — see [The Client ID](#the-client-id-malclientid) below.

Each target captures the redirect itself (`AuthRedirectChannel` in `:app:shared/auth`): desktop on a loopback listener,
web in a popup, Android on an Auth Tab raced against the manifest's custom-scheme intent filter.
**Paste-the-code is not dead code** — it is the modelled fallback for a headless desktop, a blocked
popup or a missing Custom-Tabs browser, and it is the path every capture funnels into, so there is one parser and one
set of error messages.

**Web needs the relay, on the same origin.** MAL sends no CORS headers on its token or API endpoints and answers
preflight `OPTIONS` with 405, so a browser cannot call them at all.
`:server` relays them (`MalRelay.kt`) under the `/mal` prefix — the token endpoint, `GET`s under `/v2`, and one write,
`PATCH /mal/v2/anime/{id}/my_list_status` with a numeric `{id}`; no other `PATCH` path is routed — and `:core`'s
`expect fun platformMalEndpoints()` routes the browser targets there while jvm/android call MAL directly.

The browser actuals derive the relay URL from `window.location.origin`, so the same build works behind the reverse proxy
and on a direct dev-server port. Both paths keep the relay same-origin — the reverse proxy routes `/mal`, and the
webpack dev server proxies it — which is what makes CORS a non-issue rather than something to work around. Moving the
relay to its own hostname would reintroduce the original failure.

Web therefore needs two processes:

```bash
mise run run:server   # relay on :18010
mise run run:web      # app on :18020
```

Three things must agree on the `/mal` prefix: `MAL_RELAY_PATH_PREFIX` in `:core`, the reverse-proxy route, and the
dev-server proxy in `webpack.config.d/devserver.js`. `:server`'s routes are built from the constant itself, so they
cannot drift from it.

### The Client ID: `mal.clientId`

No real Client ID is committed, and the user never sees, types or overrides one: the build's value is the only source.
A build without one still runs and tests; the Welcome Screen disables its button (`SignInState.clientIdMissing`).

The build-time default is generated by the `generateMalBuildConfig` task in `core/build.gradle.kts` as
`const val MAL_CLIENT_ID` in `commonMain` — a plain `const val` needs no expect/actual and works on every target,
which is why there is no BuildKonfig dependency. Resolution, highest precedence first:

1. a `mal.clientId` Gradle property — `-Pmal.clientId=...`, or `~/.gradle/gradle.properties` (outside the repo
   entirely),
2. the `MAL_CLIENT_ID` environment variable — which `mise run run:desktop`, `run:web` and `deploy:build` load from the
   gitignored `.secure.build.env`.

A **set-but-empty** value at any step falls through to the next rather than short-circuiting, so `-Pmal.clientId=` cannot
silently hide the source below it. That order is resolution precedence; the *privacy* preference is the other way
round — `~/.gradle/gradle.properties` keeps the value out of the repo tree entirely, `.secure.build.env` keeps it out of
git, and an environment variable typed in a shell ends up in shell history.

**`ORG_GRADLE_PROJECT_mal_clientId` does not work** — Gradle maps `ORG_GRADLE_PROJECT_x` to the project property `x`
verbatim, with no underscore-to-dot conversion, so that name sets `mal_clientId` and nothing reads it. Verified. The
dotted `ORG_GRADLE_PROJECT_mal.clientId` does work (a shell cannot assign that name, but `env` and most CI secret UIs
can); `MAL_CLIENT_ID` is the straightforward route for CI.

Set nowhere it is `""` and Sign-in is disabled; **a missing value does not fail a dev run or a test** (nor a build of anything but a release artifact).
The exception is a release artifact: `:app:webApp:wasmJsBrowserDistribution` depends on `:core:requireMalClientId`,
which fails naming the two sources, because that bundle ships to users who cannot be prompted for a build-time
default. The Android release tasks depend on the same task rather than a second check — the ones that package and sign the artifact, not only the `bundleRelease`/`assembleRelease` lifecycle tasks, so no unsigned or ID-less output is written before it fails. Every step is a lazy
`Provider` and the value is declared with `inputs.property`, which is what keeps the task configuration-cache-safe and
still invalidated when the value changes — reading a file with `Properties().load(...)` at configuration
time is exactly the trap the Conventions section warns about.

`act.version` follows the same pattern in `core/build.gradle.kts` (`generateActVersion` → `ACT_VERSION`): a build with no property says `unknown`, and the About Screen and the Diagnostics Screen are the only readers.

`appModule` is the only place that reads `MAL_CLIENT_ID`. `:core` keeps `MalAuthConfig.clientId` a required parameter on
purpose, so `:core`'s own tests cannot pick up whatever the developer's machine was configured with.

`mal.layout.v1` and `mal.theme.v1` are `JsonTokenStore`'s preference records (`ThemePreference` in `:core`: dark, light
or system), and `clear()` drops neither. An earlier build also kept `mal.clientId.v1`; `restore()` removes that record on startup (`discardLegacyClientId`), and nothing else reads it.
The Pending Authorization keeps its own `clientId`, so an authorization started before an app update exchanges against
the ID it was started with.

**Web storage is split** (ADR-0007): the Session, Layout and Theme are in `localStorage` (`LocalStorageKeyValueStore`),
so a new tab or a browser restart finds them; the Pending Authorization is in `sessionStorage`
(`SessionStorageKeyValueStore`), because a popup gets a *copy* of it and two tabs must not share a PKCE verifier.
`JsonTokenStore` takes both stores and owns the routing (`storeFor`); Android and desktop pass one instance for both,
and the web platform module binds the tab-scoped one under the `TAB_SCOPED_STORE` qualifier. On startup `restore()`
deletes the old Session/Layout/Theme records from the tab-scoped store (`discardLegacyTabScopedRecords`) — a no-op when
the two are the same instance, which is what stops it deleting the real Session on Android and desktop. A refresh
token now outlives the tab: a shared machine needs an explicit sign-out. Signing out in one tab signs out
the others: `KeyValueStore.changes` (web: the `storage` event; empty elsewhere) feeds `MalSessionRepository`, which drops
a signed-in tab to `SignedOut(SignedOutElsewhere)` when the Session record is removed by someone else.

## Deployment

The web target ships to `https://act.io-workshop.net`: the production Wasm bundle at `/`, the Relay at `/mal`,
`about.html` at `/about` and `privacy.html` at `/privacy`, one origin. Everything is run from the developer's machine; there is no CI. The decisions
are in `.scratch/web-deploy/spec.md`.

**Path:** browser → the **edge** (existing, outside this repo; terminates TLS and reverse-proxies *everything*, not a
redirect, to the droplet's private VPC IP over HTTP) → **Caddy** in the `act-web` container (`${PRIVATE_IP}:80`, the only
published port) → `/mal` to the **relay** container, everything else static with the SPA fallback. Images are
`$DOCKERHUB_USER/act-web` and `act-relay`, tagged with `act.version` only — no `latest`, and an existing tag is refused,
so a tag is immutable. `deploy/` holds the Dockerfiles, the Caddyfile, `docker-compose.yml`, `terraform/` and `scripts/`.

**`/mal` must stay same-origin in production too.** MAL sends no CORS headers, so the relay on its own hostname would
bring the original failure back; the edge must pass `/mal` through with `PATCH` and `Authorization`, and must not add
`Cross-Origin-Opener-Policy` (see [the popup](#web-sign-in-the-popup-and-two-things-that-must-not-change)).

**Env files:** three, gitignored (`.secure.*.env`), each with a committed `.example` listing every key.
`.secure.build.env` holds `MAL_CLIENT_ID`, `DOCKERHUB_USER` and `ANDROID_TEST_DEVICE_ID`; `.secure.release.env` holds the four `ACT_UPLOAD_*` upload-keystore variables, is loaded by `build:android:release` and `run:android:release` alone, and Gradle reads it only through `providers.environmentVariable` (`requireUploadSigning` fails a release build naming what is unset; debug builds never read it); `.secure.deploy.env` holds `DIGITALOCEAN_TOKEN`,
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, `EDGE_SSH` and the `TF_VAR_*`. **mise reads them, per task**
(`env = { _ = { file = ... } }` in `mise.toml`) — never a global `[env]` — so the DO token reaches only `infra:*` and
`deploy*`; `deploy` and `deploy:status` load the file for `EDGE_SSH` and `unset` the token at once (`terraform output`
reads the S3 state with the Spaces key alone), and the deploy scripts reach Gradle only through `lib.sh`'s `task` — a
nested `mise run` of `build:web:dist`, `build:server:dist`, `test:server` and `test:shared:web`, which inherits the
caller's environment — so `task` strips the Spaces key, `EDGE_SSH` and every `TF_VAR_*` as well.

**Terraform state** is not local: it lives in the shared `io-workshop-tfstate` Spaces bucket (owned by the
`io-workshop-hub` stack) under `act/web.tfstate`, via the `backend "s3"` block in `deploy/terraform/versions.tf`, and
the `AWS_*` pair is a Spaces key scoped to that bucket. There is **no state locking** — Spaces ignores the conditional
writes `use_lockfile` relies on — so never run two `infra:apply`s at once. Gradle picks `MAL_CLIENT_ID` up
from the environment itself, so `run:desktop` and `run:web` are prefilled with it (`run:server` loads nothing); plain
Gradle run outside mise does not read these files. `docker login` is done once by hand.
The Client ID ends up in plain text in a public image, deliberately: it is equally public in `webApp.js`.

| Task                                              | Does                                                                                                                                        |
|---------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `mise run run:*` / `build*` / `test*`             | the [Commands](#commands) above; the clients and builds with `.secure.build.env` loaded, the tests with nothing                             |
| `mise run infra:plan` / `infra:apply`             | Terraform (`deploy/terraform/`), by hand — never from a deploy; a replaced droplet moves its private IP, so the edge's upstream must follow |
| `mise run build:android:release`                  | `bundleRelease`: signed with the upload key, R8 and resource shrinking on; the mapping ships in the AAB                                       |
| `mise run deploy:build`                           | `build:web:dist` + `build:server:dist`, then both images (`linux/amd64`)                                                                    |
| `mise run deploy:push`                            | pushes both; refuses if either tag exists on Docker Hub                                                                                     |
| `mise run build:desktop:deb`                      | Linux `.deb` of the desktop app (ADR-0009); see [Desktop release](#desktop-release)                                                        |
| `mise run deploy [version]`                       | the whole path below; with a version, only redeploys that pushed tag                                                                        |
| `mise run deploy:status`                          | `ACT_VERSION` and `docker compose ps` on the droplet                                                                                        |

`deploy`: refuses a dirty tree; runs `test:server` and `test:shared:web`; builds and pushes; copies
`docker-compose.yml` and writes the droplet's `.env` over `ssh -J $EDGE_SSH deploy@<terraform output private_ip>`;
`pull`, `up -d`, waits for both healthchecks; prunes images other than the current and previous version; then smoke-tests
through the edge (`deploy/scripts/smoke.sh`: the SPA fallback, `/webApp.js` is JS, `/about`, `/privacy`, a bogus-bearer call to
`/mal/v2/anime` is MAL's **401** — an anonymous one is 403 — and no COOP anywhere).

**Rollback:** on any failure once it reaches the droplet, `deploy` prints `mise run deploy <previous-version>` and
exits non-zero (a failure in the tests, build or push has deployed nothing, and just exits non-zero); it never rolls
back by itself. Any version argument — `act.version` included, which is how you roll forward again after a rollback — skips tests,
build and push and redeploys that tag as it is on Docker Hub, after checking both images exist there.

The relay's trusted proxies in `docker-compose.yml` are the Compose network (pinned to `172.29.0.0/24`) **and** the edge's
private IP — both hops, as the table under [Commands](#commands) explains.

## Desktop release

The desktop app ships as the package `io-workshop-act` (ADR-0009): the display name is `ACT`, the package, the
`/opt/io-workshop-act/` folder and the `.desktop` file are `io-workshop-act`, and the launcher inside is `bin/ACT`
(jpackage names it after the generic `packageName`). `packageVersion` is `act.version`.

`mise run build:desktop:deb` loads `.secure.build.env`, refuses on a non-Linux OS or without `dpkg-deb` before Gradle
starts, and runs `:app:desktopApp:packageDeb`. The installer is
`app/desktopApp/build/compose/binaries/main/deb/io-workshop-act_<act.version>_amd64.deb`. Every `package*Deb/Msi/Dmg`
task depends on `:core:requireMalClientId`, so without a Client ID it fails with that task's message and writes no
installer. It is not part of `mise run build`.

## Architecture

Two tiers of shared code, deliberately separated:

- **`:core`** — pure Kotlin Multiplatform, **no Compose dependency**. Logic that must also be usable by the Ktor server (`:server` depends on it as a plain JVM library). Targets jvm, wasmJs, android.
- **`:app:shared`** — Compose Multiplatform UI plus platform abstractions. Exposes `:core` via `api(project(":core"))`, so app modules get `:core`'s API transitively.

The three client modules (`:app:androidApp`, `:app:desktopApp`, `:app:webApp`) are thin entry points only: each has a `main`/`Activity` that sets up its platform's window and calls the single `App()` composable from `:app:shared`. Put UI in `:app:shared`, not in the app modules.

**Screens take a value, not a ViewModel.** `SessionRoute` switches on a sealed `ScreenState` produced in `:core` by `ScreenStateSource` — a total combine over nine flows — and each screen takes its variant plus a separate actions record. `App()` resolves `MalSessionRepository` (whose `state` and `config` are two of the nine flows) plus `SignIn`, `SessionControls`, `AnimeListRepository`, `AnimePageRepository`, `LayoutPreference`, `ScreenChoice` (which Screen is open; see ADR-0006) and `ThemePreference` (process-scoped `single`s in `:core`, each with its own scope), plus `CoverPolicy`, and `AppScreen` wires them to the value and binds the `AuthRedirectChannel`. Two inputs are read outside the Screen State, by `App()` itself: `ThemePreference`, because the theme wraps every screen including the ones before a Session; and `CoverPolicy` (whether a Target hides Adult Covers), because it is a per-Target constant bound in each platform's Koin module, not state — nothing about it changes, so it has nothing to say to `ScreenStateSource`, and it reaches `AnimeCoverBox` through `LocalCoverPolicy`; below that, nothing is a ViewModel, and none should be added — see `docs/adr/0005-no-viewmodels.md`. The Anime List lasts one Session by `AnimeListRepository`'s own rule — it builds a pager on entering `SignedIn` and discards it on leaving — not by how anything is scoped; the open Anime Pages (`AnimePageRepository`, history in `ScreenState.SignedIn`, ADR-0006) end with the Session by the same rule. The actions stay out of `ScreenState` because Compose skips on `equals` and a `data class` holding a `() -> Unit` is neither equal nor stable. See `docs/adr/0004-screen-state-in-core.md`, which also carries the answer to "why is a UI type in the module `:server` depends on".

The two additions to that are both sign-in plumbing that only an entry point can do, and both forward immediately into `:app:shared` rather than deciding anything: `MainActivity.onCreate`/`onNewIntent` hand the redirect Intent to `AuthRedirectInbox`, and web's `main()` relays a popup's redirect to its opener before Koin starts. Neither is a place to add behaviour.

`:server` is a standalone Ktor/Netty app (`Application.kt`) that shares only `:core`.

### expect/actual

`Platform.kt` in `commonMain` declares `expect fun getPlatform(): Platform`. Actuals live in `Platform.android.kt`, `Platform.jvm.kt`, `Platform.wasmJs.kt`. Adding a target means adding an actual in that source set or the build breaks at compile time.

### Source-set layout quirks

- The web code lives in `webMain` / `webTest`, the intermediate source sets from Kotlin's default hierarchy template, not in `wasmJsMain`. `wasmJs` is the only web target — the `js` target was removed — so today the split is one level deeper than it needs to be, but it keeps browser code in one tree that another web target could compile unchanged. `wasmJsMain` holds only the `Platform` actual; put browser code in `webMain`. Code in `webTest` runs under `wasmJsTest`.
- `:core` and `:app:shared` use the **`com.android.kotlin.multiplatform.library`** plugin (AGP 9), not the classic library plugin. Its unit tests live in **`androidHostTest`**, not `androidUnitTest`/`test`. The task is `testAndroidHostTest`.
- `:app:androidApp` is the only classic Android module (`com.android.application`) and holds all Android resources and the manifest.

### Compose resources

Assets go in `app/shared/src/commonMain/composeResources/<qualifier>/` and are reached through the generated accessor package `act.app.shared.generated.resources.Res` (e.g. `Res.drawable.compose_multiplatform`). The generated package is derived from the module path, so it changes if the module is moved or renamed.

### Web hosting page

`app/webApp/src/webMain/resources/index.html` hardcodes `<script src="/webApp.js">`, the filename the Wasm build emits.

The leading slash is load-bearing: the same page is served for `/oauth/callback` by the dev server's
`historyApiFallback`, and a relative `webApp.js` there resolves to `/oauth/webApp.js`, which 404s — the page renders its
loading spinner and the app never boots. `curl` cannot see this; it gets a 200 and the right HTML.

### Web sign-in: the popup, and two things that must not change

The web Redirect Capture is a popup that `postMessage`s the redirect back to `window.opener`
(`PopupRedirectChannel`), falling back to a full-page redirect when the browser blocks it and then to Paste-the-code.

- **Never set `Cross-Origin-Opener-Policy: same-origin`** on the dev server, `:server`, or the reverse proxy. It severs
  `window.opener` when the popup navigates to myanimelist.net, and the login then hangs with no error anywhere. Nothing
  sets COOP today, which is what makes the popup possible at all; `same-origin-allow-popups` is the value to use if
  cross-origin isolation is ever needed.
- **The popup must not boot the app.** `main()` calls `relaySignInRedirectToOpener()` *before* `initKoin()` and returns
  if it answers true. A `window.open`ed document gets its **own copy** of `sessionStorage`, not a shared view, so a
  popup that started the app would exchange the code against its own copy and clear a Pending Authorization the opener
  would never see cleared.

### Android sign-in: the redirect arrives at an Activity, not at a channel

A custom-scheme redirect lands on `MainActivity` and nowhere else, so it forwards it to `AuthRedirectInbox.Shared` —
a process-scoped `MutableSharedFlow(replay = 1)` — and does nothing else with it. Everything with an opinion about a
redirect sits above `MainActivity` and outlives it. (An *inbox*, not a relay: `Relay` in this project means the `/mal`
routes on `:server`, and nothing here forwards anything to MAL.)

- **`replay = 1` is load-bearing.** `onNewIntent` runs before `onResume`, and on a cold start the Intent is in hand
  before Koin has built `SignIn`, so a redirect is routinely delivered before anything is collecting. Without the
  replay it is dropped and the sign-in hangs with no error anywhere.
- **Two things take from the inbox, and only one of them always exists.** `IntentRedirectChannel` when a sign-in is
  live, filtering by `state` because the filter is exported and any app can fire that Intent. `AndroidStartupRedirect`
  when the process was *killed* behind the browser — ordinary on a low-RAM device — and the redirect relaunches the
  app: no armed channel, no `state` in memory, only the persisted Pending Authorization, which is why that record is
  written before the browser opens.
- **`intent.data` is nulled on delivery.** `setIntent()` hands the same object to a later recreation, and an
  authorization code is single-use.
- **Cancellation is a heuristic and must stay non-destructive.** On the intent-filter path there is no cancellation
  API at all, so a resume that follows a stop is treated as backing out. The `ON_STOP` requirement is what stops the
  *first* `onResume` — which fires before the browser is on top — cancelling every sign-in at the moment it starts; a
  call, a notification or a configuration change still read as cancellations. So `Cancelled` only re-enables the
  button, and `MalSessionRepository.cancelAuthorization` keeps the Pending Authorization. A redirect already delivered
  wins over a suspected cancellation without a grace window, because `onNewIntent` precedes `onResume`. The signal is
  `Lifecycle.currentStateFlow`, not `eventFlow`: the capture only subscribes after the browser has been launched, and
  a `StateFlow` still tells a late subscriber that the Activity is off screen.

### Android sign-in: Auth Tab in front, intent filter behind, and the two raced

`AuthTabRedirectChannel` wraps `androidx.browser`'s `AuthTabIntent`, which is the right tool for this: the redirect
comes back through an `ActivityResultLauncher` — no intent filter involved — and the user backing out is a real result
code rather than a lifecycle guess. `rememberAuthRedirectChannel()` is a `@Composable` because of it: an
`ActivityResultLauncher` can only be registered from composition, and androidx requires that registration to happen
unconditionally, before the Activity reaches `STARTED`.

**It is not enough on its own, and that is the whole design.** Auth Tab needs **Chrome 137+**. Everywhere else the same
Intent is read as a plain Custom Tab — androidx puts a null `EXTRA_SESSION` in it so that it is — and the launcher then
reports `RESULT_CANCELED` **even when the login succeeded**, with the redirect arriving through the intent filter
instead. Believing the result code alone would break sign-in for everyone not on current Chrome.

So both channels run and neither is authoritative alone:

- **A redirect from either side wins outright**, with no grace window. It is the one answer that cannot be a
  misinterpretation.
- **Anything else waits out `CANCELLATION_GRACE` (2s) for the other side.** Symmetric, because both orderings happen:
  a `RESULT_CANCELED` that precedes the redirect it is really about, and a lifecycle heuristic that fires before the
  result code lands. The window is only ever fully spent while the Activity is still off screen, so nobody watches it
  run down.
- **Two redirects for one sign-in are normal, and only one may be exchanged.** The channel returns exactly one
  `AuthRedirectResult`, and `completeAuthorization` reads the Pending Authorization from the store and clears it.

`IntentRedirectChannel.expect()` exists for this: the intent-filter side has to know which `state` to accept even when
the Auth Tab already put the user in the browser, and `open()` would launch a second one.

Which arrangement a device gets is `browserPlan()` — Auth Tab, plain Custom Tab, or bare `ACTION_VIEW` — from
`CustomTabsClient.getPackageName()` and `isAuthTabSupported()`. Both return nothing useful without the manifest's
`<queries>`, and nothing throws when it is missing. `BrowserPlan.redirectChannel()` is what each plan then builds, split
out of the composable so `BrowserPlanTest` can pin *which launcher each plan reaches* — a swapped one still completes a
sign-in and reports nothing, it just stops being the good arrangement.

**`LocalUriHandler` is the last rung and must never be the first.** Its Android actual is a bare `ACTION_VIEW`: no
Custom Tab, no result channel, no way to close the tab. That is precisely the `PlainView` rung and nothing above it —
reimplementing it by hand would only do it worse, since Compose's handler already opens on the Activity's own Context
and so needs no `FLAG_ACTIVITY_NEW_TASK`. **A WebView is disallowed, not discouraged** — RFC 8252 §8.12, and the
password is only ever typed on myanimelist.net.

### Android sign-in: the manifest is load-bearing

Four things in `app/androidApp/src/main/AndroidManifest.xml` are each a silent failure if lost, so
`AndroidManifestTest` — the only test in that module — asserts all four:

- **`launchMode="singleTop"`.** Under `standard` the redirect Intent starts a *second* `MainActivity`
  instance, so the instance that receives the code is not the one the sign-in is attached to. Nothing
  throws; the redirect simply does nothing. `singleTask`/`singleInstance` avoid that too, at the price of
  task-management surprises.
- **The custom-scheme intent filter** must stay byte-identical to `ANDROID_REDIRECT_URI` in `:core`, which MAL compares
  byte-exactly. It is spread over three attributes (`scheme` / `host` / `path`) there and one string in Kotlin, which is
  exactly how the two drift.
- **`<queries>`.** Without it, on API 30+ `CustomTabsClient.getPackageName()` and `isAuthTabSupported()` see no browsers
  at all — the `androidx.browser` AAR ships none of its own — and sign-in quietly degrades to Paste-the-code.
- **`allowBackup="false"`, plus the two rules files.** Auto Backup would copy the refresh token to the cloud.
  `allowBackup` covers cloud backup; device-to-device transfer on API 31+ is governed by
  `res/xml/data_extraction_rules.xml` alone and ignores that flag; and below API 31 those rules are ignored in turn and
  `res/xml/backup_rules.xml` (`android:fullBackupContent`) is what would count. So the `SharedPreferences` file
  (`MAL_STORE_NAMESPACE` + `.xml`) is named in all three places. The app therefore does not participate in backup at all
  — deliberate, since the only thing worth backing up is the one thing that must not be.

## Conventions

- **The product version is `act.version` in `gradle.properties`** — `:server`'s `version` and Android's `versionName` read it via `providers.gradleProperty`. Shell tasks read it without Gradle: `grep '^act.version=' gradle.properties | cut -d= -f2`. `versionCode` is derived from it as `major*1_000_000 + minor*1_000 + patch` (ADR-0008), per variant through a `Provider`; a malformed version or a part above 999 fails the build. It is also generated into `:core` `commonMain` as `const val ACT_VERSION` by `generateActVersion`, the way `MAL_CLIENT_ID` is (a lazy `Provider`, `inputs.property`, no BuildKonfig), and is read in two places: the About Screen and the Diagnostics Screen.
- **All dependency and plugin versions live in `gradle/libs.versions.toml`.** Build scripts reference `libs.*` aliases only — never inline a version string in a `build.gradle.kts`.
- Java is 21, Azul Zulu, **pinned in `mise.toml` and not downloaded by Gradle**: `gradle/gradle-daemon-jvm.properties` names the vendor and version, there is no foojay resolver and no `toolchainUrl.*`, so a machine without that JDK fails with "No defined toolchain download url" rather than fetching one — the fix is `mise install`, not `updateDaemonJvm` (which would write the URLs back). An IDE's Gradle sync does not go through mise: set its Gradle JDK to the mise install once (`mise where java`). Android and Android-KMP modules compile to **JVM target 11**.
- Gradle configuration cache and build cache are enabled in `gradle.properties`. Build logic that reads state at execution time will fail configuration-cache validation.

## Agent skills

### Issue tracker

Issues live as markdown files under `.scratch/<feature>/`, not on GitHub — the remote (`github.com/MustD/act`) hosts
the code, and its public Issues page is only the privacy policy's contact. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical roles, used verbatim. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — one root `CONTEXT.md` plus `docs/adr/`. See `docs/agents/domain.md`.
