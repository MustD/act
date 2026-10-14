# Android release identity: `net.io_workshop.act`, and `versionCode` derived from `act.version`

Status: accepted

The Android app ships on Google Play as `net.io_workshop.act`, and its `versionCode` is computed from `act.version`
rather than kept by hand. Both are effectively permanent once the first build is uploaded: Play never lets an
`applicationId` change, and never accepts a `versionCode` lower than one it has seen.

## Considered options

- **`io.github.mustd.act`** — tied to the GitHub account that hosts the source. Rejected in favour of the domain the
  product actually lives on, `act.io-workshop.net`, so the app, the web build and the privacy policy share one
  identity. Both schemes were registered on the MAL integration ahead of the choice; the unused one is removed once
  Android signs in.
- **`versionCode` as its own hand-bumped number** — one more value to forget, and the only thing keeping it ahead of
  the last upload would be memory.

## Decision

- `applicationId`, the Android `namespace` and every Kotlin package are `net.io_workshop.act`. The Redirect URI's scheme
  is `net.io-workshop.act://oauth/callback`: a URI scheme may not contain `_`, so the scheme and the package differ by
  that one character. The scheme is byte-compared by MAL and is as permanent as the `applicationId`.
- `versionCode = major * 1_000_000 + minor * 1_000 + patch` of `act.version`. Codes skip whenever the web ships without
  Android, which Play does not mind. The formula caps minor and patch at 999; changing it later must still produce a
  number above the last one uploaded.
- The store namespace is renamed with the package, without migration: Android starts as a fresh install anyway, and web
  and desktop sign in once more. The old namespace's records are left where they are.
