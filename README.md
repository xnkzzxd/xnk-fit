# XNK Fit

A free, offline-first workout and meal app for people without a coach. Android first. Indonesian first.

**Status: skeleton.** The Android project builds (B-01): an empty Hari ini screen and the five-tab bar. The domain rules are in code with tests (B-02: targets, safety gates, pace check in `app.xnkfit.domain`); onboarding and the health screening (B-05, `ui/onboarding`, `ui/screening`) are the first screens to use them: the app opens on onboarding until a profile exists, nothing is saved before its last step, and under 13 it stops and keeps nothing. Process: docs, then clickable mockups (owner approval), then code.

- [docs/PRD.md](docs/PRD.md): product requirements (D-01)
- [docs/Design.md](docs/Design.md): how it is built (D-02)
- [docs/Rules.md](docs/Rules.md): every number and its source (D-03)
- [docs/Content.md](docs/Content.md): exercises, food, badges, content updates (D-04)
- [docs/mockups/](docs/mockups/README.md): clickable mockups, open `docs/mockups/index.html` (D-05)
- [docs/Google-OAuth.md](docs/Google-OAuth.md): Google sign-in checklist (S-03)
- [docs/Play-Health.md](docs/Play-Health.md): Play health declarations (S-04)
- [docs/Content-Update.md](docs/Content-Update.md): content update host (S-06)
- [docs/TODO.md](docs/TODO.md): task list with the recommended model per task

## Build and test

Needs JDK 17 or newer (CI uses 21), the Android SDK with platform 36 and build-tools 36.0.0 (`ANDROID_HOME` set, or `sdk.dir` in `local.properties`), and Python 3 for the checks in `tools/`.

```sh
./gradlew testDebugUnitTest        # unit tests, including the Room database and migration tests (Robolectric, JVM)
./gradlew testDebugUnitTest --tests 'app.xnkfit.data.db.*'   # only the database tests
./gradlew lintRelease              # Android Lint, warnings are errors
./gradlew assembleRelease          # R8 on; output is unsigned: app/build/outputs/apk/release/app-release-unsigned.apk
python3 tools/check_text_literals.py          # no hardcoded UI text (NF-3)
python3 -m unittest discover -s tools         # tests of that rule
tools/check_apk_size.sh app/build/outputs/apk/release/app-release-unsigned.apk   # 20 MB budget
tools/check_permissions.sh app/build/outputs/apk/release/app-release-unsigned.apk # Design 14 list
```

Database (B-03): the Room schema JSON is exported to `app/schemas/` on every build and is committed. Never edit or delete a committed version file. To change the schema: change the entities, bump `XnkDatabase.VERSION`, add the `Migration` to `XnkDatabase.MIGRATIONS`, add a test next to `MigrationTest.exampleMigrationPattern`, commit the new `N.json`. The database tests run on the JVM with Robolectric, not on a phone or emulator; see Design §3 and §13.

CI (`.github/workflows/ci.yml`) runs all of the above on every push and pull request. There is no signing key, keystore or client ID in this repo; release signing will come from CI secrets later. UI text lives in `app/src/main/res/values/strings.xml`. Speed benchmarks will live in `benchmark/` (B-19).
