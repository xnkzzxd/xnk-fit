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
| `strings` | All UI text (NF-3). On Android this is not a Kotlin package but the resource file `app/src/main/res/values/strings.xml` (Indonesian), with `values-en/` added later (B-2x). Kotlin reads it through `stringResource`. Code packages live under `app.xnkfit` (for example `app.xnkfit.domain.rules`). |

## 3. Data model

All tables have `id` (UUID text), `createdAt`, `updatedAt` (epoch ms). Deletes are hard deletes except where a row has backup history (see §9). Money never appears.

| Table | Key columns | Notes |
| --- | --- | --- |
| `profile` | sex, birthYear, heightCm, goal, interest, nickname, healthRisk (bool), screeningDone, pregnant (pregnant or breastfeeding, one answer), eatingHistory | One row. Sensitive health fields stay on the phone (PV-1). |
| `measure` | type (weight, waist, + six opt-in), value, unit, takenAt | Weight by default. Unit always metric. |
| `photo` | path, takenAt, kind (front, side, back), hiddenFromBackup | Files in app-private storage; rows reference them. |
| `equipment` | setting (gym, home-bw, home-db, custom), items (set of codes) | Tick-your-own = `custom`. |
| `exercise` | id (stable code, e.g. `SQUAT_GOB`), name_id, name_en, pattern, muscles, level, variantOf, cues_en, hint_id | Read-only from content. Patterns and muscles from Content.md. |
| `program` | id, source (`auto`, `own`, `jelajahi`), kind (`program`, `routine`; B-08), setting, rulesVersion, createdAt, isActive | `auto` rows are regenerated; `own` rows never are (L-5). |
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
| `points_event` | id, at, kind (workout, activity, foodDay, measure, streakWeek, loggedWeek), points | Level = sum. Never from weight (P-3). |
| `badge` | code, earnedAt | 18 codes in Content.md. |
| `journey_moment` | id, at, text, kind, photoId? | P-2. |
| `backup_meta` | lastBackupAt, lastDeviceId, lastDriveFileIds | B-6 compares device ids. |
| `schema_meta` | version, appVersionCreated | B-5 newer-version check. |
| `content_meta` | contentVersion, source (bundled, update), filesJson (file name to SHA-256), lastCheckAt, lastAttemptAt | B-04. Installed content and the update check clock (Content-Update.md §3). Singleton id `content`. |
| `content_doc` | id (`badges`, `meal-packages-meta`), bodyJson | B-04. Whole bundled documents nobody queries inside. |
| `meal_package` | id, profile, slot, bodyJson | B-04. The 72 bundled packages (Content.md §4.5); the planner reads by profile and slot. |

Indexes: `food_log(day)`, `workout(startedAt)`, `workout_set(workoutId)`, `measure(type, takenAt)`.

Implementation (B-03, `app.xnkfit.data.db`): the schema is version 1, 21 tables plus the FTS4 table `food_fts` (§10). Deviations and choices, all inside this text:

- Every table has `id`, `createdAt`, `updatedAt`. Singletons use a fixed id (`profile` = `me`, `backup_meta` = `backup`, `schema_meta` = `schema`); `badge.id` is the badge code, so a badge is earned once. `recipe.ownOrBundled` is the column `origin`.
- Lists (muscles, equipment items, household measures, allergens, ingredients, reminder days, Drive file ids) are JSON text columns named `...Json`; no query looks inside them.
- Foreign keys exist only where children must die with the parent: `program_day` → `program`, `program_item` → `program_day`, `workout_set` → `workout` (cascade). `workout.programDayId`, `program_item.exerciseId`, `food_log.foodId`, `food_log.recipeId` and `journey_moment.photoId` have no foreign key, so regenerating a program, a content update or deleting an own food never alters history.
- Unique keys: `(workoutId, position)` on `workout_set`, `(programId, position)` on `program_day`, `(dayId, position)` on `program_item`, `(weekStart, day, slot)` on `meal_plan` (a new row for the same slot replaces the old one).
- `workout.restEndsAt` holds the rest-timer end time (§4). A set write is one transaction: the set row plus the workout's `updatedAt` and `restEndsAt`. The set DAO does update-then-insert, not Room `@Upsert`, because `@Upsert` swallows a unique-key clash and would drop a tapped set without an error.
- `schema_meta` is written once when the file is created.

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

