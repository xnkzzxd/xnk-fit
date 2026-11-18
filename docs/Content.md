# Content — XNK Fit

| | |
| --- | --- |
| Status | Draft, 2026-11-16 (task D-04) |
| Reads | [PRD.md](PRD.md) §7.4 (L-10), §7.5 (M-7…M-9), §7.6 (P-5). Numbers and sources: [Rules.md](Rules.md). |
| Scope | What content ships in the app: exercises and their figures, movement patterns, food data, meal packages, activity list, badges, reminder text sources, and how content updates work. |
| Language | Exercise names, muscles and cues in English (owner decision, PRD L-10). Food, meal and UI text in Indonesian (PRD §2). Every text lives in one strings or content file (NF-3). |

## 1. Shipping model

- **Bundled** with the app: exercises, patterns, the TKPI food table, bundled meal packages, badges, activity list, glossary, welcome cards.
- **Updatable** (PV-2): a static content file set, checksummed. The app downloads a manifest, verifies SHA-256 for each file, and swaps the content in one transaction. Nothing user-specific is sent. Host, signing and check schedule: [Content-Update.md](Content-Update.md).
- Each content file has a `version` and `schema` number. The app refuses a content file with a newer `schema` than it understands and keeps the bundled copy.
- Content IDs are stable forever (for example `SQUAT_GOB`). A removed exercise is marked `active = false`, never deleted, so old workouts still resolve (Design §3).

## 2. Equipment settings and exercise split (L-1, L-10)

| Setting | Code | Who | Equipment assumed |
| --- | --- | --- | --- |
| Gym | `gym` | Full gym members | Machines, barbell, cable, dumbbells, bench |
| Home bodyweight | `home-bw` | No equipment | Floor, wall, stairs, chair |
| Home dumbbell / band | `home-db` | Some equipment at home | Dumbbells, resistance bands, a pull-up bar optional |
| Tick your own | `custom` | Anyone | Whatever the user ticks in the equipment list |

Library target: **about 150 curated exercises** (PRD L-10). Built in C-01 (2026-11-18): **149 entries** in [`content/exercises.json`](../content/exercises.json), checked by `node tools/check-exercises.mjs`.

| Group | Target | Built | Covers |
| --- | --- | --- | --- |
| Gym | about 60 | 60 | Machines, barbell, cable, dumbbell (heavy) |
| Dumbbell / band | about 40 | 40 | Dumbbell, band, kettlebell-light |
| Bodyweight | about 35 | 34 | Bodyweight, each with an easier and a harder variant |
| Cardio and core | about 15 | 15 | Treadmill, bike, rower, planks, carries, dead bug |

Every pattern (§3) has **at least three choices in each setting** that can carry it (L-10). A pattern with fewer than three in a setting is a content bug, tested in CI (`tools/check-exercises.mjs`). `CURL` and `CARRY` are required in `gym` and `home-db` only, `LEG_ISO` in `gym` only (§3). In `home-bw`, `PULL_V` (all three need a bar) and `PULL_H` (needs a table or door) count entries whose `equipment` the user may lack; the generator still applies L-3.

## 3. Movement patterns

Each exercise has one `pattern` and a list of main and helper muscles (for the muscle map, L-11). Patterns:

| Pattern code | Plain name (ID) | Needs (settings) | Example exercises (EN) |
| --- | --- | --- | --- |
| `SQUAT` | Jongkok | all four | Back Squat, Goblet Squat, Bodyweight Squat, Bulgarian Split Squat |
| `HINGE` | Engsel pinggul | gym, db, bw (single leg) | Romanian Deadlift, Hip Thrust, Glute Bridge, Single-Leg Deadlift |
| `PUSH_H` | Dorong horizontal | gym, db, bw | Bench Press, Dumbbell Press, Push-Up, Incline Push-Up |
| `PUSH_V` | Dorong vertikal | gym, db, bw | Shoulder Press, Pike Push-Up |
| `PULL_H` | Tarik horizontal | gym, db (band / row), bw (inverted row, needs table) | Cable Row, Dumbbell Row, Band Row, Inverted Row |
| `PULL_V` | Tarik vertikal | gym, db (band), bw (pull-up needs bar, L-3) | Lat Pulldown, Band Pulldown, Pull-Up |
| `CURL` | Tekuk siku | gym, db, band | Dumbbell Curl, Cable Curl, Band Curl |
| `TRI` | Trisep | gym, db, bw | Triceps Pushdown, Overhead Triceps, Bench Dip |
| `LEG_ISO` | Isolasi kaki | gym | Leg Extension, Leg Curl, Calf Raise |
| `CORE` | Inti / perut | all four | Plank, Dead Bug, Hollow Hold, Crunch, Pallof Press (band) |
| `CARRY` | Angkat jalan | gym, db | Farmer Carry, Suitcase Carry |
| `CARDIO` | Kardio | all four | Treadmill, Stationary Bike, Rower, Jump Rope, Brisk Walk |

When a pattern has no valid exercise for the user's setting, the app says the sentence from L-3 (for example "Butuh palang atau karet untuk gerakan tarik") and does not substitute another pattern.

Every exercise also has: `level` (1–3), `variantOf` (easier variant link), `cues_en` (3 short lines, English), `hint_id` (one Indonesian line, "Kenapa gerakan ini"), `equipment` codes, `muscles` (main, helper; from the muscle table in §5).

## 4. Food data (M-6, M-7, M-9)

