# XNK Fit

A free, offline-first workout and meal app for people without a coach. Android first. Indonesian first.

**Status: skeleton.** The Android project builds (B-01): an empty Hari ini screen and the five-tab bar. The domain rules are in code with tests (B-02: targets, safety gates, pace check in `app.xnkfit.domain`); onboarding and the health screening (B-05, `ui/onboarding`, `ui/screening`) are the first screens to use them: the app opens on onboarding until a profile exists, nothing is saved before its last step, and under 13 it stops and keeps nothing. The bundled content loader and the signed content update are in `app.xnkfit.data.content` (B-04); the update stays off until the owner's public key is set (docs/Content-Update.md §7). Process: docs, then clickable mockups (owner approval), then code.

- [docs/Keputusan-Pemilik.md](docs/Keputusan-Pemilik.md): ringkasan keputusan untuk pemilik (Indonesia)
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

Needs JDK 17 or newer (CI uses 21), the Android SDK with platform 36 and build-tools 36.0.0 (`ANDROID_HOME` set, or `sdk.dir` in `local.properties`), and Python 3 and Node 20+ for the checks in `tools/`.

```sh
./gradlew testDebugUnitTest        # unit tests, including the Room database and migration tests (Robolectric, JVM)
./gradlew testDebugUnitTest --tests 'app.xnkfit.data.db.*'   # only the database tests
./gradlew testDebugUnitTest --tests 'app.xnkfit.data.content.*'   # content loader and update tests
node --test tools/test-content-manifest.mjs   # manifest generator and signer (throwaway key)
./gradlew testDebugUnitTest --tests 'app.xnkfit.data.backup.*'   # backup zip, restore and fake-Drive tests
./gradlew lintRelease              # Android Lint, warnings are errors
./gradlew assembleRelease          # R8 on; output is unsigned: app/build/outputs/apk/release/app-release-unsigned.apk
# Drive backup is off ("belum tersedia") unless the owner's OAuth client id is passed:
#   ./gradlew assembleRelease -Pxnkfit.driveClientId=...   (or env XNKFIT_DRIVE_CLIENT_ID; never commit it)
python3 tools/check_text_literals.py          # no hardcoded UI text (NF-3)
python3 -m unittest discover -s tools         # tests of that rule
tools/check_apk_size.sh app/build/outputs/apk/release/app-release-unsigned.apk   # 20 MB budget
tools/check_permissions.sh app/build/outputs/apk/release/app-release-unsigned.apk # Design 14 list
```

## Debug APK from CI (test build)

`.github/workflows/apk-debug.yml` builds `./gradlew assembleDebug` on every push to `main` and `claude/**` and on demand (Actions tab, "APK debug", Run workflow). The APK is signed with the stock Android debug keystore (no release key exists in this repo), has no Drive OAuth client id (Drive shows "belum tersedia") and is kept 14 days as the artifact `xnk-fit-debug-<commit>-<date>`. The job summary lists size, version, applicationId, SHA-256 and the manifest permissions.

Install on a phone:

1. Open the run in the Actions tab, download the artifact (a zip) from the Artifacts section and unzip it to get the `.apk`. Compare its SHA-256 with the job summary if you like.
2. Copy the APK to the phone, open it, and allow "install unknown apps" for the app you opened it from (Settings > Apps > Special access > Install unknown apps). Or from a computer with USB debugging on: `adb install -r xnk-fit-debug-<commit>-<date>.apk`.

Warnings: this is a test build. It is debuggable, not minified, signed with a shared debug key that anyone can reproduce, and it will not update over a release build later (different signature, uninstall first). Do not keep real data you cannot lose in it; make a backup from the app's Data screen first (needs a Drive client id, so not in this build; export is not guaranteed). The app id is `app.xnkfit`, so it replaces a previous debug build in place.

Remove it: Settings > Apps > XNK Fit > Uninstall, or `adb uninstall app.xnkfit` (this deletes the app's data). Artifacts disappear from GitHub after 14 days, or delete them by hand on the run page.

Database (B-03): the Room schema JSON is exported to `app/schemas/` on every build and is committed. Never edit or delete a committed version file. To change the schema: change the entities, bump `XnkDatabase.VERSION`, add the `Migration` to `XnkDatabase.MIGRATIONS`, add a test next to `MigrationTest.exampleMigrationPattern`, commit the new `N.json`. The database tests run on the JVM with Robolectric, not on a phone or emulator; see Design §3 and §13.

CI (`.github/workflows/ci.yml`) runs all of the above on every push and pull request. There is no signing key, keystore or client ID in this repo; release signing will come from CI secrets later. UI text lives in `app/src/main/res/values/strings.xml`. Speed benchmarks will live in `benchmark/` (B-19).