Implementation (B-07): `domain.logger` (pure: next set, supersets, rest end, resume window; Rules.md R_TRN_LOGGER), `data.logger.LoggerRepository` (Room), `ui.logger` (screen, ViewModel, alarm). Readings and deviations, all inside this text:

- **Plan on disk at start.** A workout that follows a program day writes every planned set (not done, prefilled with the B-06 suggestion, else last time's same set, else the bottom of the rep range; kg never invented) in the same transaction as the `workout` row. Each later tap (number, stepper, form, kind, tick, add, delete, swap, superset, rest change) is one Room transaction through a single mutex, in tap order. Set order is `position = slot × 100 + set number`.
- **Schema.** v1 (unreleased) got `workout_set.holdSec`, `supersetGroup`, `restSec`, `exercise.settingsJson`, `equipmentJson`, `program.level`; JSON regenerated, no migration (Decisions.md).
- **Modes.** Both modes work on the same rows; the menu switches mode mid-workout. With no active program (the Latihan screen that creates one is B-08) the user adds exercises in either mode. "Seterusnya" in the swap sheet is not built: changing a program for good is own-routine editing (B-08, L-5); swap is "Hanya hari ini".
- **Resume.** At launch an unfinished workout from today or yesterday opens the Latihan tab on "Lanjutkan latihan?" (Lanjutkan / Selesaikan); older ones are closed at their last done set with no note (no text was specified). Feeling, note and the summary screen (L-13) are B-08.
- **Rest alarm.** `setExactAndAllowWhileIdle` (RTC_WAKEUP), not `setAlarmClock`, which would show an alarm-clock icon and "next alarm" for a 90 s rest. A not-exported `RestAlarmReceiver` posts one notification on the "Istirahat" channel; no service. Notifications (Android 13+) and the exact-alarm page (Android 12+) are asked once, when the first rest timer starts; a "no" leaves the in-app timer with the line "Pengingat istirahat mati…" and an "Izinkan" button. Alarms do not survive "Force stop" or a reboot (no boot receiver, §14).
- **Not verified here:** no phone or emulator in the sandbox. The kill test is simulated on the JVM (close the database without finishing, open a new instance on the same file, Robolectric); the real process kill at each step stays with B-18. The set write measured 0.7 ms median, 1.7 ms p95 on the JVM (budget 50 ms, §10); the phone figure is B-19.

## 5. Screens and navigation

Bottom bar (phone): **Hari ini · Latihan · ＋ · Makan · Saya** (N-1). The ＋ opens a sheet with four actions (N-2). Phone back steps: sheet → screen → tab → exit prompt (N-3). Tablets (≥ 600 dp wide): side rail with the same five destinations and two-column content (N-4). Implementation (B-17): `ui.adaptive` (`widthClassOf`, `useTwoColumns`, `TwoColumns`) measures the window width (`LocalWindowInfo`) and font scale; no WindowSizeClass dependency. At font scale 2.0 a 600 dp window keeps one column.

| Screen | Content (PRD ref) |
| --- | --- |
| Onboarding | Goal, sex/birth year/height/weight, interest, nickname, one health question, permissions (O-1…O-6). Under 13 stops here (O-5). Built in B-05 (`ui/onboarding`): `OnboardingViewModel` holds everything typed in memory and writes `profile` plus the first `measure` (weight) only at "Masuk"; all gate decisions are `domain.rules.evaluateTargets`. Deviations from the mockup: "Pulihkan cadangan" is shown disabled until B-16; the permissions screen asks notifications and camera for real, while Health Connect and Google Drive rows only say they are asked when used (their libraries arrive in B-14 and B-16). Activity level is not asked in onboarding (O-4), so the gates run with `JARANG`; it cannot change which gate applies. |
| Screening | Full health form, opened from the question or from the first program (S-1, O-2). `ui/screening`: `ScreeningScreen` and `ScreeningResultScreen` are stateless and reusable; today only onboarding opens them, the "first program" entry comes with B-06. The profile row keeps only `healthRisk` (any of the five risk answers), `pregnant` and `eatingHistory` (`ya`/`tidak`); the single answers are not stored. |
| Hari ini | Next workout or rest-day card, calorie ring, week strip, level and streak (H-1). Non-lifters get the activity target instead (H-2). Empty cards name what fills them, with one button (H-3). |
| Latihan | Program day list, own routines, Jelajahi shelf (L-9), activity log entry, muscle map per week (L-11), review proposals (L-4). |
| Logger | Dipandu or Bebas, chosen at first workout (L-6). Set kinds, supersets, rest timer, swap, suggestions, previous numbers (L-7). |
| Ringkasan | Summary after finish: numbers, records, session muscle map, feeling, note, share (L-13). |
| Makan | Target ring, macros, meal options (3 per meal, M-3), food log, week planner (M-3, M-10), shopping list (M-8). |
| Makan → Pilih makanan | Search TKPI and packaged foods, household measures first (M-6, M-7). |
| Saya | Profile, measures and charts (P-1), journey (P-2), athlete card, Kartu saya (P-2, P-6), badges (P-5), settings (P-7). |
| Pengaturan | Profil, Latihan, Makan, Pengingat, Tampilan, Data, Bantuan — in that order (P-7). Built in B-14 (`ui/pengaturan`, `data/prefs`): Pengingat switches, Tampilan (theme; units are metric only), Izin, manual content check, Data, Hapus semua data, Bantuan, Privasi. Profil, Latihan and Makan sections are not built yet (see Decisions.md). Opened from Saya through `ui/nav/Overlay`. |
| Bantuan | Target sources, welcome cards, how-to, glossary, privacy, sources (P-8). Built in B-14 (`ui/bantuan`); the privacy text is `strings_privacy.xml` = `docs/Privacy-Policy.md`. |

Welcome cards: one per tab, shown once, dismissable (P-8).

Empty states: a line of what fills the screen plus one button. No sample data anywhere (H-3).

## 6. Program generator and progression

Pure function in `domain.training`:

`generate(profile, equipment, goalFocus, history) -> Program`

Steps:

1. Filter exercises by equipment setting. A pattern with no valid exercise is reported as missing with the plain sentence from L-3, never filled with a wrong pattern.
2. Pick one main and one accessory per pattern from the level-appropriate variants (Rules.md §Latihan).
3. Set sets/reps/rest from the level table in Rules.md.
3a. Weekly volume (L-17): sum working sets per muscle group (main 1, helper 0.5; R_TRN_VOL_COUNT). Raise groups under the level minimum (R_TRN_VOL_FILL), trim above 20. Golden tests assert the totals per equipment setting.
4. Attach a reason string per exercise ("Kenapa gerakan ini", L-11).

Progression (L-2, L-4): double progression for weights: when every working set reaches the top of the rep range with form "rapi", propose +2.5 kg (upper) or +5 kg (legs), limited to 10 % of the load and rounded down to the equipment step (R_TRN_PROG_STEP). For bodyweight and limited weights: reps to the top, then +1 set, then the next harder variant (`variantOf`). Bands: band level + reps.

Form hold (L-12): a "ragu" self-rating holds the load until two "rapi" in a row. Reps and sets may still progress.

Review (L-4): every 4 weeks or after 3 completed sessions, the generator reruns with the log. It returns a diff. The user accepts or declines each change; the one-line reason is stored on the change.

Return after absence (L-16): gap ≥ 14 days → the first session uses 60 % of the last working loads and one fewer set. No banner.

Own routines (L-5): a program row with `source = own` is never touched by the generator. Editing an `auto` program turns it into `own` on save. B-08 (`data/program/ProgramRepository`): a routine is a one-day `program` row with `kind = routine`, kept out of the program list and the rotation; a new `auto` program is stored inactive while an `own` one is active; a Jelajahi card is built by the same generator and becomes the active program. The review (L-4) runs through B-06 `review` only for an `auto` program with auto-programming on. Figure assets (`figures/…`) are drawn by `ui/latihan/FigureViews.kt` (muscle map and animated rig; rig only where `exercises.json` has `rig`). New v1 column `program.kind`, JSON regenerated, no migration (Decisions.md).

Implementation (B-06, `app.xnkfit.domain.training`: `Library.kt`, `ProgramGenerator.kt`, `Progression.kt`). Deviations and readings, all inside this text; the numbers are Rules.md R_TRN_GEN and R_TRN_EQUIP:

- **Signature.** `generate(profile, equipment, goalFocus, history, library, todayEpochDay)`: the library and today's epoch day are passed in because the domain reads no files and has no clock. `profile` is `TrainingProfile(gate, experience, daysPerWeek, sessionMinutes)` (the three questions of the mockup's first training profile); `gate` is the `evaluateTargets` result, and anything but `Ready` returns `Blocked` (no program before screening, PR-5). Risk answers and under-18 light mode give level 1 whatever the experience (R_TRN_LEVEL1, R_GATE_U18).
- **Equipment.** Fixed settings assume the items of Content.md §2 (R_TRN_EQUIP); `custom` takes entries of any setting whose every item is ticked, plus floor work. `ExerciseEntity` (B-03/B-04) has no `settings` or `equipment` column yet, so the app cannot build the generator's `Exercise` list from Room today; the golden tests read `content/exercises.json` directly. B-07/B-08 add the two columns (or read the bundled file) before wiring.
- **Week.** Full body A/B/C (2–3 days) or upper/lower (4 days, level 2+ only); level 1 is held to 3 days. Each day is a template of patterns, most important first, cut to the exercise count for the chosen length (30/45/60 min → 4/5/6). Main = first library entry the level and equipment allow; accessory = the entry whose main muscles overlap least with it. Cardio and carries are never generated (they stay in Jelajahi and own routines).
- **Volume (step 3a).** A template already over 20 sets for a group is cut first (accessory sets, then accessories), then each group under its minimum gets sets inside R_TRN_SETS, then one accessory exercise (the one that adds least to groups already covered) on as many days as needed, never past 20 for any group. The minimum wins over the chosen length: a day can hold more exercises than the length suggests, and its `estMinutes` (sets × (rest + 45 s)) shows the real length. Groups still under are returned in `underMinimum` for the L-17 sentence: in `home-bw` the pull groups (no bar, band or table assumed), and in `home-db` on a 4-day split QUADS or HAMS, because every home-db squat and hinge entry has GLUTES as a main muscle and the GLUTES cap of 20 is reached first. 2-day level-2 weeks can leave CALVES under (one isolation pattern).
- **Missing patterns (L-3).** `missing` lists each pattern with no possible entry and the items that would open it (the entries needing the fewest extra items), e.g. `PULL_V needs band+bar` for home bodyweight. Text comes from strings in the UI.
- **Progression and return.** `sessionTargets(day, level, history, today, library)` gives the next session's sets, reps, load or band level with a `Reason` code (L-4; the UI writes the line). The return rule (R_TRN_RETURN ⚠ G9) is measured from the last session of any kind and replaces progression for that session; the UI shows no banner (L-16) and must not call the rule evidence-based. A harder variant (`variantOf`) is only proposed in the review, where the user accepts or declines it; it must fit the equipment and the level (a level-1 program never gets a level-2 entry).
- **Review.** `review(...)` returns `OwnRoutine`, `AutoOff`, `NotDue`, `Blocked` or `Proposals(changes, proposed)`; `applyChanges` applies only the accepted ones. A different program shape (for example a level change that turns 3 days into a split) is one `REPLACE_PROGRAM` change. `regenerateOrKeep` keeps an `own` or Jelajahi program untouched and keeps no auto program when the gates block (L-5, PR-5).
- **Open for B-07.** `CORE` holds (plank, side plank) carry the 10–15 range like other bodyweight work; the mockup shows "waktu tahan". The logger decides whether the range is seconds or reps for holds.

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