| Source code | Content | Count (target) | Flag |
| --- | --- | --- | --- |
| `tkpi` | TKPI 2020 (Tabel Komposisi Pangan Indonesia), raw foods and cooked items as listed | about 1,100 | `isEstimate = false` |
| `packaged` | Packaged foods by name (brand-neutral names, e.g. "Mie instan, kuah, 1 bungkus") with label values | start with about 80 | `isEstimate = false` only when label-verified |
| `warung` | Common warung dishes (nasi campur, soto, gado-gado, padang set) with estimated portions | about 50 | `isEstimate = true` |
| `bought` | Bought meals for "makan di luar" (warteg, padang, bakso, nasi goreng) | about 30 | `isEstimate = true` |
| `own` | User's foods and recipes | unlimited | user-entered |

### 4.1 Warung and bought-meal estimates (C-04)

File: `content/foods-estimates.json`, checked by `node tools/check-foods-estimates.mjs`.

- **66 rows**, all `source = warung-est`, `isEstimate = true`, halal, shown as "perkiraan" (R_EST). Warung dishes and bought meals share one file; the bought-meal set is widened in C-07.
- **Method:** each row lists its ingredients with grams (`recipe`) and one common portion (`portion`, also copied to `household` as code `porsi`, source `estimate`). Macros per portion are estimated from those ingredients and the common recipe; kcal = 4/4/9 of the macros, rounded to 5. Per-100 g values come from the portion. Each row carries a spread (`spreadPct`, 15–50 %) and a `kcalRange`. The TKPI 2020 table is not in the repo yet (C-03), so the ingredient values are **not yet computed from TKPI**; that recalculation is a later step.
- **No label or official-site values are used.** No brand numbers are in the file.
- **Sodium is `null` in every row**: no basis yet. Filled when computed from TKPI or a label.
- **⚠ rows** (wide range, no firm basis): `W_GUDEG_NASI`, `W_MARTABAK_MANIS`, `W_ES_TEH_MANIS`, `W_TEH_MANIS_HANGAT`, `W_ES_JERUK`, `W_ES_KOPI_SUSU`, `W_BOBA_MILK_TEA`, `W_ES_CAMPUR`, `W_JUS_ALPUKAT`, `W_ROTI_BAKAR`. Cause: sugar, milk, margarine or topping amounts differ by seller.
- Rows without ⚠ are still estimates; their spread (15–35 %) is in the row.

Halal by default (R_HALAL): no pork or alcohol in any bundled item. Each food row carries `allergens` (egg, seafood, nuts, milk) and `lactose` (bool) for the filters (R_LIMIT_ALLERGY).

Household measures (M-6): each food may have a list `household = [{code, label, grams, source}]`. Example codes: `centong` (cooking spoon), `piring`, `potong`, `sdm`, `sdt`, `gelas`, `butir`, `buah`, `iris`. PMK 41/2014 (Rules S17) gives a first set; cooked dishes and centong weights are **our own estimates**, flagged `±` and listed in §8.

Each food row: name_id, name_en, kcal per 100 g, protein, carbohydrate, fat, fibre, sodium (mg), household list, source code, `isEstimate`.

## 5. Muscles and figure

Muscle groups (16 for the muscle map, L-11):

| Code | Name (ID) | Code | Name (ID) |
| --- | --- | --- | --- |
| `CHEST` | Dada | `QUADS` | Paha depan |
| `FRONT_DELT` | Bahu depan | `HAMS` | Paha belakang |
| `SIDE_DELT` | Bahu samping | `GLUTES` | Bokong |
| `REAR_DELT` | Bahu belakang | `CALVES` | Betis |
| `BICEPS` | Biseps | `ABS` | Perut |
| `TRICEPS` | Trisep | `OBLIQUES` | Samping perut |
| `LATS` | Punggung lebar | `LOWER_BACK` | Punggung bawah |
| `TRAPS` | Trapezius | `FOREARM` | Lengan bawah |

Figure: our own vector drawing, male and female, front and back (Design §11). Each exercise shows one or two poses at a time, with joint angles per frame. **Joint angles come only from a written source** (for example a standard exercise description with a range of motion, or EMG-based muscle activation studies for main and helper muscles, cited per exercise in Content.md §9). No copied artwork from other apps. Monochrome; the main muscle takes the accent, helper a tint.

Decision D-06 (2026-11-17): start from the XNK app's muscle map (`figure.min.json`, react-muscle-highlighter shapes, MIT, keep its credits) and exercise rig (`rig.min.json`, 21 patterns, XNK's own work); draw new rigs only for lifts XNK lacks. Figure work is the largest content job (PRD §14 item 2): about 150 exercises, but only the pattern set (about 25 base poses) is drawn; variants reuse a base pose with a changed angle table.

### 5.1 Figure files and rig mapping (C-08, 2026-11-18)

Files in `content/figures/`, checked by `node tools/check-figures.mjs`:

| File | What | Origin |
| --- | --- | --- |
| `figure.min.json` | Muscle map, male and female, front and back | XNK `apps-script/pt-scheduler/figure/figure.min.json` (built by `tools/build-figure.js`), shapes from react-muscle-highlighter 1.2.0, MIT |
| `rig.min.json` | 21 animated rig patterns, 13 frames each | XNK `apps-script/pt-scheduler/figure/rig.min.json` (built by `tools/build-rig.js`), XNK's own work, joint angles from XNK `docs/program/Gerak.md` |
| `CREDITS.md` | MIT licence text and XNK credits, unchanged copy of `docs/mockups/FIGURE-CREDITS.md` | — |
| `source.json` | Origin, XNK path and sha256 of both build files; the checker fails if either file is edited | — |
| `muscle-map.json` | The 16 muscle codes → figure regions (§5.2) | — |
| `no-rig.json` | Every exercise without a rig, with its reason | — |

