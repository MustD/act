# act

A MyAnimeList client: Kotlin Multiplatform / Compose Multiplatform for Android, Web (Wasm) and Desktop (JVM), plus a
small Ktor relay the web target needs. Production is <https://act.io-workshop.net>.

## Layout

- [`core`](./core/src) — pure Kotlin Multiplatform, no Compose: sign-in, the Session, the Anime List, the Screen State.
- [`app/shared`](./app/shared/src) — the Compose UI and the platform abstractions.
- [`app/androidApp`](./app/androidApp), [`app/desktopApp`](./app/desktopApp), [`app/webApp`](./app/webApp) — thin entry
  points.
- [`server`](./server/src/main/kotlin) — the relay: MAL sends no CORS headers, so the browser calls it under `/mal`.
- [`deploy`](./deploy) — Dockerfiles, Caddy, Compose, Terraform and the deploy scripts.

## Setup

Everything runs through [mise](https://mise.jdx.dev); `mise tasks` lists every command.

```bash
mise install                                  # the JDK (Azul Zulu 21) and Terraform
cp .secure.build.env.example .secure.build.env   # then fill in MAL_CLIENT_ID
```

The Client ID comes from an app registered at myanimelist.net/apiconfig with App Type `other`. Without one the app
still builds, runs and tests; Sign-in is disabled.

In an IDE, point the Gradle JDK at the mise install (`mise where java`) — Gradle no longer downloads one.

## Run

```bash
mise run run:desktop        # desktop
mise run run:desktop:hot    # desktop with Compose Hot Reload
mise run run:server         # relay on :18010
mise run run:web            # web on :18020 — needs run:server as well
mise run build:android      # Android debug APK
```

## Test

```bash
mise run test                                  # everything
mise run test:core                             # :core on every target
mise run test:shared:jvm                       # also test:shared:android, test:shared:web
mise run test:android
mise run test:server
mise run test:one core "*.SignInTest"          # one class or method; suites: core, shared, server, android
mise run build                                 # compile every target and run every test
```

## Deploy

`mise run deploy` — see the Deployment section of [`CLAUDE.md`](./CLAUDE.md), which is also the full reference for
everything above. Needs `docker` (with buildx) and `ssh` on the machine; mise does not manage those.