Implementation notes (B-10): the planner and its rules are pure Kotlin in `domain.food` (`Planner.kt`, `Shopping.kt`, `FoodLog.kt`); `data.food` holds the Room repository, FTS4 search, mappers, DataStore for the food preferences and shopping ticks (no schema change), and the target source; `ui.makan` holds the four screens. Deviations from this section: portions are not scaled to the target; "Favorit" is derived from the log; recipes have no builder screen yet. Details and the rows to review are in Decisions.md.

Implementation notes (B-11): `domain.today` holds the target card (all gates, R_GATE_ED without numbers, sources per number), the recalculation prompt, workout card, weekly activity and tasks; `ui/today` reads them through `TodayViewModel`. `TargetBasisStore` remembers what the target was calculated with; `RoomTargetSource` now reads it, so Makan and Hari ini share one target (this replaces B-10's rebuilt one). Deviations in Decisions.md.

## 8. Reminders and notifications

- Local notifications only (R-1). Channels: Latihan, Makan, Timbang, Istirahat (rest timer).
- Scheduling: `WorkManager` periodic check at 15 min intervals, plus exact alarms only for the rest timer. Reminders fire at the user's hour (R-2).
- Max three reminders per day; a reminder is skipped when its action is already done (workout logged today, food logged for the slot).
- Text: from `strings`, varied by a rotation index, short and neutral (R-2). The rotation never repeats the same text twice in a row.
- Permission asked at the moment of use (O-3).

Implementation notes (B-15): `domain.reminder.nextReminder` decides from plain facts (fake-clock tests); `data.reminder.ReminderWorker` reads the facts from Room and posts at most one notification per run on channel Latihan, Makan or Timbang ("kembali" shares Latihan). The job is one unique periodic work (15 min, the system minimum), started on app open and when a switch is turned on, cancelled when all switches are off; no boot receiver (WorkManager keeps its job across restarts). What was posted today and the "kembali" count live in DataStore `reminder_log`. Texts are `string-array` resources in `strings_reminder.xml`, index = `rotationIndex` (day + kind offset). Rules and deviations: Decisions.md.

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

Built in B-16 (`data/backup`): the zip holds the user tables only (bundled content, `food_fts`, `backup_meta`, `schema_meta` are left out; DataStore settings are not in it); a backup without photos leaves the phone's photos in place. Drive works only when the build carries the owner's OAuth client id (`-Pxnkfit.driveClientId`, empty by default → "belum tersedia"). Automatic backup on a phone that never backed up stops when Drive's newest backup is from another phone and the Data screen asks. See Decisions.md (B-16).

Delete all (B-7): removes the database, photos, DataStore and cached content, then restarts at onboarding.

## 10. Speed budget and performance

Targets from NF-1, measured on the reference phone. **Owner decision 2026-11-18 (S-05): Xiaomi Redmi 10** (Helio G88, 4 GB or 6 GB RAM, 6.5″ 1080×2400, launched on Android 11, updatable to Android 13). Source: https://m.gsmarena.com/xiaomi_redmi_10-11060.php (listing search, 2026-11-18).
Gap against the first plan (2–3 GB, Android 8–9), accepted by the owner: this phone is stronger than the target. To keep the "fast on 2–3 GB" promise (PRD G6) the speed check also runs (a) with the CPU throttled 4× on this phone and (b) on an Android 8 (API 26) emulator limited to 2 GB RAM. Budgets below must pass on both. The first candidate (Redmi 9A, 2 GB) is dropped.

| Measure | Budget | How it is checked |
| --- | --- | --- |
| Cold start to usable Hari ini | 2.0 s | Macrobenchmark, CPU throttled 4× (`StartupBenchmark`, `timeToFullDisplayMs` median; Hari ini reports fully drawn when loaded) |
| Tab switch | 100 ms | Macrobenchmark frame timing (`TabSwitchBenchmark`, `frameDurationCpuMs` P99) |
| Set tick saved to disk | 50 ms | Unit timing on the DAO path (`SpeedBudgetTest`, JVM, every PR) |
| Food search per keystroke | 100 ms | Benchmark on the full TKPI table (`SpeedBudgetTest`, JVM, every PR) |
| Installed size | 20 MB | APK size check in CI |

How each is run and the measured numbers: docs/Speed.md (B-19).

Rules:

- Baseline profiles committed for the cold-start and tab paths. **Not yet (B-19):** the generator exists (`benchmark/`), the file needs an API 33+ device or emulator with KVM; no profile is written by hand (Speed.md §5).
- Food search uses a prebuilt FTS4 table, not a LIKE scan.
- No image loads on the first frame; exercise figures are vector drawings (see Content.md) and cache as `ImageBitmap` on demand.
- Charts draw on `Canvas`, one pass per series.
- CI fails the build when a benchmark exceeds its budget (NF-1). **Deviation (B-19):** CI has no device, so CI gates the JVM-measurable budgets (set tick, food search, APK size); cold start and tab switch run by hand on a device and `tools/benchmark-budget.mjs` fails on their results. The unrooted Redmi 10 cannot be throttled: its run is held to budget ÷ 4 ⚠ (Decisions.md).

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
| AndroidX Activity Compose (`activity-compose`) | `ComponentActivity.setContent`, the entry point of every Compose app (added in B-01) | Small |
| Room | Database (§3) | Small |
| DataStore | Settings | Small |
| WorkManager | Reminders, backup on open | Small |
| `androidx.health.connect:connect-client` 1.1.0 | Steps (L-15): `READ_STEPS` only, `aggregateGroupByPeriod` of `StepsRecord.COUNT_TOTAL`. Added in B-09 | Small; the card hides itself where Health Connect is missing |
| `com.google.android.gms:play-services-auth` (AuthorizationClient, scope `drive.appdata` only) + Drive REST over the platform `HttpURLConnection` | Backup (B-1). Added in B-16. **Deviation:** no Ktor/OkHttp: the content update (B-04) already uses `HttpURLConnection`, so that is the one HTTP client | play-services-auth pulls play-services-base/basement/tasks; R8 shrinks; APK checked against the 20 MB budget |
| kotlinx.serialization | Backup JSON | Small |
| JUnit, Robolectric, Compose UI test, Macrobenchmark | Tests (NF-5) | Test only |
| `androidx.benchmark:benchmark-macro-junit4` 1.5.0 + Gradle plugin `androidx.baselineprofile` 1.5.0, `androidx.test.ext:junit` 1.3.0, `androidx.test.uiautomator:uiautomator` 2.4.1 | Macrobenchmark and baseline profile generator in `:benchmark` (B-19) | Test module only, not in the APK |
| `androidx.profileinstaller:profileinstaller` 1.4.1 | Applies the baseline profile on sideloaded installs and API 26–27, and lets Macrobenchmark compile with it (B-19) | Small (one receiver, no permission) |
| `androidx.room:room-testing` | `MigrationTestHelper` for the migration tests (§13), same version as Room (added in B-03) | Test only |
| `androidx.test:core` | `ApplicationProvider` for Robolectric database tests (B-03) | Test only |

Anything else needs a row here first.

## 13. Tests and quality gates (NF-5)

| Gate | Tool | Runs on |
| --- | --- | --- |
| Domain rules | JUnit, pure JVM; every rule ID has at least one test | Every PR |
| Generator | JUnit with golden programs per equipment setting | Every PR |
| Migrations | Room migration tests from each exported schema (`MigrationTestHelper`, Robolectric; JSON in `app/schemas/`, committed, never edited after release). `MigrationTest.everyReleasedVersionMigratesToLatest` finds every `N.json` by itself. Today only v1 exists: the test runs v1 → v1, and one example migration is kept as an ignored pattern. CI fails when a build changes a committed schema file (see CI). | Every PR |
| Backup round trip | Unit: export → import equals source per table, refusals before any write, rollback on a failed write (`BackupArchiveTest`); Drive upload, keep-three, device check against a fake Drive server on 127.0.0.1 (`DriveBackupTest`) | Every PR |
| Kill test | Instrumented: kill at each logger step | Nightly |
| Screen tests | Compose UI tests, phone and tablet, light and dark | Every PR. **Not in place yet (B-05):** `compose-ui-test` is not in the catalog and no Compose test has run; screens are covered by ViewModel tests on the JVM (`OnboardingViewModelTest`, `ScreeningModelTest`) |
| Speed | JVM: `SpeedBudgetTest` (set tick, food search on the full table). Device: Macrobenchmark (`:benchmark`) checked by `tools/benchmark-budget.mjs`, see Speed.md | JVM every PR; device before release (no device in CI) |
| Text literals | `tools/check_text_literals.py`: no string literal with letters in any `ui` package; escape only with `// text-ok: <reason>`. Has its own tests. **Deviation from "lint rule" (B-01):** a source scan, not an Android Lint check, because a custom Lint check needs the lint-api library and its own module; the scan is 80 lines, tested, and runs the same place. Move to real Lint if it produces false hits. | Every PR |
| APK size | `tools/check_apk_size.sh`, budget 20 MB (§10). Release APK file size stands in for installed size until B-19 measures on a device. | Every PR |
| Permissions | `tools/check_permissions.sh`: the built APK may declare only the §14 list plus the WorkManager and AndroidX extras named there | Every PR |
| Android Lint | `lintRelease`, warnings are errors. The "newer version available" checks and `OldTargetApi` are off: they go stale, and would turn CI red with no code change (first CI run failed on `OldTargetApi` because the runner had a newer platform than our target 36). Target SDK is raised on purpose, per §1 | Every PR |

Rule tests: each number in Rules.md is asserted in a test that names its rule ID. A rule with ⚠ in Rules.md has a test marked `@Ignore("unverified")` until closed. After S-07 (2026-11-18) the ⚠ rules are R_HH (G7), bought and regional meal numbers (G8), R_TRN_RETURN (G9) and R_LEVEL (tuning); every other rule test runs. R_ACT, R_FIBRE, R_TRN_SETS and R_TRN_REPS assert the values changed in S-07. `RulesCoverageTest` (B-02) reads Rules.md and the test sources and fails when a rule ID has no test whose name starts with the ID, when a ⚠ rule has a running test or a ✅/👤 rule has none, when a `domain` constant is not named after a rule ID, or when `domain` code writes a number outside a named constant.

## 14. Security and privacy in code

- No network call except: Drive (user-chosen), content update (`GET` of static files with checksum), Health Connect (local). The app has no backend to call.
- Content update (PV-2): downloads a manifest and files over HTTPS from the project's static host (GitHub Pages, [Content-Update.md](Content-Update.md)); checksum and manifest signature verified; no request body; no user data.
- Google sign-in tokens live in the system account store, not in app files.
- Photos are excluded from backup unless B-3 is on; their rows set `hiddenFromBackup`.
- Share cards (B-13) are drawn on the phone and handed to the system share sheet as one PNG in `cache/share/` through the FileProvider; no storage permission, no upload. The face mosaic uses the framework `FaceDetector` and pixelates the whole photo when it finds no face.
- Release builds strip `Log` calls with R8 so no user value reaches logcat.
- `android:allowBackup="false"`: Android's own cloud backup must not copy the database outside the user's Drive choice.
- The manifest asks only for: notifications, camera (photos), Health Connect read (steps), internet, exact alarm (rest timer only). Nothing else, with these exceptions the build adds and that were checked in B-01: `WAKE_LOCK` and `ACCESS_NETWORK_STATE` from WorkManager (needed to run work on Wi-Fi only, B-2; no prompt, normal permissions), and `app.xnkfit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` from AndroidX (the app's own signature permission, grants nothing to others). WorkManager's `FOREGROUND_SERVICE` and `RECEIVE_BOOT_COMPLETED` are removed in the manifest because we use no foreground service and API 26+ keeps JobScheduler work across reboot. `<uses-feature camera required=false>` is not a permission; it keeps phones without a camera installable.

## 15. Accessibility

- Content descriptions on every icon button.
- Touch 48 dp (B-05 raised it from 44; `MinTouch` in `ui/common`); focus order follows reading order.
- Colour is never the only signal (muscle map has labels, planner has text).
- Big text up to 200 % (§11).

## 16. Open design questions

| # | Question | Default until decided |
| --- | --- | --- |
| 1 | Exact reference phone model | Decided in S-05 (owner, 2026-11-18): Redmi 10, plus a 2 GB API 26 emulator run (§10) |
| 2 | Content update host | Decided in S-06 (owner left it to Claude, 2026-11-18): GitHub Pages of a project content repo, per-file SHA-256 and a signed manifest; details in [Content-Update.md](Content-Update.md) |
| 3 | Whether the figure is drawn with Compose Canvas or a static SVG asset | Decided in D-06 (2026-11-17): reuse the XNK app's figures, the muscle map (`figure.min.json`, react-muscle-highlighter shapes, MIT, credits kept) and the animated exercise rig (`rig.min.json`, XNK's own work); new rigs only for lifts XNK lacks. |
