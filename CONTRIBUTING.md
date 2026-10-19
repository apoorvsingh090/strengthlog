# Contributing to StrengthLog

Thanks for your interest in contributing! StrengthLog is a local-only,
offline Android app — changes must keep the "no account, no internet
access, no data leaves the device" promise intact.

## Development setup

Prerequisites:

- JDK 17
- Android SDK with platform `android-35` (Android Studio handles this)
- Gradle wrapper is committed — no separate Gradle install needed

```bash
git clone https://github.com/apoorvsingh090/strengthlog.git
cd strengthlog
```

Create `local.properties` in the repo root (git-ignored, generated
automatically by Android Studio):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Then build and verify:

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests (CSV, 1RM, Wilks, standards)
./gradlew lintDebug            # Android lint
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Making changes

- **Branch naming:** `feature/<short-description>` or `fix/<short-description>`
- **Commit messages:** concise and imperative, one logical change per commit
  (e.g. `Fix idempotent CSV re-import for cross-device timestamps`).
- **Code style:** follow the existing Kotlin style (official Kotlin coding
  conventions); let Android Studio format for you. Keep composables small and
  push state into ViewModels (`StateFlow`), matching the existing MVVM shape.
- **Room schema:** schema is exported to `app/schemas/`. If you change any
  `@Entity`, bump the DB version and provide a `Migration` — never bump
  `fallbackToDestructiveMigration` silently.
- **Tests:** new domain/CSV/standards logic needs unit tests under
  `app/src/test/`. UI-only changes should still be exercised manually and
  described in the PR.

## Pull requests

1. Fork and create a branch from `main`.
2. Make the change, add/update tests, run `testDebugUnitTest` + `lintDebug`.
3. Update `README.md` or `CHANGELOG.md` if user-visible behaviour changes.
4. Open a PR using the template; reference related issues (`Fixes #123`).
5. CI must be green before review.

## Reporting bugs

Use the **Bug report** issue template, search existing issues first, and
include device/Android version plus Logcat output when you have it.

## Feature ideas

Use the **Feature request** template. Features that break offline/local-only
operation (new permissions, network calls, accounts) are out of scope by
design — please don't open PRs that add the `INTERNET` permission.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md).
By participating you agree to abide by its terms.
