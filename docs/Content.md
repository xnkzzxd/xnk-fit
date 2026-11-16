# Content — XNK Fit

| | |
| --- | --- |
| Status | Draft, 2026-11-16 (task D-04) |
| Reads | [PRD.md](PRD.md) §7.4 (L-10), §7.5 (M-7…M-9), §7.6 (P-5). Numbers and sources: [Rules.md](Rules.md). |
| Scope | What content ships in the app: exercises and their figures, movement patterns, food data, meal packages, activity list, badges, reminder text sources, and how content updates work. |
| Language | Exercise names, muscles and cues in English (owner decision, PRD L-10). Food, meal and UI text in Indonesian (PRD §2). Every text lives in one strings or content file (NF-3). |

## 1. Shipping model

- **Bundled** with the app: exercises, patterns, the TKPI food table, bundled meal packages, badges, activity list, glossary, welcome cards.
- **Updatable** (PV-2): a static content file set, checksummed. The app downloads a manifest, verifies SHA-256 for each file, and swaps the content in one transaction. Nothing user-specific is sent.
- Each content file has a `version` and `schema` number. The app refuses a content file with a newer `schema` than it understands and keeps the bundled copy.
- Content IDs are stable forever (for example `SQUAT_GOB`). A removed exercise is marked `active = false`, never deleted, so old workouts still resolve (Design §3).

## 2. Equipment settings and exercise split (L-1, L-10)

| Setting | Code | Who | Equipment assumed |
| --- | --- | --- | --- |
| Gym | `gym` | Full gym members | Machines, barbell, cable, dumbbells, bench |
| Home bodyweight | `home-bw` | No equipment | Floor, wall, stairs, chair |
| Home dumbbell / band | `home-db` | Some equipment at home | Dumbbells, resistance bands, a pull-up bar optional |
| Tick your own | `custom` | Anyone | Whatever the user ticks in the equipment list |

Library target: **about 150 curated exercises** (PRD L-10):

| Group | Count | Covers |
| --- | --- | --- |
| Gym | about 60 | Machines, barbell, cable, dumbbell (heavy) |
| Dumbbell / band | about 40 | Dumbbell, band, kettlebell-light |
| Bodyweight | about 35 | Bodyweight, each with an easier and a harder variant |
| Cardio and core | about 15 | Treadmill, bike, rower, planks, carries, dead bug |

Every pattern (§3) has **at least three choices in each setting** that can carry it (L-10). A pattern with fewer than three in a setting is a content bug, tested in CI.

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

Titles, messages and condition checks live in the badge table (Design §3); the messages are kind and never mention weight loss.

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
| C-07 | Bought and regional meal numbers (warung, bought) | Flagged estimates; listed here until a source exists (PRD §14 item 6) |
| C-08 | Exercise figure poses (about 25 base poses, sourced angles) | Design task D-05 onward (PRD §14 item 2) |
| C-09 | English cues for each exercise (3 lines) | Owner review of English (PRD §14 item 3) |
| C-10 | Level ↔ exercise mapping for variants | Set when the pattern list is final |

## 9. Sources for exercises and figures

| Item | Source | Status |
| --- | --- | --- |
| Pattern list (§3) | Standard resistance-training pattern classes, ACSM Position Stand 2009 (Rules S14) | ✅ class list; exercise picks ⚠ review |
| Main and helper muscles per exercise | EMG-based activation review, to cite per exercise in the library file | ⚠ to fill per exercise |
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