⚠ **XNK commit not recorded.** This session could not open the XNK repo (access denied), so both files were taken from the D-05 mockup copies (`docs/mockups/figure-data.js`, commit dd20ca1; `rig-data.js`, commit 4754def), the JSON unchanged. `source.json` has `xnkCommit: null`; a session with XNK access compares the sha256 values and fills it. XNK `Gerak.md` (the joint-angle sources of the 21 rigs) was not read for the same reason.

**Mapping rule.** `exercises.json` has a new field `rig`: an XNK pattern name only when the rig shows the **same lift with the same setup**, otherwise `null`. A rig never stands in for a different lift: borrowed angles or the wrong equipment would teach the wrong movement, and the angles were sourced for that lift only. Every exercise shows the muscle map, so all 149 have a figure; the rig is extra.

Result: **20 exercises have a rig, 129 have `rig = null` with a written reason** in `no-rig.json`. 20 of the 21 XNK patterns are used; `squat-machine` (a belt/lever squat machine) matches no entry.

| Exercise | XNK rig | Note |
| --- | --- | --- |
| `SQUAT_GOB` | `goblet-squat` | |
| `LEG_PRESS` | `leg-press` | XNK rig is the 45° sled |
| `HINGE_RDL_DB` | `db-rdl` | |
| `HINGE_HT_MC` | `hip-thrust-machine` | |
| `PUSHH_BENCH_DB` | `db-bench-press` | |
| `PUSHH_PECDEC` | `chest-fly-machine` | |
| `PUSHH_PUSHUP_INCL` | `incline-push-up` | Rig shows a bench-height support; entry says wall or chair |
| `PUSHH_PUSHUP` | `push-up` | |
| `PUSHV_DB` | `db-shoulder-press` | Rig is seated with a back rest; entry lists no bench ⚠ owner: keep, or derive a standing rig |
| `PUSHV_LAT_DB` | `lateral-raise` | Front view |
| `PULLH_CBLROW` | `seated-cable-row` | |
| `PULLH_BO_DB` | `db-row` | |
| `PULLV_PULLDOWN` | `lat-pulldown` | |
| `CURL_CABLE` | `bicep-curl-cable` | |
| `TRI_PUSHDOWN` | `triceps-pushdown` | |
| `LEGISO_EXT` | `leg-extension` | |
| `LEGISO_CURL_SEAT` | `seated-leg-curl` | |
| `LEGISO_CALF_STAND` | `calf-raise-machine` | |
| `LEGISO_ABD` | `hip-abduction-machine` | Front view |
| `CORE_DEADBUG` | `dead-bug` | |

**Without a rig (129), by kind** (`no-rig.json`; this is the remaining C-08 drawing work, task C-08b):

