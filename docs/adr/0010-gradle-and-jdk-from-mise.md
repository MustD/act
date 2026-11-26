# Gradle and the JDK come from mise, pinned exactly; no wrapper

Status: accepted

## Context

The desktop `.deb` bundles a runtime made from the JDK the Gradle daemon runs on, and the relay image runs on
`eclipse-temurin:21-jre-alpine`. Java4Shell (CVE-2026-114938, 2026-11-17) is a heap overflow in HotSpot's AVX-512
Base64-decoder intrinsic, affecting every OpenJDK build from 17.0.6, whatever the vendor; the fix is a specific patch
build. Neither place could name one:

- mise held `java = "zulu-21"`, and `gradle/gradle-daemon-jvm.properties` could only ask for a vendor and a major
  version (`AZUL`, `21`), so whichever Zulu 21 a machine had installed is what a `.deb` shipped.
- The relay's `21-jre-alpine` is a floating tag, and `deploy:build` does not `--pull`, so a stale local base image would
  ship unnoticed.

The fixed Zulu build could not be identified with confidence; Temurin `25.0.5.1+2` is the fixed build already verified
in a sibling project, and it exists both as a mise JDK and as an `eclipse-temurin` image — the same build in both places.

## Decision

- `mise.toml` pins the JDK exactly (`java = "temurin-25.0.5.1+2.0.LTS"`) and Gradle (`gradle = "9.5.1"`).
- `deploy/relay.Dockerfile` pins the same build: `FROM eclipse-temurin:25.0.5.1_2-jre-alpine`. The two move together.
- No daemon-JVM criteria: the daemon runs on mise's `JAVA_HOME`.
- No Gradle wrapper: `gradlew`, `gradlew.bat` and `gradle/wrapper/` are gone and listed in `.gitignore`; mise tasks
  call `gradle`.
- Java 25 rather than a patched 21: the move had to touch both pins anyway, and 25 is the current LTS.
- `act-web`'s `caddy:2-alpine` is left floating: Caddy is Go, and is not affected.
- No `-XX:-UseBASE64Intrinsics` on the relay: the pinned, patched JDK is the fix, and the flag would mask a forgotten
  bump later.

## Consequences

- One exact JDK across the build, the desktop runtime and the relay, and a security release is a two-line bump
  followed by a release (`act.version`, `deploy`, the desktop release).
- The jvm targets set no bytecode target, so they now compile to Java 25; every runtime that loads them is 25.
  Android stays on JVM target 11, and runs on ART, which this CVE does not touch.
- Gradle only works from mise's environment: a `mise run` task, `mise exec`, or a shell with `mise activate`. A shell
  without mise has no `gradle` and fails loudly instead of building with some other JDK.
- IDEs find no wrapper: set the Gradle distribution to `mise where gradle` and the Gradle JDK to `mise where java`, and
  redo both after a pin changes. An IDE not yet reconfigured may regenerate the wrapper; `.gitignore` keeps it out.
- The wrapper's checksum-pinned distribution download is replaced by mise's install of the pinned Gradle version.
