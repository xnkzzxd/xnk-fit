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


### 4.2 TKPI 2020 table (C-03)

File: `content/foods-tkpi.json`. Built by `node tools/build-tkpi.mjs <tkpi.pdf>`, checked by `node tools/check-foods-tkpi.mjs`. The PDF (Kemenkes, 148 pages, table "Komposisi zat gizi makanan per 100 gram BDD", PDF pages 15 to 83) is **not in the repo** (copyright, 21 MB); the owner's copy is on Google Drive. Run the builder from a folder other than the download folder.

- **1,143 rows** in the table, all `source = tkpi`, `isEstimate = false`, 64 table pages, 13 sections (4.1 Serealia to 4.13 Minuman). **3 rows** are not in the table and sit in `needsHandCheck` with page numbers (below). No TKPI code is missing apart from those 3.
- **Per row:** `id` (`TKPI_` + code), `code` (original TKPI code, e.g. AR001), `name_id`, `group` and `groupName` (section), `processing` (raw `R` / processed `P`, from the code), `kcalPer100`, `protein`, `fat`, `carb`, `fibre`, `sodium`, `micro` (water, ash, calcium, phosphorus, iron, potassium, copper, zinc, retinol, beta-carotene, total carotene, thiamin, riboflavin, niacin, vitamin C), `bdd` (edible portion %), `sourceRef` (the SUMBER column), `page` (PDF page), `flags`.
- **Method:** `pdftotext -bbox` gives each word with its x/y position. A row starts at its code; name and source words are taken by position; each number goes to the column whose centre (from the unit header row of that page) is nearest. Because of that, **blank cells stay blank** (`null`, "tidak dianalisis") and do not shift later numbers. Page layouts differ by a few points, so the column centres are read per page. Numbers are copied as printed; nothing is rounded, filled in, imputed or estimated.
- **Atwater (Rules S9):** the checker compares printed kcal with 4/4/9 from protein, carbohydrate and fat. TKPI does not always use 4/4/9 (it uses food-specific factors, counts fibre and other carbohydrates differently, and some legumes, oils and alcohol-containing items differ), so a row is only flagged `kcalVsMacros` when it is off by more than 15 % **and** more than 10 kcal. 53 rows are flagged. Several of these are clearly typos in the printed table (for example AP022 Mi kering: 339 kcal with carbohydrate 6.3 g; DR004 Andewi: 226 kcal; BR033 Umbi Uwi: carbohydrate 82.3 g with 120 kcal). The app shows the printed kcal, not a recalculated one.
- **Other source flags:** `proximateSum` (water + protein + fat + carbohydrate + ash is not 100 g ± 6 g), 23 rows; `implausible:<field>` (one printed value above a physical limit, for example copper 110 mg in apples ER003, which looks like a unit mix-up in the table), 21 values; `macrosOver100` (GP022, GP060); `fibreOverCarb`, 7 rows. In total **92 rows carry at least one flag** (8.0 %). The numbers are still exactly as printed. B-04 decides whether flagged rows are hidden from search; until then treat flagged rows as ⚠ and do not use the flagged field in any calculation.
- **Not in this file:** `name_en` is `null` (English names are only in the PDF index, not parsed); `household` is empty (C-05); `halal` and `allergens` are `null` because TKPI does not state them (items with pork, such as KR003 Lemak babi, must be marked by C-11 or B-04 before any halal filter is trusted).
- **Empty cells:** fibre is empty in 220 rows, BDD in 155 rows (the PDF itself says some foods have no BDD). Sodium and other micronutrients are empty where the PDF is empty. TKPI marks imputed and borrowed values in light blue; the colour is not in the text, so the file does not say which values are imputed.

**Hand-check list (3 rows, not in the table):**

| Code | Page | Name | Reason |
| --- | --- | --- | --- |
| ER101 | 53 | Salak, segar | fat cell is empty in the PDF (core value) |
| GP059 | 70 | Dendeng belut, goreng, masakan | one cell is printed as "1,480" (thousands comma); not guessed |
| MP013 | 79 | Melase | protein cell is empty in the PDF (core value) |

**Spot-check (30 random rows, seed 20261118) against the PDF:** each row's values were compared in order with an independent plain-text extraction (`pdftotext -layout`), 30 of 30 matched. Column placement with blanks was also checked by eye on the rendered page 68 (rows GP020 to GP038, including GP034, GP033 and GP022). Rows: AP009, AP039, AP049, AP087, BP070, CP019, CP033, CP034, CP046, DR021, DR101, DR116, DR151, DP003, DP044, ER092, ER097, ER107, FP029, FP066, GR060, GP008, GP034, GP036, GP061, GP095, HP002, HP007, MP008, NR001. The order-only comparison cannot see a value that moved to a neighbouring column, so the sum-to-100 and 4/4/9 checks in the checker are the guard for that.