| Kind | Count | Meaning | Exercises (base pattern) |
| --- | --- | --- | --- |
| `derive` | 67 | Same joint path as an XNK base pattern, but a different load, grip, support or machine. Needs its own angle table from a written source and its own equipment drawing | goblet-squat: `SQUAT_BACK`, `SQUAT_FRONT`, `SQUAT_SMITH`, `SQUAT_DB`, `SQUAT_SUMO_DB`, `SQUAT_BAND`, `SQUAT_BW`, `SQUAT_CHAIR`, `SQUAT_JUMP`, `SQUAT_WALLSIT` · db-rdl: `HINGE_RDL_BB`, `HINGE_DL_BB`, `HINGE_TRAP`, `HINGE_GM`, `HINGE_DL_DB`, `HINGE_GM_BAND` · hip-thrust-machine: `HINGE_HT_BB`, `HINGE_HT_DB`, `HINGE_HT_BW` · db-bench-press: `PUSHH_BENCH_BB`, `PUSHH_INCL_BB`, `PUSHH_INCL_DB`, `PUSHH_CHEST_MC`, `PUSHH_INCL_MC`, `PUSHH_FLOOR_DB`, `PUSHH_BAND`, `TRI_CLOSE` · chest-fly-machine: `PUSHH_FLY_CBL`, `PUSHH_FLY_DB`, `PUSHH_BAND_FLY` · push-up: `PUSHH_PUSHUP_KNEE`, `PUSHH_PUSHUP_DECL`, `TRI_NARROW` · db-shoulder-press: `PUSHV_OHP_BB`, `PUSHV_MC`, `PUSHV_BAND` · lateral-raise: `PUSHV_LAT_CBL`, `PUSHV_LAT_BAND` · db-row: `PULLH_BBROW`, `PULLH_TBAR`, `PULLH_CSROW_MC`, `PULLH_DBROW`, `PULLH_CSROW_DB` · seated-cable-row: `PULLH_SA_CBL`, `PULLH_BAND_ROW` · lat-pulldown: `PULLV_VBAR`, `PULLV_ASSIST`, `PULLV_PULLUP`, `PULLV_CHINUP`, `PULLV_NEG`, `PULLV_BAND_PD`, `PULLV_BAND_PU` · bicep-curl-cable: `CURL_BB`, `CURL_EZ`, `CURL_PREACHER`, `CURL_INCL_DB`, `CURL_DB`, `CURL_HAMMER`, `CURL_BAND` · triceps-pushdown: `TRI_ROPE`, `TRI_BAND` · seated-leg-curl: `LEGISO_CURL_LYING` · hip-abduction-machine: `LEGISO_BAND_ABD`, `LEGISO_ADD` (after the owner's muscle decision) · calf-raise-machine: `LEGISO_CALF_DB`, `LEGISO_CALF_BW`, `LEGISO_CALF_SL` |
| `new` | 55 | No XNK pattern with the same joint path. Needs a new rig with joint angles from a written source | Single-leg squats and lunges (`SQUAT_BULG_DB`, `SQUAT_BULG_BW`, `SQUAT_SPLIT_BW`, `LUNGE_REV_DB`, `LUNGE_REV_BW`, `LUNGE_WALK_DB`, `LUNGE_WALK_BW`, `STEPUP_DB`, `STEPUP_BW`, `SQUAT_PISTOL`), `SQUAT_HACK`; `HINGE_BACKEXT`, `HINGE_PULLTHRU`, `HINGE_BAND_PT`, `HINGE_SLDL_DB`, `HINGE_SLDL_BW`, `HINGE_SWING_KB`, `HINGE_BRIDGE_BW`, `HINGE_BRIDGE_SL`, `HINGE_SUPERMAN`; `PUSHV_PIKE`, `PUSHV_PIKE_KNEE`, `PUSHV_HSPU_WALL`; `PULLH_FACEPULL`, `PULLH_BAND_FACE`, `PULLH_REVFLY_MC`, `PULLH_REVFLY_DB`, `PULLH_BAND_REV`, `PULLH_INVROW`, `PULLH_INVROW_KNEE`, `PULLH_TOWEL`; `PULLV_STRAIGHT`, `PULLV_BAND_ST`, `PULLV_DB_PULLOVER`; `TRI_OH_CBL`, `TRI_OH_DB`, `TRI_BAND_OH`, `TRI_SKULL`, `TRI_DIPMC`, `TRI_BENCHDIP`, `TRI_BWEXT`; `LEGISO_KICK_CBL`, `LEGISO_KICK_BW`; all five carries; `CORE_PLANK`, `CORE_SIDEPLANK`, `CORE_HOLLOW`, `CORE_CRUNCH`, `CORE_BIRDDOG`, `CORE_PALLOF`, `CORE_PALLOF_BAND` |
| `map-only` | 7 | Proposal (owner to confirm): cyclic whole-body movement; a 13-frame loop teaches little. Muscle map and cues only | `CARDIO_TREAD`, `CARDIO_WALK`, `CARDIO_BIKE`, `CARDIO_ROWER`, `CARDIO_WALK_BW`, `CARDIO_JOG`, `CARDIO_ROPE` |

No new rig was drawn in C-08: no joint-angle source was collected for any of the 129, and the rig builder (`build-rig.js`) lives in the XNK repo, which this session could not open. Hand-written rig data would break the "angles only from a written source" rule above.

### 5.2 Muscle map against the library (C-08)

The app colours both the muscle map and the rig marks from `exercises.json` muscles through `content/figures/muscle-map.json`, never from the rig's own muscle list. Changes from the D-05 mockup mapping, so that the map shows only the library's muscles:

- `QUADS` no longer lights the figure's `adductors` (inner thigh is not the quadriceps; §12.3). `adductors` now has no code and stays unlit.
- The figure's `upper back` (middle trapezius and rhomboid area) moves from `LATS` to `TRAPS`.
- `GLUTES` keeps `abductors` (gluteus medius and minimus abduct the hip, OpenStax 2022 §11.6).

The mockups (`docs/mockups/mock.js`) still use the old mapping; they are not shipped.

Mismatches the figure cannot fix (reported, not failed, by the checker):

| Issue | Effect | Exercises |
| --- | --- | --- |
| One `delts` region for `FRONT_DELT`, `SIDE_DELT`, `REAR_DELT` | The map cannot show which head works; the whole shoulder takes the strongest level | 47 entries name the heads at different levels, e.g. the 13 rows, face pulls and reverse flies with `REAR_DELT` main, the 3 lateral raises (`SIDE_DELT` main) |
| One `abs` region for `ABS` and `OBLIQUES` (XNK merged the package's obliques into abs) | Oblique work shows as front abs | 11 entries: `CORE_SIDEPLANK`, the five carries, `HINGE_SWING_KB`, `PULLH_SA_CBL`, `CORE_PLANK`, `CORE_CRUNCH`, `CORE_DEADBUG` |
| Front-view rig `lateral-raise` has no trapezius mark | `TRAPS` helper shows on the map only, not on the rig | `PUSHV_LAT_DB` |
| XNK rig's own muscle list differs from the library | Ignored by rule; the library wins | 14 of 20 rigged entries (checker output), e.g. `seated-cable-row` rig: upper back main, library: `LATS`, `TRAPS`, `REAR_DELT` main |

Fixing the first two needs new shapes: split `delts` into three heads and keep `obliques` in the XNK figure build (`build-figure.js`), then re-copy. That is an XNK change; listed in C-08b.

## 6. Badges (P-5)

18 badges, none for weight lost (PRD P-5). Each has a code, a plain title (ID), a condition, and a kind message.

| # | Code | Title (ID) | Condition |
| --- | --- | --- | --- |
| 1 | `WO_FIRST` | Latihan pertama | First finished workout |
| 2 | `WO_5` | Lima kali latihan | 5 finished workouts |
| 3 | `WO_20` | Dua puluh latihan | 20 finished workouts |
| 4 | `WO_50` | Lima puluh latihan | 50 finished workouts |
| 5 | `STREAK_4` | Empat minggu | Streak of 4 weeks |
| 6 | `STREAK_12` | Dua belas minggu | Streak of 12 weeks |
| 7 | `PR_FIRST` | Rekor pertama | First personal record |
| 8 | `PR_10` | Sepuluh rekor | 10 personal records |
| 9 | `ACT_150` | 150 menit | 150 activity minutes in one week |
| 10 | `ACT_300` | 300 menit | 300 activity minutes in one week (vigorous counts double) |
| 11 | `FOOD_7` | Tujuh hari catat | Food logged on 7 days |
| 12 | `FOOD_30` | Tiga puluh hari catat | Food logged on 30 days |
| 13 | `TARGET_WEEK` | Minggu sesuai target | A week within ±10 % of target |
| 14 | `MEASURE_4` | Empat kali ukur | 4 scheduled measures |
| 15 | `PHOTO_FIRST` | Foto pertama | First progress photo (kept on the phone) |
| 16 | `BACK` | Kembali lagi | Back after 14+ days away, workout finished |
| 17 | `LEVEL_3` | Level 3 | Reached level 3 |
| 18 | `PLAN_WEEK` | Rencana seminggu | Week planner full for 7 days |

Built in C-10 (2026-11-18): [`content/badges.json`](../content/badges.json) holds the 18 codes (stable IDs, stored in `badge.code`), Indonesian titles, machine-readable conditions, one celebration message each, and an icon name from the mockup set. Checked by `node tools/check-badges.mjs` (exactly 18, no duplicate code, title or order, required fields, tone rules).

- **Conditions** use eleven named metrics defined once in the file (for example `finishedWorkouts`, `personalRecords`, `streakWeeks`, `activityMinutesWeek`), each computed from Room tables in Design §3: `workout`, `workout_set`, `activity`, `food_log`, `points_event`, `measure`, `photo`, `meal_plan`. They are deterministic and can be unit-tested from fixture rows.
- **Personal record** (not a table in Design §3): derived from `workout_set`. A done set beats every earlier set of the same exercise by load, or by reps at equal load, or by reps for bodyweight. The first set of an exercise is a baseline. One record per exercise per workout.
- **Earned once, never lost.** Badges never depend on weight, calories eaten below target, or very low intake. The activity badges stop at 300 minutes a week (WHO upper range, S13); there is no volume badge above it (PRD PR-5). `TARGET_WEEK` counts weeks within ±10 % of target on either side and its message does not mention food amounts. `MEASURE_4` counts measuring, never the value.
- **Messages**: Indonesian, neutral, no "kamu" or "Kak", at most one emoji, at most 110 characters, no weight or calorie words (Rules R_TONE, R_TONE_WORDS). Titles follow the table above; the Saya mockup uses shorter labels as placeholders and the app uses `title_id`.
- **Icons** (names only, no assets): star, medal, trophy, flame, run, food, check, ruler, camera, heart, cal. The grey locked state is drawn by the app, not a badge icon.

## 7. Activities and MET (L-14)

Activity codes with MET values are in [Rules.md §4](Rules.md#4-activity-met). Content adds the Indonesian labels and short examples:

| Code | Label (ID) | Example line |
| --- | --- | --- |
| `jalan` | Jalan cepat | Jalan santai ke pasar |
| `lari` | Lari | Lari pagi di taman |
| `sepeda` | Sepeda | Sepeda ke kantor |
| `renang` | Renang | Berenang bolak-balik |
| `angkat` | Angkat beban | Latihan beban di rumah |
| `senam` | Senam | Senam irama |
| `bulutangkis` | Bulu tangkis | Main bulu tangkis sore |
| `sepak` | Sepak bola | Main futsal |
| `lain` | Lainnya | Free text plus MET from the closest row |

## 8. Open content items (⚠)

| # | Item | Owner / task |
| --- | --- | --- |
| C-05 | Household measures for cooked dishes and centong weights (estimates until verified) | Research task after D-04 |
| C-06 | Packaged food label values (about 80) | Collect from label photos or product sheets; owner or task |
| C-04 | Warung and bought-meal estimates: 66 rows built in `content/foods-estimates.json` (see §4.1) | Done; 10 rows ⚠ |
| C-07 | Bought and regional meal numbers (wider set; replace estimates where a label or official site exists) | Flagged estimates; listed here until a source exists (PRD §14 item 6) |
| C-01 | Muscle review done 2026-11-18 (§12): 57 entries `verified`, 65 `analog`, 27 `unverified` (praktik umum ⚠). 21 entries had muscles changed | Remaining ⚠ need a study or owner decision |
| C-01b | 35 entries still `review.muscles = "uncertain"` ⚠, each with its reason in `review.note` (§12.3). Owner decisions: `LEGISO_ADD` (adductors not in the 16 muscles; QUADS is wrong) and `PULLV_DB_PULLOVER` pattern (now CHEST main, still `PULL_V`) | Owner; later research |
| C-08 | Figures reused from XNK (§5.1): 20 exercises with a rig, 129 with a written reason; every exercise has the muscle map. XNK commit not recorded ⚠ | Done 2026-11-18; rest in C-08b |
| C-08b | Rigs still to draw with sourced joint angles: 67 `derive`, 55 `new`; owner to confirm 7 cardio `map-only` and `PUSHV_DB` seated rig; record XNK commit; split delts and obliques in the XNK figure build (§5.2) | Needs XNK repo access and a joint-angle source per rig |
| C-09 | English cues for each exercise (3 lines): all 149 drafted by Claude in its own words, none owner-reviewed | Owner review of English (PRD §14 item 3) |
| C-10 | Level ↔ exercise mapping for variants | Set when the pattern list is final |

## 9. Sources for exercises and figures

| Item | Source | Status |
| --- | --- | --- |
| Pattern list (§3) | Standard resistance-training pattern classes, ACSM Position Stand 2009 (Rules S14) | ✅ class list; exercise picks ⚠ review |
| Main and helper muscles per exercise | `source.muscles_cues` per entry: EMG studies, biomechanics studies or an anatomy text (list in §12.4) | ✅ 57 verified, ✅/⚠ 65 analog, ⚠ 27 unverified; 35 uncertain (§12.3) |
| Joint angles per pose | 20 XNK rigs: XNK `docs/program/Gerak.md` (not read in C-08, ⚠). New rigs: standard range-of-motion descriptions, cited per pose | ⚠ 129 to fill (C-08b) |
| Calories per food | TKPI 2020 (Rules S9) | ✅ source; per-row check when built |

No exercise is published without its source line in the library file.

## 10. Reminder and tone text

Reminder text lives in the strings file, not in code. Samples that set the tone (R-2, R_TONE):

| Type | Example |
| --- | --- |
| Latihan | "Latihan hari ini: Full Body A, sekitar 45 menit." |
| Makan | "Sudah makan siang? Catat dulu, cukup satu menit." |
| Timbang | "Timbang Minggu pagi, tidak harus setiap hari." |
| Rest done | "Istirahat selesai. Siap set berikutnya?" |
| Skip (already done) | No reminder sent |

Rules: short, neutral, no shaming words, no "harus". Each type has at least five variants used in rotation (Design §8).

## 11. Changes log

| Date | Change |
| --- | --- |
| 2026-11-16 | First draft (D-04). Counts are targets; the library is built in its own tasks. |
| 2026-11-18 | C-04: 66 warung and bought-meal estimates added (§4.1, §8). |
| 2026-11-18 | C-02: pattern check passes (minimum 3 per required pattern and setting; CI wiring left to B-01). |
| 2026-11-18 | C-01: `content/exercises.json` with 149 entries and `tools/check-exercises.mjs`. `variantOf` points to the easier variant. |
| 2026-11-18 | C-01 muscle review (Opus): sources per entry, 21 muscle changes, 1 hint change, statuses `verified` / `analog` / `unverified`, file version 2026-11-18.2 (§12). |
| 2026-11-18 | C-08: XNK muscle map and rig copied to `content/figures/` (credits kept, sha256 locked, XNK commit ⚠); `rig` field added to `exercises.json` (20 rigged, 129 with reason in `no-rig.json`); muscle-map changes for QUADS and upper back; `tools/check-figures.mjs` (§5.1, §5.2). |

## 12. Muscle review (C-01, 2026-11-18)

Scope: main and helper muscles, pattern, equipment and settings of all 149 entries in `content/exercises.json`. English cues (C-09) and figures (C-08) were not touched. Sources were opened as abstracts (Europe PMC), full text where free (McGill 2009, McGill 2012, Escamilla 2016) and OpenStax for single-joint anatomy. Each entry now has:

- `source.muscles_cues`: exact citation (authors, year, journal, volume, pages, table or abstract page) and the figure used, or `praktik umum`.
- `source.status`: `verified` (a study or anatomy text covers this exercise), `analog` (a study of the closely related parent exercise; same joint actions, different load or setup), `unverified` (praktik umum ⚠).
- `review.muscles`: `reviewed`, or `uncertain` ⚠ with the reason in `review.note`; `review.date`.

Result: 57 verified, 65 analog, 27 unverified. 114 reviewed, 35 uncertain ⚠. Of the 23 entries flagged in the draft, 7 are resolved and 16 stay ⚠; 19 more entries were marked ⚠ because no study was found. Pattern, equipment and settings: no errors found; `node tools/check-exercises.mjs` passes with the same counts per pattern and setting.

### 12.1 Muscle changes (21 entries)

| ID | Before (main / helper) | After (main / helper) | Reason |
| --- | --- | --- | --- |
| `SQUAT_FRONT` | QUADS / GLUTES, ABS, LOWER_BACK | QUADS, GLUTES / HAMS, LOWER_BACK, ABS | Gullett 2009: muscle activity no different from back squat, so same split as `SQUAT_BACK` |
| `SQUAT_SMITH` | QUADS, GLUTES / – | QUADS, GLUTES / HAMS | Svensson 2022: hamstrings active (less than free squat) |
| `HINGE_HT_DB` | GLUTES / HAMS | GLUTES / HAMS, QUADS | Match barbell hip thrust; Contreras 2015 vastus lateralis similar to squat |
| `HINGE_HT_BW` | GLUTES / HAMS | GLUTES / HAMS, QUADS | As above |
| `HINGE_SWING_KB` | GLUTES, HAMS / LOWER_BACK, FOREARM, ABS | GLUTES, HAMS / LOWER_BACK, OBLIQUES, FOREARM | McGill 2012 Table 1: rectus abdominis 7 %, internal oblique 30–42 % MVC; Zebis 2013 semitendinosus 73–115 % keeps HAMS main |
| `PUSHH_FLY_DB` | CHEST / FRONT_DELT, BICEPS | CHEST / FRONT_DELT | No fly study measured biceps; Campos 2020 anterior deltoid 19 % MVIC |
| `PUSHV_LAT_DB` | SIDE_DELT / – | SIDE_DELT / FRONT_DELT, REAR_DELT, TRAPS | Campos 2020: medial 30 %, posterior 24 %, anterior 21 % MVIC; Coratella 2020 upper trapezius active |
| `PUSHV_LAT_CBL` | SIDE_DELT / – | SIDE_DELT / FRONT_DELT, REAR_DELT, TRAPS | As dumbbell lateral raise |
| `PUSHV_LAT_BAND` | SIDE_DELT / – | SIDE_DELT / FRONT_DELT, REAR_DELT, TRAPS | As dumbbell lateral raise |
| `PULLH_SA_CBL` | LATS / BICEPS, TRAPS | LATS / BICEPS, TRAPS, OBLIQUES | Fenwick 2009: one-arm cable row challenges trunk twist |
| `PULLV_DB_PULLOVER` | LATS, CHEST / TRICEPS | CHEST / LATS, TRICEPS | Marchetti 2011: pectoralis major EMG significantly larger than latissimus; Di Fonza 2026 review agrees. Pattern left as `PULL_V` (owner) |
| `CARRY_FARMER_DB` | FOREARM, TRAPS / ABS, OBLIQUES, GLUTES | FOREARM, TRAPS / OBLIQUES, LOWER_BACK, GLUTES | McGill 2009 Table 2: rectus abdominis 13–21 %, lumbar erector spinae 106–144 % MVC |
| `CARRY_TRAP` | FOREARM, TRAPS / ABS, GLUTES | FOREARM, TRAPS / OBLIQUES, LOWER_BACK, GLUTES | As farmer's walk |
| `CARRY_SUIT_DB` | OBLIQUES, FOREARM / ABS, TRAPS | OBLIQUES, FOREARM / LOWER_BACK, GLUTES, TRAPS | McGill 2009 Table 2: opposite-side external oblique 65 %, lumbar erector spinae 97 %, gluteus medius 57–64 %, rectus abdominis 6–21 % |
| `CARRY_KB` | OBLIQUES, FOREARM / ABS | OBLIQUES, FOREARM / LOWER_BACK, GLUTES | As suitcase carry |
| `CARRY_FRONT_KB` | ABS, FOREARM / OBLIQUES, TRAPS | OBLIQUES, FOREARM / LOWER_BACK, TRAPS | McGill 2012 Table 3: racked carry rectus abdominis 1.1–1.3 %, internal oblique 8.4 %, erector spinae 4.5–7.9 % MVC (all low) |
| `CORE_PLANK` | ABS / OBLIQUES, LOWER_BACK | ABS / OBLIQUES | Escamilla 2016 Table 1: lumbar paraspinals 5 % MVIC |
| `CORE_SIDEPLANK` | OBLIQUES / ABS | OBLIQUES / ABS, LOWER_BACK, GLUTES | Escamilla 2016: external oblique 62 %, paraspinals 29 %; Ekstrom 2007: gluteus medius at strengthening level |
| `CORE_CRUNCH` | ABS / – | ABS / OBLIQUES | Escamilla 2016: external/internal oblique 28/33 % MVIC |
| `CORE_BIRDDOG` | ABS, LOWER_BACK / GLUTES | LOWER_BACK, GLUTES / ABS | Ekstrom 2007: gluteus maximus at strengthening level; ABS placement still praktik umum (note in entry) |
| `CARDIO_BIKE` | QUADS, HAMS / GLUTES, CALVES | QUADS, GLUTES / HAMS, CALVES | Ericson 1988: gluteus maximus and vasti carry power; biceps femoris mainly a hip extensor |

Other change: `SQUAT_FRONT` `hint_id` "…dan menekan paha depan" → "…; otot yang bekerja sama dengan squat biasa." (the old line contradicted Gullett 2009). Main bench, push-up and press splits were checked against Welsch 2005, Muyor 2023, Campos 2020 and Calatayud 2015 and left as they were.

### 12.2 Draft flags resolved (7)

`SQUAT_PISTOL` (DiStefano 2009 single-limb squat), `HINGE_SWING_KB`, `PULLV_DB_PULLOVER`, `CARRY_SUIT_DB`, `CARRY_KB`, `CORE_SIDEPLANK`, `CARDIO_BIKE`.

### 12.3 Still ⚠ (35, reason in `review.note`)

| Reason | IDs |
| --- | --- |
| Owner decision: no correct code in the 16 muscles | `LEGISO_ADD` (adductors; QUADS is wrong. Drop with `active=false` or add a muscle) |
| Main muscles not measured (grip, trapezius) | `CARRY_FARMER_DB`, `CARRY_TRAP`, `CARRY_FRONT_KB` |
| Muscles confirmed active, but no main/helper ranking in the source | `CARDIO_TREAD`, `CARDIO_WALK`, `CARDIO_WALK_BW`, `CARDIO_JOG` (Cappellini 2006), `CARDIO_ROWER` (Turpin 2011) |
| No EMG study found (praktik umum) | `SQUAT_WALLSIT`, `SQUAT_HACK`, `SQUAT_JUMP`, `HINGE_BACKEXT`, `HINGE_PULLTHRU`, `HINGE_BAND_PT`, `HINGE_SUPERMAN`, `PUSHV_PIKE`, `PUSHV_PIKE_KNEE`, `PUSHV_HSPU_WALL`, `PULLH_FACEPULL`, `PULLH_BAND_FACE`, `PULLH_REVFLY_MC`, `PULLH_REVFLY_DB`, `PULLH_BAND_REV`, `PULLH_TOWEL`, `TRI_DIPMC`, `TRI_BENCHDIP`, `TRI_BWEXT`, `CORE_DEADBUG`, `CORE_HOLLOW`, `CORE_PALLOF`, `CORE_PALLOF_BAND`, `CARDIO_ROPE` |
| Source exists but could not be opened (HTTP 403) | `PULLV_STRAIGHT`, `PULLV_BAND_ST` |

`PULLH_TOWEL` also keeps the open safety question of the door anchor. `analog` entries are usable but weaker than `verified`: incline presses (front-delt share), hammer curl (brachioradialis share) and machine/band variants rely on the parent exercise.

### 12.4 Sources used

| Key | Citation | Used for |
| --- | --- | --- |
| Andersen 2018 | Andersen V et al. J Strength Cond Res 32(3):587–593 | Deadlift, hex bar, hip thrust |
| Aygun-Polat 2025 | Aygun-Polat E et al. BMC Sports Sci Med Rehabil 17:251 | Bulgarian split squat |
| Calatayud 2015 | Calatayud J et al. J Strength Cond Res 29(1):246–253 | Push-up vs bench press |
| Camara 2016 | Camara KD et al. J Strength Cond Res 30(5):1183–1188 | Straight vs hex bar deadlift |
| Campos 2020 | Campos YAC et al. J Hum Kinet 75:5–14 | Deltoid in press, raise, fly |
| Cappellini 2006 | Cappellini G et al. J Neurophysiol 95(6):3426–3437 | Walking, running |
| Contreras 2015 | Contreras B et al. J Appl Biomech 31(6):452–458 | Hip thrust vs squat |
| Contreras 2016 | Contreras B et al. J Appl Biomech 32(3):254–260 | Hip thrust variants |
| Coratella 2020 | Coratella G et al. Int J Environ Res Public Health 17(17):6015 | Lateral raise |
| Di Fonza 2026 | Di Fonza D et al. J Funct Morphol Kinesiol 11(3):315 | Latissimus review (rows, pulldown, pullover) |
| DiStefano 2009 | DiStefano LJ et al. J Orthop Sports Phys Ther 39(7):532–540 | Single-limb squat and deadlift, lunge, abduction |
| Ekstrom 2007 | Ekstrom RA et al. J Orthop Sports Phys Ther 37(12):754–762 | Bridge, side bridge, bird dog, lunge, step-up |
| Ericson 1988 | Ericson MO. Scand J Rehabil Med 20(1):35–41 | Cycling |
| Escamilla 1998 | Escamilla RF et al. Med Sci Sports Exerc 30(4):556–569 | Squat, leg press, leg extension |
| Escamilla 2001 | Escamilla RF et al. Med Sci Sports Exerc 33(9):1552–1566 | Squat and leg press |
| Escamilla 2016 | Escamilla RF et al. Sports Health 8(4):372–379, Table 1 | Crunch, plank, side plank |
| Fenwick 2009 | Fenwick CM et al. J Strength Cond Res 23(5):1408–1417 | Rows |
| Gullett 2009 | Gullett JC et al. J Strength Cond Res 23(1):284–292 | Front vs back squat |
| Kipp 2022 | Kipp K et al. J Strength Cond Res 36(2):314–323 | Squat, split squat, step-up |
| Marchetti 2011 | Marchetti PH, Uchida MC. J Appl Biomech 27(4):380–384 | Pullover |
| Marcolin 2015 | Marcolin G et al. J Athl Train 50(11):1126–1132 | Push-up variants |
| Marcolin 2018 | Marcolin G et al. PeerJ 6:e5165 | Curls |
| McAllister 2014 | McAllister MJ et al. J Strength Cond Res 28(6):1573–1580 | RDL, good morning, leg curl |
| McGill 2009 | McGill SM et al. J Strength Cond Res 23(4):1148–1161, Table 2 p. 1154 | Farmer's walk, suitcase carry |
| McGill 2012 | McGill SM, Marshall LW. J Strength Cond Res 26(1):16–27, Table 1 p. 21, Table 3 p. 24 | Kettlebell swing, racked carry |
| Muyor 2023 | Muyor JM et al. J Hum Kinet 87:23–34 | Bench press, chest press |
| Snarr 2017 | Snarr RL et al. J Hum Kinet 58:5–13 | Pull-up variants |
| Svensson 2022 | Svensson F et al. Front Sports Act Living 4:1021323 | Smith squat |
| Turpin 2011 | Turpin NA et al. J Electromyogr Kinesiol 21(6):1030–1040 | Rowing ergometer |
| Welsch 2005 | Welsch EA et al. J Strength Cond Res 19(2):449–452 | Bench press, fly |
| Youdas 2010 | Youdas JW et al. J Strength Cond Res 24(12):3404–3414 | Pull-up, chin-up |
| Youdas 2016 | Youdas JW et al. J Strength Cond Res 30(7):1933–1941 | Inverted row |
| Zebis 2013 | Zebis MK et al. Br J Sports Med 47(18):1192–1198 | Kettlebell swing, RDL, leg curl |
| OpenStax 2022 | Betts JG et al. *Anatomy and Physiology 2e*, OpenStax, §11.5 Fig. 11.26; §11.6 Figs. 11.30–11.32 | Single-joint arm, hip, knee and ankle actions |

Limits: most figures come from abstracts; MVC normalisation differs between studies, so numbers are compared only within a study. EMG amplitude is activation, not proof of growth; Muyor 2023 advises against choosing exercises on activation alone.
