# Desktop release identity: `io-workshop-act`, one `.deb`, an AUR `-bin` package, unsigned elsewhere

Status: accepted

The desktop app ships as the package `io-workshop-act`: a `.deb` attached to the GitHub Release for `act.version`, and
an AUR package `io-workshop-act-bin` that repackages that same `.deb`. Windows (`.msi`) and macOS (`.dmg`) have build
tasks but are unsigned and untested. The package name is effectively permanent — an AUR package name and the
`/opt/io-workshop-act/` path that installed systems depend on are not renamed lightly.

## Considered options

- **`act`** — taken on Arch: `extra/act` (nektos/act, the local GitHub Actions runner) owns the name and `/usr/bin/act`.
  `act-mal` was the other candidate; `io-workshop-act` matches the domain the product lives on, as ADR-0008 did for
  Android.
- **An AUR source package building with Gradle** — the build needs the MAL Client ID, which is never committed, so an
  AUR user's build would have Sign-in disabled. Not viable while the Client ID is a build-time secret.
- **A separate `.tar.gz` for the AUR** — cleaner for pacman, but a second Linux artifact per release. The `-bin` package
  unpacks the `.deb` instead, and so must install the `.desktop` entry and icon itself: a `.deb`'s maintainer scripts
  never run under `makepkg`.
- **CI on GitHub Actions runners** — the only way `.msi`/`.dmg` would be built without owning the hardware. Deferred:
  the tasks are shaped so a runner matrix can call them later, but today nothing builds them.

## Decision

- The display name is `ACT`; the package, the `/opt` directory and the launcher are `io-workshop-act`. The AUR package
  also links `/usr/bin/io-workshop-act`; the `.deb` does not, because that means overriding `jpackage`'s maintainer
  scripts with no Debian system to test them on.
- Desktop packages start at `1.0.0`: `jpackage` rejects a major version of 0 for `.dmg` and `.msi`, and the alternative
  was a fake version for those two formats.
- Assets are uploaded to a Release written by hand, never one the task creates, and an asset already there is never
  replaced — the same immutability as the Docker image tags. An AUR `pkgver-pkgrel` is never pushed twice either.
- No code signing: Windows shows a SmartScreen warning, macOS quarantines the `.dmg`. Signing needs an Apple Developer
  account and a Windows certificate, neither of which exists.
- No ProGuard on desktop: it was never configured, could only be tested on Linux, and the bundled runtime dominates the
  download either way.
