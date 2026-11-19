# TODO — XNK Fit

| | |
| --- | --- |
| Status | Draft, 2026-11-16 |
| Process | Docs → clickable mockups (owner approves) → code. No app code before D-05 is approved. |
| Working rule | One task per session. Take the lowest-numbered open task whose dependencies are done. Mark `[~]` when you start, `[x]` when done (same commit). |
| Model column | Recommended model for that task's session. Opus 5.5 for design and rules judgement, Sonnet 5.5 for code and normal writing, Haiku 5.5 for mechanical or repetitive work. |
| Orchestrator | Owner. Each row becomes its own session with the model shown. |

Status keys: `[ ]` open · `[~]` in progress · `[x]` done · `[-]` deferred or dropped.

## Phase D — Docs and mockups

| ID | Task | Depends | Model | Status |
| --- | --- | --- | --- | --- |
| D-01 | PRD (`docs/PRD.md`) | — | Opus 5.5 | [x] (commit on `claude/d-01-prd`; push blocked, see below) |
| D-02 | Design (`docs/Design.md`) | D-01 | Opus 5.5 | [x] (first draft, owner review pending) |
| D-03 | Rules (`docs/Rules.md`) with every number sourced or ⚠ | D-01 | Opus 5.5 | [x] (first draft; sources ⚠ to close, see S-07) |
| D-04 | Content (`docs/Content.md`): exercise split, patterns, food sources, badges | D-01, D-03 | Sonnet 5.5 | [x] (first draft; counts are targets) |
| D-05 | Clickable mockups for every screen in PRD §5/Design §5, phone and tablet, light and dark, sent to the owner for approval | D-02, D-04 | Opus 5.5 | [x] (`docs/mockups/`, 15 screens; screen list for D-06 in `docs/mockups/README.md`) |
| D-06 | Owner approval of D-05 (decision, not code) | D-05 | — (owner) | [x] (2026-11-17: all 15 screens approved; English cues, reuse XNK figures, logger mode chosen with no preselect, screening text final in S-07) |

## Phase S — Setup and checks (outside the code)

Some items are the owner's to do (accounts, forms). Claude prepares the text and checklists only.

