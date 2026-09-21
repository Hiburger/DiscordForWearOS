# Contributing to DiscordWear

Hello there !  
Thanks for helping out; this is a small hobby project, a 3rd party Discord client for Wear OS, and every PR, bug report or idea is welcome.

One heads up before you start: this is a 3rd party client, which goes against Discord's ToS. Contributing carries the same (small) risks as using it. Also, note that anything you contribute is published under the repo's GPLv3.

## Setting things up

- **JDK 17** (Zulu or Temurin are fine). Newer JDKs mostly work, but Gradle 8.13 outright dies on JDK 24+ with `Unsupported class file major version`, so 17 is the safe pick (it is what CI uses too).
- **Android SDK** with platform 36 and build-tools 36 (Android Studio sets this up for you, or use `sdkmanager`).
- A watch or a Wear OS emulator is nice for manual testing, but not required to build.

The commands that matter:

```bash
./gradlew :app:assembleDebug      # hands you a debug APK -> app/build/outputs/apk/debug/
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

Here we mostly use conventional-ish prefixes, try to keep that up:

- `feat:` new stuff
- `fix:` bug fixes
- `docs:` documentation
- `style:` formatting / wording
- `api:` changes to the Discord API layer
- `chore:` builds, version bumps, tooling

Small and focused beats long and detailed. One topic per commit.

## Pull requests

- Base your branch on `main` branch
- One topic per PR. If you fixed two very unrelated things, open two PRs
- Write a short description of what and why
- **Do not bump `versionCode` / `versionName` / `UpdateChecker.CURRENT_VERSION`**. That is release management and happens only when publishing a release. The app compares `CURRENT_VERSION` against the latest GitHub release, so a premature bump either makes the update checker nag people about a release that does not exist or hides one that does.
- There is no CI on PRs (the Actions workflow is manual and needs signing secrets), so run the tests and lint yourself before pushing. If lint reports a new error, that is a bug in the PR, not noise!

## Reporting bugs

The most useful bug report for a Wear OS app contains:

1. Watch model + Wear OS version (Usually in Settings > System > About)
2. App version (Settings > About in the app)
3. What you did, what you expected, what happened  
4. (If it's a crash: `adb logcat` output from right after it happens. This is an annoying step, i'm not forcing you to do it but it helps me)

```bash
adb logcat -d > log.txt
```

Attach the file instead of pasting 500 lines into the issue :P

## Questions / ideas

Open an issue. Feature ideas are welcome but big ones are worth discussing in an issue first so nobody builds something that gets rejected in review. 

Thanks a lot for your help !