**Known limits (kendala):** blank cells and the "1,480" cell above; typos and unit slips printed in the table itself (flags above); fibre in TKPI is a mix of total fibre and dietary fibre (PDF note to Table 3), so fibre is only comparable within a row, not across foods; zinc in wheat flour is adjusted for fortification but not in foods made from it (PDF §2.6); vitamin C uses an old titration method; the file does not know which values are imputed (colour only).

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
| C-01 | Muscle review done 2026-11-18 (§12): 57 entries `verified`, 65 `analog`, 27 `unverified` (praktik umum ⚠). 21 entries had muscles changed | Remaining ⚠ need a study or owner decision |
| C-01b | 34 entries still `review.muscles = "uncertain"` ⚠, each with its reason in `review.note` (§12.3). Owner decided 2026-11-18: `LEGISO_ADD` keeps QUADS as a stand-in; `PULLV_DB_PULLOVER` stays `PULL_V` | Later research |
| C-08 | Exercise figure poses (about 25 base poses, sourced angles) | Design task D-05 onward (PRD §14 item 2) |
| C-09 | English cues for each exercise (3 lines): all 149 drafted by Claude in its own words, none owner-reviewed | Owner review of English (PRD §14 item 3) |
| C-10 | Level ↔ exercise mapping for variants | Set when the pattern list is final |

## 9. Sources for exercises and figures

| Item | Source | Status |
| --- | --- | --- |
| Pattern list (§3) | Standard resistance-training pattern classes, ACSM Position Stand 2009 (Rules S14) | ✅ class list; exercise picks ⚠ review |
| Main and helper muscles per exercise | `source.muscles_cues` per entry: EMG studies, biomechanics studies or an anatomy text (list in §12.4) | ✅ 57 verified, ✅/⚠ 65 analog, ⚠ 27 unverified; 34 uncertain (§12.3) |
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
| 2026-11-18 | C-01 muscle review (Opus): sources per entry, 21 muscle changes, 1 hint change, statuses `verified` / `analog` / `unverified`, file version 2026-11-18.2 (§12). |
| 2026-11-18 | Owner decisions: `LEGISO_ADD` keeps QUADS (stand-in for adductors); `PULLV_DB_PULLOVER` stays `PULL_V`. |

## 12. Muscle review (C-01, 2026-11-18)

Scope: main and helper muscles, pattern, equipment and settings of all 149 entries in `content/exercises.json`. English cues (C-09) and figures (C-08) were not touched. Sources were opened as abstracts (Europe PMC), full text where free (McGill 2009, McGill 2012, Escamilla 2016) and OpenStax for single-joint anatomy. Each entry now has:

- `source.muscles_cues`: exact citation (authors, year, journal, volume, pages, table or abstract page) and the figure used, or `praktik umum`.
- `source.status`: `verified` (a study or anatomy text covers this exercise), `analog` (a study of the closely related parent exercise; same joint actions, different load or setup), `unverified` (praktik umum ⚠).
- `review.muscles`: `reviewed`, or `uncertain` ⚠ with the reason in `review.note`; `review.date`.

Result: 57 verified, 65 analog, 27 unverified. 115 reviewed, 34 uncertain ⚠. Of the 23 entries flagged in the draft, 8 are resolved (one by owner decision) and 15 stay ⚠; 19 more entries were marked ⚠ because no study was found. Pattern, equipment and settings: no errors found; `node tools/check-exercises.mjs` passes with the same counts per pattern and setting.

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
| `PULLV_DB_PULLOVER` | LATS, CHEST / TRICEPS | CHEST / LATS, TRICEPS | Marchetti 2011: pectoralis major EMG significantly larger than latissimus; Di Fonza 2026 review agrees. Pattern stays `PULL_V` (owner decision 2026-11-18) |
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

### 12.2 Draft flags resolved (8)

`SQUAT_PISTOL` (DiStefano 2009 single-limb squat), `HINGE_SWING_KB`, `PULLV_DB_PULLOVER`, `CARRY_SUIT_DB`, `CARRY_KB`, `CORE_SIDEPLANK`, `CARDIO_BIKE`, and `LEGISO_ADD` by owner decision 2026-11-18: QUADS kept as a stand-in (adductors are not in the 16 muscles; source status stays `unverified`).

### 12.3 Still ⚠ (34, reason in `review.note`)

| Reason | IDs |
| --- | --- |
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
