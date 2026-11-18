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
| C-01 | Library draft done (149). Source for every entry is "praktik umum" (status `unverified` ⚠); only the pattern class cites S14. EMG or guideline per entry still to find | Review task (Opus 5.5) |
| C-01b | Muscle review: 23 entries have `review.muscles = "uncertain"` with a note (`LEGISO_ADD` needs a decision: adductors are not in the 16 muscles). The other 126 are `draft` | Opus 5.5, separate session |
| C-08 | Exercise figure poses (about 25 base poses, sourced angles) | Design task D-05 onward (PRD §14 item 2) |
| C-09 | English cues for each exercise (3 lines): all 149 drafted by Claude in its own words, none owner-reviewed | Owner review of English (PRD §14 item 3) |
| C-10 | Level ↔ exercise mapping for variants | Set when the pattern list is final |

## 9. Sources for exercises and figures

| Item | Source | Status |
| --- | --- | --- |
| Pattern list (§3) | Standard resistance-training pattern classes, ACSM Position Stand 2009 (Rules S14) | ✅ class list; exercise picks ⚠ review |
| Main and helper muscles per exercise | Library file has `source.muscles_cues = "praktik umum"` on all 149 entries; EMG citation not yet found | ⚠ unverified, 23 flagged for Opus review |
| Joint angles per pose | Standard range-of-motion descriptions, cited per pose | ⚠ to fill per pose |
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