| ID | Task | Depends | Model | Status |
| --- | --- | --- | --- | --- |
| S-01 | Name checks: store search (Play, App Store), PDKI trademark (classes 9, 41, 44), domain xnkfit.com (PRD §12). Write results in PRD §12 | — | Sonnet 5.5 | [x] Play, App Store, domain checked 2026-11-17. PDKI could not be checked by tool; owner states on 2026-11-18 that the name "XNK Fit" is safe (owner's statement, not a register check) |
| S-02 | Play target SDK rule at release time; record in Design §1. Remaining: official date for next raise (API 37) not published as of 2026-11-17; re-check before release | D-02 | Sonnet 5.5 | [~] |
| S-03 | Google OAuth verification for the Drive app folder: checklist and timing (PRD §14 item 4) | D-02 | Sonnet 5.5 | [x] (`docs/Google-OAuth.md`: `drive.appdata` is non-sensitive, no review expected; owner does the console steps; three ⚠ items inside) |
| S-04 | Play Health Connect and health-apps declarations: checklist and timing (PRD §14 item 4) | D-02 | Sonnet 5.5 | [x] (`docs/Play-Health.md`: form is required for every app; steps only; draft answers; review time not published, three ⚠) |
| S-05 | Pick the reference low-end phone (2–3 GB RAM, Android 8–9), record it in Design §10 | D-02 | Haiku 5.5 | [x] Owner chose Redmi 10 (2026-11-18). It is stronger than the plan (4 GB+, Android 11), so Design §10 adds a 2 GB API 26 emulator run |
| S-06 | Content update host choice (Design §16 item 2) | D-02 | Sonnet 5.5 | [x] (`docs/Content-Update.md`: GitHub Pages, signed manifest; owner left the choice to Claude 2026-11-18: repo `xnk-fit-content`, no mirror on day one; Cloudflare bandwidth terms ⚠) |
| S-07 | Close ⚠ sources: read each source in Rules §13 and write the exact figure and page; then fix G1–G8 in Rules §10 | D-03 | Opus 5.5 | [x] 2026-11-18: G1–G6 closed with figures and pages (Rules §13); owner decisions Rules §12 #4–#8 applied. Handed on with written reasons: G7 → C-05, G8 → C-07, G9 (R_TRN_RETURN) stays an owner rule without a source |
| S-08 | Play developer account: owner creates it (not Claude). Claude only writes the checklist | — | — (owner) | [ ] Owner does it last (2026-11-18), before B-20 / R-01 |

## Phase C — Content (may run in parallel with D-05)

| ID | Task | Depends | Model | Status |
| --- | --- | --- | --- | --- |
| C-01 | Exercise library file: about 150 entries, IDs, patterns, muscles, cues (English), hints (Indonesian), sources per entry | D-04 | Sonnet 5.5 (draft), Opus 5.5 (review of muscles) | [x] (149 entries in `content/exercises.json`, checker passes. Muscle review by Opus done 2026-11-18, Content §12: all 149 reviewed; sources per entry: 57 verified, 65 analog, 27 praktik umum ⚠; 21 entries had muscles changed; 34 still ⚠ uncertain with reasons; owner decided `LEGISO_ADD` keeps QUADS and the pullover stays `PULL_V`) |
| C-02 | Movement pattern check: each pattern has ≥ 3 choices per setting (Content §2) | C-01 | Haiku 5.5 | [x] (`node tools/check-exercises.mjs` passes 2026-11-18: every pattern has ≥ 3 choices in each required setting; lowest counts are 3. Needs table = Content §3. B-01 can wire the script into CI) |
| C-03 | Food table from TKPI 2020 (about 1,100 rows) with parser and checks | D-04 | Sonnet 5.5 | [x] (1,143 rows, 3 on the hand-check list, 92 rows flagged for source inconsistencies; `name_en`, household, halal and allergens left empty, see Content §4.2) |
| C-04 | Warung and bought-meal estimates, each flagged | D-04 | Sonnet 5.5 | [x] |
| C-05 | Household measures: PMK 41/2014 first set; cooked dish and centong weights as flagged estimates | C-03 | Opus 5.5 | [ ] |
| C-06 | Packaged food label values (about 80) | C-03 | Haiku 5.5 (data entry) | [x] (2 rows, both third-party ⚠; about 80 not reached, 13 failed products in Content §4.3; no producer label found) |
| C-07 | Bought and regional meal numbers (Content §8, C-07) | C-04 | Sonnet 5.5 | [~] 108 flagged estimate rows in `content/foods-regional.json` + `tools/check-foods-regional.mjs` (passes); open: official `bought` rows (no gerai site read), TKPI codes instead of the generic ingredient table, sodium |
| C-08 | Exercise figures: reuse the XNK muscle map and 21 rig patterns (D-06); draw sourced rigs only for lifts XNK lacks | C-01, D-06 | Opus 5.5 | [x] 2026-11-18: `content/figures/` (XNK files from commit `6dc57b3` in `xnk-source/`, unchanged, credits kept, sha256 locked); `rig` field in `exercises.json`: 20 exercises with an XNK rig, 129 `null` with a written reason in `no-rig.json`; all 149 show the muscle map; `node tools/check-figures.mjs` passes (Content §5.1, §5.2). Not done: no new rig drawn (no sourced joint angles yet; XNK repo, incl. `Gerak.md`, could not be opened here) → C-08b |
| C-08b | Rest of the figures: read XNK `Gerak.md` for the 20 reused rigs; draw sourced rigs for 67 `derive` + 55 `new` exercises (`content/figures/no-rig.json`); owner confirms 7 cardio `map-only` and the seated `PUSHV_DB` rig; split delts and obliques in the XNK figure build (Content §5.2) | C-08, XNK repo access | Opus 5.5 | [ ] |
| C-09 | Owner review of English cues | C-01 | — (owner) | [ ] |
| C-10 | Badge table: 18 codes, titles, conditions, messages | D-04 | Sonnet 5.5 | [x] (`content/badges.json`, `tools/check-badges.mjs` passes; Content §6) |
| C-11 | Meal packages (Makan): three options per slot, regions, bought, anak kos, own | C-03, C-04 | Sonnet 5.5 | [x] (`content/meal-packages.json`: 72 packages, 6 profiles, own hook; `tools/check-meal-packages.mjs` passes; Content §4.5; budget tiers ⚠ unsourced; row needs listed there) |

## Phase B — Build (after D-06 approval)

Code tasks. Each one ends with its tests passing (Design §13).

| ID | Task | Depends | Model | Status |
| --- | --- | --- | --- | --- |
| B-01 | Repo skeleton: Gradle, Compose, Room, DataStore, WorkManager, R8, CI (unit tests, lint, text-literal rule, APK size check) | D-06 | Sonnet 5.5 | [x] (verified in sandbox: `testDebugUnitTest`, `lintRelease`, `assembleRelease` green; size and permission checks pass; CI green on GitHub (run 44420990967, all steps: text-literal rule and tests, unit tests, lint, release build with R8, permission check, APK size check); first run failed in Lint on `OldTargetApi` (runner SDK newer than target 36), check turned off and recorded in Design §13; concurrency changed so pushes from parallel sessions stop cancelling each other's runs; not run on a device or emulator, no emulator in sandbox) |
| B-02 | Domain: targets, BMR/TDEE, safety gates, pace check; every Rules ID has a test | B-01, S-07 | Opus 5.5 | [x] (2026-11-18: `domain.rules`, `domain.training`, `domain.food`; all 87 Rules IDs have a test, checked by `RulesCoverageTest` together with "no number outside a named constant"; 125 tests, 4 `@Ignore("unverified")`: R_HH (G7), G8, R_TRN_RETURN (G9), R_LEVEL; golden target profiles. Rules.md: 7 IDs added for numbers that had none, readings written down; age gates use the youngest possible age from the birth year (safe side, Rules §12 #9, Decisions.md); R_PTS_TARGET code follows the 5-logged-days rule. `testDebugUnitTest`, `lintRelease`, `assembleRelease` green in sandbox; not run on GitHub CI yet) |
| B-03 | Room schema v1 and migration test harness; export schema JSON | B-01, D-02 | Sonnet 5.5 | [x] (`data/db`, `app/schemas/.../1.json`; 28 tests green, 1 ignored example migration; JVM/Robolectric only, nothing run on a device; see Design §3) |
| B-04 | Content loader and checksummed content update (PV-2) | B-03, C-01, C-03 | Sonnet 5.5 | [ ] |
| B-05 | Onboarding and screening (O-1…O-6, S-1…S-6), under-13 stop | B-02, D-06 | Sonnet 5.5 | [x] (2026-11-19: `ui/onboarding`, `ui/screening`; 28 JVM tests for flow, gates, input checks, back button, under-13 wipe, no program before the form; text-literal rule clean. No Compose UI tests and nothing run on a device or emulator; see Design §13 and Decisions.md. Permissions: notifications and camera asked for real, Health Connect and Drive rows wait for B-14/B-16. "Pulihkan cadangan" disabled until B-16) |
| B-06 | Program generator and progression (Design §6; L-1…L-5, L-12, L-16) with golden tests | B-02, B-04, C-02 | Opus 5.5 | [ ] |
| B-07 | Logger, Dipandu and Bebas, crash safety, rest timer with exact alarm (L-6…L-8, NF-2) | B-03, B-06 | Opus 5.5 | [ ] |
| B-08 | Latihan screen, own routines, Jelajahi, muscle map, activity log (L-9, L-11, L-13, L-14) | B-07 | Sonnet 5.5 | [ ] |
| B-09 | Health Connect steps, hidden when missing (L-15) | B-08 | Sonnet 5.5 | [ ] |
| B-10 | Food log, meal options, week planner, shopping list, household measures (M-1…M-10) | B-04, C-11 | Opus 5.5 (planner rules), Sonnet 5.5 (rest) | [ ] |
| B-11 | Hari ini screen and targets card, recalculation prompt (H-1…H-3, M-2) | B-02, B-10 | Sonnet 5.5 | [ ] |
| B-12 | Saya: progress, photos on phone, journey, level, streak, badges (P-1…P-5) | B-03, C-10 | Sonnet 5.5 | [ ] |
| B-13 | Share cards and Kartu saya, editor, QR on, no-kg switch (P-6) | B-12 | Sonnet 5.5 | [ ] |
| B-14 | Settings, Bantuan, welcome cards, glossary, privacy (P-7, P-8, PV-4) | B-11 | Sonnet 5.5 | [ ] |
| B-15 | Reminders: local notifications, rotation text, three-per-day cap (R-1, R-2) | B-08, B-10 | Sonnet 5.5 | [ ] |
| B-16 | Backup and restore: zip format, Drive appdata, last three, warnings, device check (B-1…B-7) | B-03 | Opus 5.5 | [ ] |
| B-17 | Tablet layout: rail and two columns (N-4) and 360–430 dp checks (D-2) | B-08…B-14 | Sonnet 5.5 | [ ] |
| B-18 | Kill and crash tests (NF-2), screen tests phone and tablet, light and dark (NF-5) | B-07, B-17 | Sonnet 5.5 | [ ] |
| B-19 | Speed budget: Macrobenchmark, baseline profiles, CI gate (NF-1) | B-17 | Opus 5.5 | [ ] |
| B-20 | Play closed test prep: data safety form, privacy policy text, store listing (PV-4, S-08) | B-18 | Sonnet 5.5 | [ ] |

## Phase R — Release

| ID | Task | Depends | Model | Status |
| --- | --- | --- | --- | --- |
| R-01 | Closed test: 12 testers for 14 days (owner runs it) | B-20 | — (owner) | [ ] |
| R-02 | Release checklist (PRD §13) and production submission | R-01 | Opus 5.5 | [ ] |

## Owner notes

- Git: `claude/d-01-prd` holds the D-01 commit locally. The branch is now on GitHub (checked 2026-11-18).
- Working tree of this repo lives at `C:\Users\erlan\xnk-fit`.
- The XNK coaching repo's plans (exercise figures, program rules) are separate; this repo does not depend on them.

## Changes log

| Date | Change |
| --- | --- |
| 2026-11-18 | B-03 done: Room schema v1, DAOs, FTS4 food search, migration harness, schema JSON committed. |
| 2026-11-16 | First TODO (D-05 onward planned; D-01…D-04 drafted). |
| 2026-11-18 | B-02 done: domain rules, gates, pace check, rule tests and coverage check. |
| 2026-11-18 | C-08 done: XNK figures reused, 20 rigs mapped, 129 reasons written, figure checker; C-08b added for the rest. |
| 2026-11-18 | C-01 done: exercise library draft, checker. |
| 2026-11-18 | C-02 done: pattern check passes on the 149 entries. |
| 2026-11-18 | C-01 muscle review (Opus) done: sources on all 149 entries, 21 muscle changes, 35 entries still ⚠ (Content §12). |
| 2026-11-18 | Autonomy rule for sessions: owner is away; decisions taken by Claude are listed in `docs/Decisions.md` for the owner to reverse. `R_PTS_TARGET` now counts weeks with food logged on 5+ days (matches `LOGWEEK_1`). |
| 2026-11-18 | C-10 owner decisions: `TARGET_WEEK` replaced by `LOGWEEK_1` (a week with food logged on 5+ days, no calorie number); personal record definition accepted as written (Content §6). |
| 2026-11-18 | Owner decisions: S-05 Redmi 10, S-06 left to Claude, S-01 name accepted, S-08 last. Sessions started for B-01 and C-01. |
| 2026-11-18 | S-07 done: R_ACT set to FAO/WHO/UNU PAL values (owner), G1 closed; mockup Bantuan example updated. |
| 2026-11-18 | Owner decisions on Rules §12 #5–#8 recorded; R_TRN_VOL and R_TRN_PROG_STEP added; PRD S-3 and new L-17; Design §6. |
| 2026-11-18 | S-07 partly done: Rules §2/§3/§5 sources checked, §10 G1–G9 updated, §13 has exact figures and pages; G1, G7, G8, G9 still ⚠; three value changes for the owner. |
| 2026-11-18 | C-06 done: `content/foods-packaged.json` (2 third-party ⚠ rows), checker, 13 failed products in Content §4.3. |
| 2026-11-18 | S-06 done: Content-Update.md. |
| 2026-11-18 | S-05 proposed (Redmi 9A), owner to confirm. |
| 2026-11-18 | S-04 done: Play-Health.md. |
| 2026-11-18 | S-03 done: Google-OAuth.md; PRD §14 item 4 corrected. |
| 2026-11-17 | D-06 approved by the owner; decisions recorded in PRD L-6 and §14, Design §16, Content §5, mockups README. |
| 2026-11-16 | D-05 mockups in `docs/mockups/` (static HTML, phone and tablet, light and dark); D-06 approval list in its README. Local commit only; push still blocked. |
