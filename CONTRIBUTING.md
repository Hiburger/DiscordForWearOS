# Contributing to DiscordWear

Thanks for helping out! This is a small hobby project, a 3rd party Discord client for Wear OS, and every PR, bug report or idea is welcome.

One heads up before you start: this is a 3rd party client, which goes against Discord's ToS. Contributing carries the same (small) risks as using it. Anything you contribute is published under the repo's GPLv3.

## Setting things up

- **JDK 17** (Zulu or Temurin are fine). Do not use anything newer: Gradle 8.13 dies with `Unsupported class file major version` on JDK 18+.
- **Android SDK** with platform 36 and build-tools 36 (Android Studio sets this up for you, or use `sdkmanager`).
- A watch or a Wear OS emulator is nice for manual testing, but not required to build.

The commands that matter:

```bash
./gradlew :app:assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # unit tests
./gradlew :app:lintDebug          # Android lint, should report 0 errors
```

## Project map

Everything lives in `app/src/main/kotlin/com/zaffox/DiscordWear/`:

- `api/` - the Discord layer: `DiscordGateway` (websocket), `DiscordHttp` / `DiscordRestClient` (REST), `DiscordRepository` (state, what the UI collects)
- `screens/` - Compose UI, one file per screen (`ChatScreen`, `DmsScreen`, `ServerScreen`, ...)
- `NotificationService.kt` - foreground service that keeps the gateway alive in the background and raises message notifications
- `UpdateChecker.kt` - checks GitHub releases and offers in-app updates
- `SetupPreferences.kt`, `NotificationPolicy.kt` - settings storage and notification rules
- Unit tests live in `app/src/test/kotlin/`

## Commit messages

History uses conventional-ish prefixes, keep that up:

- `feat:` new stuff
- `fix:` bug fixes
- `docs:` documentation
- `style:` formatting / wording
- `api:` changes to the Discord API layer
- `chore:` builds, version bumps, tooling

Small and focused beats long and detailed. One topic per commit.

## Pull requests

- Base your branch on `main`.
- One topic per PR. If you fixed two unrelated things, open two PRs.
- Write a short description of what and why (the good ones are 5 lines, not 50).
- **Do not bump `versionCode` / `versionName` / `UpdateChecker.CURRENT_VERSION`**. That is release management and happens only when publishing a release. The app compares `CURRENT_VERSION` against the latest GitHub release, so a premature bump either makes the update checker nag people about a release that does not exist or hides one that does.
- There is no CI on PRs (the Actions workflow is manual and needs signing secrets), so run the tests and lint yourself before pushing. If lint reports a new error, that is a bug in the PR, not noise.

## Reporting bugs

The most useful bug report for a Wear OS app contains:

1. Watch model + Wear OS version (Settings > About on the watch)
2. App version (Settings > About in the app)
3. What you did, what you expected, what happened
4. If it is a crash: `adb logcat` output from right after it happens

```bash
adb logcat -d > log.txt
```

Attach the file instead of pasting 500 lines into the issue.

## Questions / ideas

Open an issue. Feature ideas are welcome, but big ones are worth discussing in an issue first so nobody builds something that gets rejected in review.
