# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- GitHub Actions CI: unit tests (`testDebugUnitTest`) + Android lint (`lintDebug`)
- Community files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`
- Issue forms (bug report, feature request) and pull request template
- `CHANGELOG.md`

### Changed

- `gradlew` file mode set to executable so CI can run the wrapper on Linux

## [0.1.0] - 2026-10-18

### Added

- Initial release: workout logging (sets, reps, weights, warm-ups, bodyweight)
- Reusable workout templates
- 160-exercise seeded catalog plus custom exercises
- Per-exercise reports: history, best sets, estimated 1RM progression
- Powerlifting standards with percentile estimate and Wilks score
- 1RM calculators (Epley and related estimators)
- CSV import/export, including my.strengthlevel.com exports (lb→kg conversion)
- Idempotent re-import via per-workout content fingerprint
- Offline-only architecture: no `INTERNET` permission, all data on-device (Room)
