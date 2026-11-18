# Design — XNK Fit

| | |
| --- | --- |
| Status | Draft, 2026-11-16 (task D-02) |
| Reads | [PRD.md](PRD.md) (what). Rules and numbers: [Rules.md](Rules.md) (D-03). Food and exercise content: [Content.md](Content.md) (D-04). |
| Scope | How the app is built: stack, data, screens, logic, backup, speed, tests. Visual mockups come in D-05, not here. |

Every requirement ID (G-, PR-, O-, N-, H-, L-, M-, P-, R-, B-, D-, S-, PV-, NF-) comes from the PRD. This file cites them; it never renumbers them.

## 1. Platform and stack

| Choice | Decision | Why |
| --- | --- | --- |
| App type | Native Android app, Kotlin, Jetpack Compose | Android first (PRD §2). Compose is the platform default and keeps the APK small. |
| Min SDK | Android 8.0 (API 26) | PRD NF-4. Covers almost all low-end phones still in use. |
| Target SDK | API 36 (Android 16) | Checked 2026-11-17 (S-02). New apps and updates must target API 36+ since 2026-08-31 ([Play Help](https://support.google.com/googleplay/android-developer/answer/11926878), [Android docs](https://developer.android.com/google/play/requirements/target-sdk)); existing apps need API 35+ to stay visible to new users on newer Android. The extension to 2026-11-01 is past. Next raise: date not published by Google, belum bisa dicek; the [policy](https://support.google.com/googleplay/android-developer/answer/11917020) says target within one year of the latest Android release, so API 37 is likely around 2027-08-31 (inference, not official). Re-check at release. Min SDK 26: no Play minSdk rule on any of these pages, so API 26 is allowed. |
| Local database | Room over SQLite, one file `xnkfit.db` | PR-1, offline. Room gives migrations and tests (NF-5). |
| Key-value settings | Preferences DataStore | Small flags only; no data that needs relations. |
| Background work | WorkManager for reminders scheduling and the Drive backup that runs on open (B-2) | No foreground service, no background daemon (B-2). |
| Notifications | Local only, `AlarmManager` exact alarms only for the rest timer (L-8) | PR-3, R-1. Exact-alarm permission requested only when the timer is first used. |
| Charts | Hand-drawn Compose `Canvas` | No chart library: size and speed (NF-1). |
| Photos | App-private storage, JPEG re-encoded at 1080 px max | PV-3. Never in the media store. |
| Drive | Google Drive REST API, `drive.appdata` scope (hidden app folder) | B-1. |
| Health Connect | Android Health Connect client, read-only steps | L-15. |
| Build | Gradle, version catalog, R8 on release | NF-1 (size), NF-2. |
| Dependencies | Keep the list short. Each new library needs a line in §12 with its reason | Speed and size budget. |

No analytics SDK, no ads SDK, no crash SDK that sends user data (PV-1). Store crash reports come from Play Console only (PR-3).

## 2. Architecture

```
UI (Compose screens, one ViewModel per screen)
  -> Domain (pure Kotlin: rules, calculators, progression, planner)
       -> Data (Room DAOs, DataStore, Drive client, content loader)
```

- **Domain has no Android imports.** Every number from [Rules.md](Rules.md) lives there as a named constant with its rule ID (for example `R_MSJ_*`, `R_FLOOR_*`). This makes the rules unit-testable on the JVM.
- **Screens never compute targets.** A ViewModel calls the domain and renders the result (same rule as XNK Coach: "backend first" becomes "domain first").
- **One source of truth:** Room. UI reads `Flow`s; a write updates the Flow.

Packages:

| Package | Contents |
| --- | --- |
| `domain.rules` | Targets, BMR, TDEE, safety gates, pace check, streak, points |
| `domain.training` | Program generator, progression, return-after-absence, muscle map counts |
| `domain.food` | Serving math, household-measure conversion, macro sums, planner limits |
| `data.db` | Room entities, DAOs, migrations |
| `data.content` | Loader for bundled content and the checksummed content update |
| `data.backup` | Zip writer/reader, manifest, Drive upload/download |
| `ui.*` | One package per tab, plus `ui.onboarding`, `ui.settings`, `ui.common` |
| `strings` | All UI text (NF-3) |

## 3. Data model

All tables have `id` (UUID text), `createdAt`, `updatedAt` (epoch ms). Deletes are hard deletes except where a row has backup history (see §9). Money never appears.

| Table | Key columns | Notes |
| --- | --- | --- |
| `profile` | sex, birthYear, heightCm, goal, interest, nickname, healthRisk (bool), screeningDone, pregnant, eatingHistory | One row. Sensitive health fields stay on the phone (PV-1). |
| `measure` | type (weight, waist, + six opt-in), value, unit, takenAt | Weight by default. Unit always metric. |
| `photo` | path, takenAt, kind (front, side, back), hiddenFromBackup | Files in app-private storage; rows reference them. |
| `equipment` | setting (gym, home-bw, home-db, custom), items (set of codes) | Tick-your-own = `custom`. |
| `exercise` | id (stable code, e.g. `SQUAT_GOB`), name_id, name_en, pattern, muscles, level, variantOf, cues_en, hint_id | Read-only from content. Patterns and muscles from Content.md. |
| `program` | id, source (`auto`, `own`, `jelajahi`), setting, rulesVersion, createdAt, isActive | `auto` rows are regenerated; `own` rows never are (L-5). |
| `program_day` | programId, position, name, estMinutes | |
| `program_item` | dayId, exerciseId, position, setsTarget, repMin, repMax, restSec, supersetGroup, setKindDefault | |
| `workout` | id, programDayId?, mode (dipandu, bebas), startedAt, endedAt?, feeling 1–5, note | Written on every tap (PR-2). `endedAt` null = unfinished. |
| `workout_set` | workoutId, exerciseId, position, kind (normal, drop, failure, …), weightKg?, reps?, bandLevel?, rpe?, form (rapi, ragu), done (bool), doneAt | Band level replaces kg for bands (L-2). |
| `activity` | id, type (code), minutes, effort (light, moderate, vigorous), kcal, at | Kcal from MET (Rules.md). |
| `food` | id, source (tkpi, packaged, warung-est, own), name_id, name_en, kcalPer100, protein, carb, fat, fibre, sodium, household (list of measure codes + gram weight), isEstimate, halal, allergens | TKPI rows are bundled. |
| `recipe` | id, name, servings, ingredients (foodId + gram), ownOrBundled | Own recipes computed from `food`. |
| `food_log` | id, day (local date), slot (pagi, siang, malam, camilan), foodId?, recipeId?, grams, kcal, protein, carb, fat, quickKcal? | Kcal stored at write time so the log never changes when a food row updates. |
| `meal_plan` | id, weekStart, slot, day, optionCode, locked (bool), cookOnce (bool), eatOut (bool) | M-3. |
| `reminder` | type, hour, minute, days, enabled | R-1. |
| `points_event` | id, at, kind (workout, activity, foodDay, measure, streakWeek, target), points | Level = sum. Never from weight (P-3). |
| `badge` | code, earnedAt | 18 codes in Content.md. |
| `journey_moment` | id, at, text, kind, photoId? | P-2. |
| `backup_meta` | lastBackupAt, lastDeviceId, lastDriveFileIds | B-6 compares device ids. |
| `schema_meta` | version, appVersionCreated | B-5 newer-version check. |

Indexes: `food_log(day)`, `workout(startedAt)`, `workout_set(workoutId)`, `measure(type, takenAt)`.

Migrations: Room `Migration` objects, each tested with the schema JSON exported per release (NF-5 "migration tests from every released schema"). Rule: append columns or add tables only; never drop a column in v1.x.

## 4. Logging, saving and crash safety

PR-2 and NF-2 mean the database is written on each tap:

1. Opening a workout inserts `workout` with `endedAt = null`.
2. Each set change writes its `workout_set` row in the same transaction as any derived counters.
3. "Selesai" sets `endedAt` and creates the `points_event` rows.
4. On launch, any `workout` with `endedAt = null` from today or yesterday shows "Lanjutkan latihan?". Older unfinished ones are closed with their last set and a neutral note.

Rest timer: the end time is stored (`restEndsAt`), not a counter, so a killed app resumes correctly. The alarm is scheduled with `setAlarmClock`-style exact alarm (L-8); it fires with the screen off or after a kill.

Food log: writes are immediate. No queue is needed because nothing leaves the phone (the PRD removed the send step).

Tests: a kill test (NF-2) runs the logger, kills the process at each step, relaunches and checks the state.

## 5. Screens and navigation

Bottom bar (phone): **Hari ini · Latihan · ＋ · Makan · Saya** (N-1). The ＋ opens a sheet with four actions (N-2). Phone back steps: sheet → screen → tab → exit prompt (N-3). Tablets (≥ 600 dp wide): side rail with the same five destinations and two-column content (N-4).

| Screen | Content (PRD ref) |
| --- | --- |
| Onboarding | Goal, sex/birth year/height/weight, interest, nickname, one health question, permissions (O-1…O-6). Under 13 stops here (O-5). |
| Screening | Full health form, opened from the question or from the first program (S-1, O-2). |
| Hari ini | Next workout or rest-day card, calorie ring, week strip, level and streak (H-1). Non-lifters get the activity target instead (H-2). Empty cards name what fills them, with one button (H-3). |
| Latihan | Program day list, own routines, Jelajahi shelf (L-9), activity log entry, muscle map per week (L-11), review proposals (L-4). |
| Logger | Dipandu or Bebas, chosen at first workout (L-6). Set kinds, supersets, rest timer, swap, suggestions, previous numbers (L-7). |
| Ringkasan | Summary after finish: numbers, records, session muscle map, feeling, note, share (L-13). |
| Makan | Target ring, macros, meal options (3 per meal, M-3), food log, week planner (M-3, M-10), shopping list (M-8). |
| Makan → Pilih makanan | Search TKPI and packaged foods, household measures first (M-6, M-7). |
| Saya | Profile, measures and charts (P-1), journey (P-2), athlete card, Kartu saya (P-2, P-6), badges (P-5), settings (P-7). |
| Pengaturan | Profil, Latihan, Makan, Pengingat, Tampilan, Data, Bantuan — in that order (P-7). |
| Bantuan | Target sources, welcome cards, how-to, glossary, privacy, sources (P-8). |

Welcome cards: one per tab, shown once, dismissable (P-8).

Empty states: a line of what fills the screen plus one button. No sample data anywhere (H-3).

## 6. Program generator and progression

Pure function in `domain.training`:

`generate(profile, equipment, goalFocus, history) -> Program`

Steps:

1. Filter exercises by equipment setting. A pattern with no valid exercise is reported as missing with the plain sentence from L-3, never filled with a wrong pattern.
2. Pick one main and one accessory per pattern from the level-appropriate variants (Rules.md §Latihan).
3. Set sets/reps/rest from the level table in Rules.md.
4. Attach a reason string per exercise ("Kenapa gerakan ini", L-11).

Progression (L-2, L-4): double progression for weights: when every working set reaches the top of the rep range with form "rapi", propose +2.5 kg (upper) or +5 kg (legs). For bodyweight and limited weights: reps to the top, then +1 set, then the next harder variant (`variantOf`). Bands: band level + reps.

Form hold (L-12): a "ragu" self-rating holds the load until two "rapi" in a row. Reps and sets may still progress.

Review (L-4): every 4 weeks or after 3 completed sessions, the generator reruns with the log. It returns a diff. The user accepts or declines each change; the one-line reason is stored on the change.

Return after absence (L-16): gap ≥ 14 days → the first session uses 60 % of the last working loads and one fewer set. No banner.

Own routines (L-5): a program row with `source = own` is never touched by the generator. Editing an `auto` program turns it into `own` on save.

## 7. Food and targets

Formulas are in [Rules.md](Rules.md): Mifflin–St Jeor BMR, activity factor, goal adjustment, safety floors (S-2…S-8). The app shows the calculation and its source in Bantuan (P-8).

Calorie target:

- Goal "turun" / "naik" / "jaga" with the deficit or surplus from Rules.md.
- Floor: never below the sex floor or BMR (S-4).
- Activity calories are shown separately and never added (M-1).

Recalculation prompt (M-2): when weight differs from the value used by ≥ 3 kg, or the pace check (S-8) fires, show the reason and a button "Hitung ulang target" or "Pertahankan". The user decides.

Meal planner (M-3, M-4):

- Each meal slot has three options from `meal_plan` candidates (Content.md, Makan packages).
- Limits (santan per week, goreng per day, the same main twice in a day) apply when choosing automatic options only. Manual choices are never blocked; the screen says "Minggu ini santan sudah 3 kali" kindly (M-4).
- Week planner: swap one option, lock a day's meal, mark "masak sekali" (batch: the same option repeated) or "makan di luar" (uses a bought-meal estimate).
- Phone: one day per screen with horizontal swipe (M-10). Tablet: 7-column table.

Food log (M-7): recent and favourite lists, copy meal, copy yesterday, quick kcal entry. Calories and macros are copied into `food_log` when logged.

Household measures (M-6): shown as "1 centong (±100 g)". Grams beside. Conversion from `food.household`.

## 8. Reminders and notifications

- Local notifications only (R-1). Channels: Latihan, Makan, Timbang, Istirahat (rest timer).
- Scheduling: `WorkManager` periodic check at 15 min intervals, plus exact alarms only for the rest timer. Reminders fire at the user's hour (R-2).
- Max three reminders per day; a reminder is skipped when its action is already done (workout logged today, food logged for the slot).
- Text: from `strings`, varied by a rotation index, short and neutral (R-2). The rotation never repeats the same text twice in a row.
- Permission asked at the moment of use (O-3).

## 9. Backup, restore and export

Format (B-4): one zip, `xnkfit-backup-<date>.zip`:

- `manifest.json`: schema version, app version, device id, created at, per-file SHA-256 and row counts.
- `data/<table>.json` per table.
- `photos/` only when photo backup is on (B-3).
- Export additionally writes readable CSVs (`csv/`), not used by restore.

Drive (B-1): `drive.appdata` scope; file names `backup-<timestamp>.zip`. Keep the last three; delete older ones after a successful upload.

Automatic (B-2): on app open or resume, if on Wi-Fi and last backup > 24 h, run in WorkManager one-time work. Manual button always available.

Restore (B-5): the Data screen shows the manifest summary, a warning that everything on the phone is replaced, and a typed confirmation word. Manifest schema > app schema → refuse with "perbarui aplikasi dulu". Checksums are verified before any write; the import runs in one transaction. Restore never merges.

Another phone (B-6): if the manifest's device id differs from `backup_meta.lastDeviceId`, ask before overwrite.

Delete all (B-7): removes the database, photos, DataStore and cached content, then restarts at onboarding.

## 10. Speed budget and performance

Targets from NF-1, measured on the reference phone. Proposed model (S-05, 2026-11-18): **Xiaomi Redmi 9A, 2 GB RAM, MediaTek Helio G25 (8× Cortex-A53, up to 2.0 GHz), 720×1600 screen, Android 10 (MIUI 12)**. Two points differ from the first plan and need the owner's OK before a phone is bought: (a) it runs Android 10, not 8–9, so Android 8–9 is not yet tested; a 2 GB Android 8–9 phone would be the stricter test; (b) store listings found on 2026-11-18 showed it out of stock, so check local availability. The CPU is a MediaTek, not a Snapdragon 4xx; the class is "2 GB, octa-core A53".
Sources: https://m.gsmarena.com/xiaomi_redmi_9a-10279.php (listing search, 2026-11-18).

| Measure | Budget | How it is checked |
| --- | --- | --- |
| Cold start to usable Hari ini | 2.0 s | Macrobenchmark, CPU throttled 4× |
| Tab switch | 100 ms | Macrobenchmark frame timing |
| Set tick saved to disk | 50 ms | Unit timing on the DAO path |
| Food search per keystroke | 100 ms | Benchmark on the full TKPI table |
| Installed size | 20 MB | APK size check in CI |

Rules:

- Baseline profiles committed for the cold-start and tab paths.
- Food search uses a prebuilt FTS4 table, not a LIKE scan.
- No image loads on the first frame; exercise figures are vector drawings (see Content.md) and cache as `ImageBitmap` on demand.
- Charts draw on `Canvas`, one pass per series.
- CI fails the build when a benchmark exceeds its budget (NF-1).

## 11. Visual system (summary for D-05)

Full mockups come in D-05. The rules here are the ones the code relies on:

- **Theme:** Material 3 color roles mapped from seven accents (D-1). Light and dark both supported. Colors are tokens, never literals in screens.
- **Red** is used for nothing except errors (PR-4). No red for calories over target or for weight gain.
- **Type:** Inter (bundled with the app, same as the XNK coaching app; owner decision in the D-05 review, 2026-11-16), weights 400–900, headings tight (−0.02 to −0.03 em). Sizes scale with the user's font setting (D-1 big text). Layout must stay usable at 200 % font scale.
- **Look:** the XNK app's Phase X portal (owner, D-05 review 2): its colour tokens, black band with contour lines, black hero cards with rings, bar-marked section titles, floating black tab dock, black segmented bars and sheet heads. Little text: one short line per item. Two buttons side by side always share the row evenly.
- **Shapes:** buttons and choice chips are rounded rectangles (12 dp), not pills. Choice rows and lone buttons fill the full width; no half-empty rows (owner, D-05 review).
- **Touch:** 44 dp minimum (D-2). Logger inputs use the numeric keyboard; steppers ＋ / − beside each number field.
- **Layout:** 360–430 dp phones, tablets ≥ 600 dp with the rail. No horizontal scrolling except the week planner swipe (M-10) and the food-list carousel.
- **Motion:** respects the system "remove animations" setting; the rest timer and charts are the only animated parts.
- **Icons:** Material Symbols, outlined.
- **Muscle map and exercise figure:** own vector drawings (Content.md §3). Monochrome; main muscle uses the accent, helper muscle a tint.

## 12. Dependencies (initial)

| Library | Why | Size concern |
| --- | --- | --- |
| Jetpack Compose + Material 3 | UI | Baseline, R8 shrinks |
| Room | Database (§3) | Small |
| DataStore | Settings | Small |
| WorkManager | Reminders, backup on open | Small |
| Health Connect client | Steps (L-15) | Hidden where missing |
| Google Sign-In + Drive REST via Ktor/OkHttp | Backup (B-1) | Keep to one HTTP client |
| kotlinx.serialization | Backup JSON | Small |
| JUnit, Robolectric, Compose UI test, Macrobenchmark | Tests (NF-5) | Test only |

Anything else needs a row here first.

## 13. Tests and quality gates (NF-5)

| Gate | Tool | Runs on |
| --- | --- | --- |
| Domain rules | JUnit, pure JVM; every rule ID has at least one test | Every PR |
| Generator | JUnit with golden programs per equipment setting | Every PR |
| Migrations | Room migration tests from each exported schema | Every PR |
| Backup round trip | Unit: export → import equals source | Every PR |
| Kill test | Instrumented: kill at each logger step | Nightly |
| Screen tests | Compose UI tests, phone and tablet, light and dark | Every PR |
| Speed | Macrobenchmark budgets (§10) | Nightly and before release |
| Text literals | Lint rule: no string literal in `ui.*` (NF-3) | Every PR |

Rule tests: each number in Rules.md is asserted in a test that names its rule ID. A rule with ⚠ in Rules.md has a test marked `@Ignore("unverified")` until closed. After S-07 (2026-11-18) the ⚠ rules are R_ACT (G1), R_HH (G7), bought and regional meal numbers (G8), R_TRN_RETURN (G9) and R_LEVEL (tuning); every other rule test runs. R_FIBRE, R_TRN_SETS and R_TRN_REPS assert the values changed in S-07.

## 14. Security and privacy in code

- No network call except: Drive (user-chosen), content update (`GET` of static files with checksum), Health Connect (local). The app has no backend to call.
- Content update (PV-2): downloads a manifest and files over HTTPS from the project's static host (GitHub Pages, [Content-Update.md](Content-Update.md)); checksum and manifest signature verified; no request body; no user data.
- Google sign-in tokens live in the system account store, not in app files.
- Photos are excluded from backup unless B-3 is on; their rows set `hiddenFromBackup`.
- Release builds strip `Log` calls with R8 so no user value reaches logcat.
- `android:allowBackup="false"`: Android's own cloud backup must not copy the database outside the user's Drive choice.
- The manifest asks only for: notifications, camera (photos), Health Connect read (steps), internet, exact alarm (rest timer only). Nothing else.

## 15. Accessibility

- Content descriptions on every icon button.
- Touch 44 dp (D-2); focus order follows reading order.
- Colour is never the only signal (muscle map has labels, planner has text).
- Big text up to 200 % (§11).

## 16. Open design questions

| # | Question | Default until decided |
| --- | --- | --- |
| 1 | Exact reference phone model | Proposed Redmi 9A in §10 (S-05); owner confirms, then budgets are measured on it |
| 2 | Content update host | Decided in S-06 (proposed, owner confirms): GitHub Pages of a project content repo, per-file SHA-256 and a signed manifest; details in [Content-Update.md](Content-Update.md) |
| 3 | Whether the figure is drawn with Compose Canvas or a static SVG asset | Decided in D-06 (2026-11-17): reuse the XNK app's figures, the muscle map (`figure.min.json`, react-muscle-highlighter shapes, MIT, credits kept) and the animated exercise rig (`rig.min.json`, XNK's own work); new rigs only for lifts XNK lacks. |
