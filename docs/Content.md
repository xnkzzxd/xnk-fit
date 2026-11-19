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
| `packaged` | Packaged foods by name (brand-neutral names, e.g. "Mie instan, kuah, 1 bungkus") with label values | start with about 80; **2 built** (C-06, §4.3) | `isEstimate = false` only when label-verified; third-party values `isEstimate = true` ⚠ |
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

Household measures (M-6): each food may have a list `household = [{code, label, grams, source}]`. Example codes: `centong` (cooking spoon), `piring`, `potong`, `sdm`, `sdt`, `gelas`, `butir`, `buah`, `iris`. PMK 41/2014 (Rules S17) gives the first set; centong, piring and cooked dishes are **our own estimates**, flagged ⚠ (§4.6). The measures live in `content/household.json`, keyed by food id; the app joins them to `food.household` (B-04).

Each food row: name_id, name_en, kcal per 100 g, protein, carbohydrate, fat, fibre, sodium (mg), household list, source code, `isEstimate`.


### 4.2 TKPI 2020 table (C-03)

File: `content/foods-tkpi.json`. Built by `node tools/build-tkpi.mjs <tkpi.pdf>`, checked by `node tools/check-foods-tkpi.mjs`. The PDF (Kemenkes, 148 pages, table "Komposisi zat gizi makanan per 100 gram BDD", PDF pages 15 to 83) is **not in the repo** (copyright, 21 MB); the owner's copy is on Google Drive. Run the builder from a folder other than the download folder.

- **1,143 rows** in the table, all `source = tkpi`, `isEstimate = false`, 64 table pages, 13 sections (4.1 Serealia to 4.13 Minuman). **3 rows** are not in the table and sit in `needsHandCheck` with page numbers (below). No TKPI code is missing apart from those 3.
- **Per row:** `id` (`TKPI_` + code), `code` (original TKPI code, e.g. AR001), `name_id`, `group` and `groupName` (section), `processing` (raw `R` / processed `P`, from the code), `kcalPer100`, `protein`, `fat`, `carb`, `fibre`, `sodium`, `micro` (water, ash, calcium, phosphorus, iron, potassium, copper, zinc, retinol, beta-carotene, total carotene, thiamin, riboflavin, niacin, vitamin C), `bdd` (edible portion %), `sourceRef` (the SUMBER column), `page` (PDF page), `flags`.
- **Method:** `pdftotext -bbox` gives each word with its x/y position. A row starts at its code; name and source words are taken by position; each number goes to the column whose centre (from the unit header row of that page) is nearest. Because of that, **blank cells stay blank** (`null`, "tidak dianalisis") and do not shift later numbers. Page layouts differ by a few points, so the column centres are read per page. Numbers are copied as printed; nothing is rounded, filled in, imputed or estimated.
- **Atwater (Rules S9):** the checker compares printed kcal with 4/4/9 from protein, carbohydrate and fat. TKPI does not always use 4/4/9 (it uses food-specific factors, counts fibre and other carbohydrates differently, and some legumes, oils and alcohol-containing items differ), so a row is only flagged `kcalVsMacros` when it is off by more than 15 % **and** more than 10 kcal. 53 rows are flagged. Several of these are clearly typos in the printed table (for example AP022 Mi kering: 339 kcal with carbohydrate 6.3 g; DR004 Andewi: 226 kcal; BR033 Umbi Uwi: carbohydrate 82.3 g with 120 kcal). The app shows the printed kcal, not a recalculated one.
- **Other source flags:** `proximateSum` (water + protein + fat + carbohydrate + ash is not 100 g ± 6 g), 23 rows; `implausible:<field>` (one printed value above a physical limit, for example copper 110 mg in apples ER003, which looks like a unit mix-up in the table), 21 values; `macrosOver100` (GP022, GP060); `fibreOverCarb`, 7 rows. In total **92 rows carry at least one flag** (8.0 %). The numbers are still exactly as printed. B-04 decides whether flagged rows are hidden from search; until then treat flagged rows as ⚠ and do not use the flagged field in any calculation.
- **Not in this file:** `name_en` is `null` (English names are only in the PDF index, not parsed); `household` is empty in this file; measures are in `content/household.json` (C-05, §4.6); `halal` and `allergens` are `null` because TKPI does not state them (items with pork, such as KR003 Lemak babi, must be marked by C-11 or B-04 before any halal filter is trusted).
- **Empty cells:** fibre is empty in 220 rows, BDD in 155 rows (the PDF itself says some foods have no BDD). Sodium and other micronutrients are empty where the PDF is empty. TKPI marks imputed and borrowed values in light blue; the colour is not in the text, so the file does not say which values are imputed.

**Hand-check list (3 rows, not in the table):**

| Code | Page | Name | Reason |
| --- | --- | --- | --- |
| ER101 | 53 | Salak, segar | fat cell is empty in the PDF (core value) |
| GP059 | 70 | Dendeng belut, goreng, masakan | one cell is printed as "1,480" (thousands comma); not guessed |
| MP013 | 79 | Melase | protein cell is empty in the PDF (core value) |

**Spot-check (30 random rows, seed 20261118) against the PDF:** each row's values were compared in order with an independent plain-text extraction (`pdftotext -layout`), 30 of 30 matched. Column placement with blanks was also checked by eye on the rendered page 68 (rows GP020 to GP038, including GP034, GP033 and GP022). Rows: AP009, AP039, AP049, AP087, BP070, CP019, CP033, CP034, CP046, DR021, DR101, DR116, DR151, DP003, DP044, ER092, ER097, ER107, FP029, FP066, GR060, GP008, GP034, GP036, GP061, GP095, HP002, HP007, MP008, NR001. The order-only comparison cannot see a value that moved to a neighbouring column, so the sum-to-100 and 4/4/9 checks in the checker are the guard for that.

**Known limits (kendala):** blank cells and the "1,480" cell above; typos and unit slips printed in the table itself (flags above); fibre in TKPI is a mix of total fibre and dietary fibre (PDF note to Table 3), so fibre is only comparable within a row, not across foods; zinc in wheat flour is adjusted for fortification but not in foods made from it (PDF §2.6); vitamin C uses an old titration method; the file does not know which values are imputed (colour only).

### 4.6 Household measures (C-05)

File: `content/household.json`. Built by `node tools/build-household.mjs` (the PMK transcription, the food mapping and the estimates are in that script), checked by `node tools/check-household.mjs`. No `foods-*.json` file is edited.

- **Source:** PMK 41/2014 Lampiran (Rules S17), copy from JDIH Kemenkes, sha256 in the file. The BPK copy still returns HTTP 403. Pages are the numbers printed in the PDF.
- **`pmkRows` (161):** every URT row of the exchange tables as printed: p. 8–9 nasi and staples, p. 10 protein nabati, p. 11–13 protein hewani (golongan A, B, C), p. 14 and 89 vegetables (1 gelas cooked and drained = 100 g), p. 15–16 fruit (net weight), p. 37 sugar, p. 41 fats, p. 89 portion notes. Each row: `urt` as printed, `qty`, `unit`, `grams` for the whole printed amount, `perUnitGrams` (grams / qty, derived), `mappedTo`, `note`. Daging babi is not copied (R_HALAL); ham is copied but not attached.
- **`foods` (151 food ids, 158 measures):** per food, a list of `{code, label, qty, grams, perUnitGrams, display, source, ref | method, flag}`. `display` is the text the app shows, e.g. `¾ gelas (±100 g)`, `1 centong (±100 g)` (R_HH). 140 measures are `pmk-41-2014` (124 PMK rows attached to 138 TKPI rows and `W_NASI_PUTIH`); 18 are `estimate`, `flag = ⚠`, with `method`, `spreadPct` and `gramsRangeEst`.
- **Mapping rule:** a PMK row is attached only to a TKPI row of the same food and state. Where PMK names no variety, the plainest TKPI row is used and the `note` says so (sukun tua, talas bogor, daging sapi lemak sedang, sawo manila, telur bebek tambak). Nasi rows go to cooked rice (AP001, AP005, AP017); the vegetable "1 gelas = 100 g" goes only to the boiled or steamed TKPI rows of vegetables listed in PMK golongan B/C. Other rows go to raw TKPI rows, because PMK does not state raw or cooked (assumption ⚠).
- **Not attached (37 rows):** no TKPI row (havermut, lele segar, bakso, kurma, leci, pear, blewah, mayones, keju krim and others); several TKPI varieties and none chosen (pindang, salak, sagu); unclear item (tepung susu krim, sosis, sardencis); generic reference rows (ikan segar, porsi notes on p. 89); duplicates; and the 3 suspect rows below.
- **PMK conflicts (3):** gula 1 sdm 10 g (p. 37) vs 20 g (p. 89), 10 g used; ikan segar 1 potong sedang 40 g (p. 11) vs 1/3 ekor 45 g (p. 89), not attached; alpokat ½ buah besar 50 g (p. 15) vs 60 g (p. 41), 50 g used.
- **Suspect printed values (3, not attached):** margarin ¼ sdt = 5 g (20 g per sdt); sari kedelai 2½ gelas = 185 g (74 g per gelas); jeruk nipis 1¼ gelas = 135 g (108 g per gelas). Two more look odd but are inside the limits and kept as printed: susu kambing ¾ gelas = 185 g; santan peras 1/3 gelas = 40 g.
- **Estimates ⚠ (18, none weighed):** centong nasi = 100 g (taken equal to the PMK ¾ gelas portion; AP001, AP005, AP017, W_NASI_PUTIH), piring nasi = 200 g (2 centong, same as C-04); tempe goreng 1 potong = 25 g and tahu goreng 1 potong = 50 g (piece size of the raw PMK row; frying loss not measured, tahu likely too high, C-04 uses 30 g); telur dadar 1 butir = 60 g (55 g egg + 5 g oil); ubi kukus 1 biji = 135 g; kecap 1 sdm = 15 g and 1 sdt = 5 g (same as PMK sirup and minyak); yoghurt 1 gelas = 200 g (same as susu); and cooked dishes at the grams used in the meal packages: pepes ikan mas and pepes oncom 1 bungkus = 100 g, ikan patin bakar 1 potong = 120 g, bandeng presto 1 potong = 80 g, gulai ikan 1 mangkok = 150 g (spread ±40 %).
- **Checker:** every food id exists and the name matches; grams 1–1,000 g and inside a per-unit range kept in the checker (for example sdt 1–8 g, sdm 3–25 g, gelas 40–260 g, liquids at least 150 g per gelas); every PMK measure equals its PMK row as printed; every estimate has ⚠, a method and a spread; `display` starts with the label and shows "±" grams; suspect rows and pork are never attached; counts match. It also lists meal-package foods that still have no measure: 6 (ketimun, tomat, selada, sawi raw; kacang tanah rebus; kacang hijau rebus).
- **Still open (§8):** weigh centong, piring, mangkok and the cooked dishes (G7); measures for raw salad vegetables and the 37 unattached PMK rows once TKPI rows exist.

### 4.5 Meal packages (C-11)

File: `content/meal-packages.json`. Built by `node tools/build-meal-packages.mjs` (the package definitions are in that script: foodId + grams only), checked by `node tools/check-meal-packages.mjs`. Totals are never typed; the builder computes them from `foods-tkpi.json` and `foods-estimates.json` (per 100 g x gram).

- **72 packages** = 6 profiles x 4 slots (pagi, siang, malam, camilan) x 3 options. Profiles: region `jawa`, `sunda`, `padang`, `umum`; `beli` (warung and warteg, every package `mode = beli`); `kos` (anak kos: hemat, minim alat, `equipment` lists at most 3 of tanpa, rice-cooker, kompor, panci, wajan). **Milik sendiri** is a hook (`own`: no bundled package; the planner accepts the user's own recipes and foods in all four slots, kcal and macros computed from the table when the user builds it).
- **Per package:** `items` (foodId, grams, `household` {code, label, qty} when the grams equal a whole or half household measure in the table, `main` on the item that counts as the main protein or dish), `kcal`, `protein`, `carb`, `fat`, `fibre` (`fibreComplete = false` when a TKPI fibre cell is empty), `budget`, `halal`, `allergens`, `lactose`, `isEstimate`, `mode`, and `tags` for the planner limits: `santan` (R_LIMIT_SANTAN), `goreng` (a fried main, R_LIMIT_GORENG), `mainProtein` (R_LIMIT_MAIN). Slot kcal here are plain table sums at the listed grams; scaling to the user's target is B-10.
- **Household measures:** estimate rows carry one `porsi`. TKPI measures are in `content/household.json` (C-05, §4.6); the builder reads only `food.household`, so package items of TKPI rows still show grams only until the builder also reads that file (B-10 or a later C-11 run).
- **Budget label (R_BUDGET):** the highest ordinal tier among the items, from `foodTier` in the file (1 Hemat, 2 Standar, 3 Bebas). The tiers are a judgement about relative cost, **not a price and not sourced** ⚠. Rice, tempe, tahu, egg, oncom, vegetables, local fruit, simple gorengan = Hemat; chicken, fish, soto, bakso, sate, dairy = Standar; rendang, nasi padang, beef (rawon), shrimp, avocado, cheese, martabak, boba and sweet iced coffee drinks = Bebas. Result: 46 Hemat, 23 Standar, 3 Bebas. Every `kos` package is Hemat or Standar.
- **Halal (R_HALAL):** no item name or recipe has pork or alcohol words; all 72 are halal.
- **Allergens (R_LIMIT_ALLERGY):** union over the items of the row's `allergens`, a keyword scan of name and recipe, and the TKPI group (H egg, G fish and seafood, J milk). `nuts` means peanut, peanut sauce, melinjo and emping (not mung bean or soy). `lactose` is true for milk, cheese, yoghurt and cream items. Conservative choices: **bread (`TKPI_AP024`) counts as egg and milk** because the table does not list the recipe; **mie kuning counts as egg**. Coconut milk (santan) is not milk.
- **Names:** plain everyday Indonesian, no health claim and no brand. The checker rejects claim words and digits in names.
- **Rules kept (checker):** every foodId exists and the name matches; kcal and macros equal the recomputed values; kcal is within 25 % of 4/4/9 from the macros; no package above 900 kcal; three options per slot per profile; the main items differ between the three options and no two options share more than half of their foods (Jaccard <= 0.5); the cheapest possible day (lowest option in each slot) is at least **1,500 kcal** in every profile, which is the higher of R_FLOOR_F and R_FLOOR_M (PR-5); no flagged TKPI row is used. Cheapest and dearest day per profile: jawa 1,756-1,988; sunda 1,569-1,942; padang 1,766-2,064; umum 1,637-1,870; beli 1,665-1,977; kos 1,657-1,936. The user's own target decides the real amount; the app never goes below the floor (R_GATE_FLOOR).
- **Estimates (R_EST):** 61 of 72 packages contain at least one estimate row and show as "perkiraan". Estimate rows are not yet recomputed from TKPI (§4.1).
- **Assumption ⚠:** a TKPI "segar" or "mentah" row is used by weight for foods eaten boiled or steamed without added fat (bihun, egg, vegetables where a "rebus" or "kukus" row exists is used instead). Weights are as eaten for rice and cooked dishes (estimate rows) and as printed for TKPI rows; the table's raw/cooked state is in its row name.

**Rows needed (butuh baris baru; none invented, packages avoid them):**

| Needed | Why |
| --- | --- |
| Oats / havermut; tauge; selada and lalapan as a prepared mix | Common anak kos and Sunda items, not in TKPI 2020 as eaten |
| Telur rebus (cooked, 1 butir); telur ceplok | Only raw (HR002) and dadar exist; boiled egg is the most common kos item |
| Ikan lele, nila, kembung, tongkol, ayam dada and paha **cooked plain** (rebus, kukus, bakar) | TKPI has them raw or only as named masakan; planner needs cooked weights |
| Daging sapi cooked, tanpa kulit ayam | Same reason |
| Mie instan (kuah and goreng), 1 bungkus | In C-06 (packaged); anak kos staple, left out until label values exist |
| Sambal, tempe bacem, pepes tahu, nasi timbel | Missing as rows (C-07). Nasi merah, pepes ikan mas and pepes oncom have household measures since C-05 |
| Susu kedelai without a source flag | `TKPI_CP060` carries a flag, so it is not used |

**Rows to fix in `foods-estimates.json` (not edited here; another session owns it):** `W_LELE_GORENG` has `allergens = []` although it is fish (the builder adds `seafood`); `W_MIE_AYAM` has no egg although mie kuning usually has it (the builder adds `egg`). If C-04 or C-07 corrects the rows, rerun the builder; nothing else changes.

### 4.3 Packaged label values (C-06)

File: `content/foods-packaged.json`, checked by `node tools/check-foods-packaged.mjs`.

- **2 rows built**, `source = packaged`, both from third-party sites (`sourceType = third-party`, `isEstimate = true`, flag ⚠), each with `sourceUrl` and `readDate` (2026-11-18). **No row is label-verified yet** (`isEstimate = false` needs a producer label or official producer product page).
  - `PK_ULTRA_FULLCREAM_250`: Ultra Milk full cream, 1 kotak 250 ml (150 kkal, protein 8 g, lemak 8 g, karbo 12 g, natrium 50 mg). Source: carbmanager.com. The source's serving is 250 ml, not 200 ml.
  - `PK_OREO_ORIGINAL_27G`: Oreo Original, 27,6 g serving (130 kkal, protein 1 g, lemak 6 g, karbo 20 g, gula 11 g, serat 1 g, natrium 140 mg). Source: Open Food Facts (barcode 8992760221028, sold in India, Indonesia, Singapore). The page shows the table as an image, so the values are as displayed on the page, not checked against the pack.
- **Halal and allergens are `null`** in both rows: no label or site stated them, so nothing is guessed.
- **No brand-logo or pack images** are used.
- **Checked:** the producer sites were read first (Milo, Ultrajaya, Indofood). Milo (`milo.co.id`) returned HTTP 403. The Ultrajaya and Indofood product pages carry no nutrition table, so they gave no values.

**Failed products (no label or source value found; not filled in).** The "about 80" target is not reached: the rest are listed here so the next session can start from them.

| Product (target) | What was found | Why not used |
| --- | --- | --- |
| Indomie Mi Goreng (85 g pack, Indonesia) | Indofood official pages have no table. Third-party values conflict (about 400 vs 193 kkal per 85 g; sodium 649 to 952 mg) | Conflicting, and the Indonesian label is missing |
| Indomie Mi Goreng Spicy Red Curry | Australian listing only (185 g, 415 kkal) | Other market, prepared-serving basis |
| Indomie Goreng Cabe Ijo | Official launch page has no table | No table |
| Milo (Indonesia, per sajian) | `milo.co.id` 403; retailer listings show other sizes and other countries | No Indonesian label |
| Ultra Milk full cream 200 ml | Official Ultrajaya page has no table; the only figure is 250 ml | Serving does not match |
| Ultra Milk full cream, Oman listing (200 ml) | Sugar missing ("?") | Other market, incomplete |
| Teh Kotak Jasmine (Ultrajaya) | Official page has no table | No table |
| Teh Botol Sosro 450 ml | No table for 450 ml; 230 ml and 200 ml figures only | Only extrapolation was possible; not used |
| Chitato Sapi Panggang | Search returned meat and steak pages only | Not found |
| Kecap Bango (per sendok makan 15 ml) | Bango data for 6.2 kg pack per 100 g; sodium 230 mg with no serving stated; sources disagree | No per-serving label |
| Roma Kelapa (Mayora) | Registry listings only, no nutrition data | No table |
| Good Day Cappuccino 25 g | Singapore and Iran listings disagree (carb per serving does not match per 100 g) | Internally inconsistent |
| Oreo Original (Indonesia pack, other than the 27,6 g entry) | Australian and Open Food Facts entries for other sizes | Other sizes only |

Next steps for C-06 (owner or a later session): read the table photo on the Indonesian pack (BPOM label), or the producer's Informasi Nilai Gizi, then write `isEstimate = false` and `sourceUrl` to that page or to a photo note.

### 4.4 Regional and street meals (C-07)

File `content/foods-regional.json`, **108 rows**, all `source = warung-est`, `isEstimate = true`, `halal = true`, each with a `recipe` (ingredient + grams) and a `basis` note. Check: `node tools/check-foods-regional.mjs` (passes: required fields, 4/4/9 within 10 %, no duplicate id or name against `foods-estimates.json` and `foods-tkpi.json`, no pork or alcohol term).

- **Method:** per-100 g values are computed from the recipe with one shared ingredient table (`ingredientReference` in the file: generic per-100 g reference values, **not** TKPI-verified). Portion grams = sum of ingredient grams as eaten. Nothing was copied from a gerai site.
- **Coverage (`region`):** Betawi 4, Sunda 13, Jawa 22, Padang 10, Sumatera 12, Bali 7, Sulawesi 11, Maluku-Papua 2, Kalimantan 3, Nusa Tenggara 5, Warteg 5, Kaki lima 14.
- **Overlap rules:** dishes already in C-04 (for example tempe mendoan, bubur ayam, sate ayam, rawon, bakso, opor ayam) and dishes that are TKPI rows (gado-gado, sayur asem, ketoprak) were left out. Rows that look similar to a C-04 row (nasi uduk with sides, nasi goreng variants, gudeg-free Jawa sets) are regional variants with their own recipe.
- **⚠ (all 108 rows):** ingredient values are generic, to be replaced by TKPI 2020 codes now that `foods-tkpi.json` exists (later step); portion grams are assumed recipes; `sodium` is `null` everywhere (no basis, not invented); mostly liquid sweets and drinks (es teler, es cendol, es kelapa muda) omit water, so per-100 g is overstated.
- **`source = bought` (official gerai nutrition pages with URL and date): 0 rows.** No gerai site was read in this session.
- **Halal (R_HALAL):** left out because they normally contain pork or alcohol: babi guling, sate babi, saksang, babi panggang Karo, lawar babi, sengsu, tuak, arak, brem, cap tikus. Present only as halal variants: lawar nangka (no pork or blood), Bali nasi campur with chicken, rujak cingur without pork.
- **Proposed replacements for `foods-estimates.json` (C-04, not edited):** none yet, no official figure collected. Slots are in `content/foods-regional-proposals.md`.

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
| `xnk-source/figure.min.json` | Muscle map, male and female, front and back | XNK `figure/figure.min.json` (built by `tools/build-figure.js`), shapes from react-muscle-highlighter 1.2.0, MIT |
| `xnk-source/rig.min.json` | 21 animated rig patterns, 13 frames each | XNK `figure/rig.min.json` (built by `tools/build-rig.js`), XNK's own work, joint angles from XNK `docs/program/Gerak.md` |
| `xnk-source/CREDITS.md`, `xnk-source/LICENSE-react-muscle-highlighter.txt` | XNK credits and the MIT licence; ship with the app | XNK |
| `xnk-source/README.md` | Origin note: XNK commit `6dc57b3`, do not edit by hand | — |
| `source.json` | XNK commit and sha256 of both build files; the checker fails if either file changes | — |
| `muscle-map.json` | The 16 muscle codes → figure regions (§5.2) | — |
| `no-rig.json` | Every exercise without a rig, with its reason | — |

The two build files were copied unedited from XNK commit `6dc57b3` (`xnk-source/`, commit 573db10 on this branch). They are byte-identical to the D-05 mockup copies (`docs/mockups/figure-data.js`, `rig-data.js`; same sha256). XNK `Gerak.md` (joint-angle sources of the rigs) was read in C-08b from its copy in `xnk-source/docs/`; summary and mismatches in §5.3.

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

- The figure's `upper back` (middle trapezius and rhomboid area) moves from `LATS` to `TRAPS`: the latissimus does not cover it.
- `QUADS` keeps the figure's `adductors`, because the owner decided (2026-11-18) that `QUADS` stands in for the adductors (`LEGISO_ADD`, §12.2).
- `GLUTES` keeps `abductors` (gluteus medius and minimus abduct the hip, OpenStax 2022 §11.6).

The mockups (`docs/mockups/mock.js`) still map `upper back` to `LATS`; they are not shipped.

Mismatches the figure cannot fix (reported, not failed, by the checker):

| Issue | Effect | Exercises |
| --- | --- | --- |
| One `delts` region for `FRONT_DELT`, `SIDE_DELT`, `REAR_DELT` | The map cannot show which head works; the whole shoulder takes the strongest level | 47 entries name the heads at different levels, e.g. the 13 rows, face pulls and reverse flies with `REAR_DELT` main, the 3 lateral raises (`SIDE_DELT` main) |
| One `abs` region for `ABS` and `OBLIQUES` (XNK merged the package's obliques into abs) | Oblique work shows as front abs | 11 entries: `CORE_SIDEPLANK`, the five carries, `HINGE_SWING_KB`, `PULLH_SA_CBL`, `CORE_PLANK`, `CORE_CRUNCH`, `CORE_DEADBUG` |
| `QUADS` also lights `adductors` (owner's stand-in) | Every quadriceps exercise shows the inner thigh too, e.g. `LEGISO_EXT`, where the adductors do little; hip adduction shows the front thigh too. A proper fix is an `ADDUCTORS` code (owner) | All 39 entries with `QUADS` |
| Front-view rig `lateral-raise` has no trapezius mark | `TRAPS` helper shows on the map only, not on the rig | `PUSHV_LAT_DB` |
| XNK rig's own muscle list differs from the library | Ignored by rule; the library wins | 14 of 20 rigged entries (checker output), e.g. `seated-cable-row` rig: upper back main, library: `LATS`, `TRAPS`, `REAR_DELT` main |

Fixing the first two needs new shapes: split `delts` into three heads and keep `obliques` in the XNK figure build (`build-figure.js`), then re-copy. That is an XNK change; listed in C-08b.

### 5.3 Joint angles of the 20 reused XNK rigs (C-08b, from `Gerak.md`)

Read from `content/figures/xnk-source/docs/Gerak.md` (unedited copy of XNK `docs/program/Gerak.md`) and checked against the frames in `rig.min.json` (first and last frame). Convention (Gerak.md): knee and elbow 0° = straight; hip flexion from the trunk line; shoulder 0° = arm at the side, 180° = overhead; ankle 0° = foot at 90° to the shin. "Choice" = Gerak.md itself says the number is a drawing choice, not a study value.

| Exercise | Rig | Animated angles (rig frames) | Source in Gerak.md | Mismatch with rig or `exercises.json` |
| --- | --- | --- | --- | --- |
| `LEGISO_EXT` | `leg-extension` | Knee 100° → 30° | Pedrosa 2021, PMID 33977835 (full ROM 100–30°) | — |
| `LEG_PRESS` | `leg-press` | Knee 100° → 5°, sled 45° | Larsen 2025, PMID 40113586 | — |
| `HINGE_HT_MC` | `hip-thrust-machine` | Hip 73° → 0° | McCurdy 2021, PMID 33900255 (top position); bottom angle computed from bench and feet | — |
| `PULLV_PULLDOWN` | `lat-pulldown` | Shoulder 163° → −5°, elbow 10° → 153° | Di Fonza 2026, PMID 42647355 (bar in front) | Shoulder and elbow numbers are not in the review: choice |
| `PULLH_CBLROW` | `seated-cable-row` | Shoulder 86° → −27°, elbow 5° → 139° | Di Fonza 2026 (narrow grip, low abduction) | Angles not in the review: choice |
| `PUSHH_PECDEC` | `chest-fly-machine` | Horizontal adduction, elbow 15° fixed (side view shows the arm shortened) | Solstad 2020, PMID 33239937 | — |
| `PUSHH_PUSHUP_INCL` | `incline-push-up` | Elbow 4° → 95° | Kang 2019, PMID 31584855 (elbow straight at top); bottom = chest near support | Rig support is bench height; entry says wall or chair (C-08 note) |
| `PUSHH_PUSHUP` | `push-up` | Elbow 4° → 95° | Kang 2019 | — |
| `LEGISO_CURL_SEAT` | `seated-leg-curl` | Hip 90°, knee 10° → 100° | Maeo 2021, PMID 33009197 (seated > prone) | Gerak.md: 10–100° is the machine on the drawing, not the study. Maeo 2021 full text (PMC7969179) used knee 0–90° with hip ~90°: the rig range is 10° off at both ends ⚠ (XNK change, not ours: file is locked) |
| `CURL_CABLE` | `bicep-curl-cable` | Elbow 2° → 135°, arm at the side | Pedrosa 2023, PMID 36828324 (0–135°); Attarieh 2025, PMID 40082069 (shoulder angle does not change growth) | Pedrosa 2023 measured a seated dumbbell **preacher** curl (shoulder 45°, full text PMC9960616); the standing arm-at-side pose rests on Attarieh 2025 |
| `TRI_PUSHDOWN` | `triceps-pushdown` | Elbow 90° → 2° | Maeo 2023 (Eur J Sport Sci 23:1240), PMID 35819335 (cable, 90–0°, neutral arm) | Gerak.md writes "Maeo 2022" (online 2022; issue 2023) |
| `LEGISO_CALF_STAND` | `calf-raise-machine` | Ankle −25° → +25° | Kassiano 2023, PMID 37015016 | The study used a horizontal leg-press machine (abstract); the rig stands. Same ankle and knee (straight) |
| `SQUAT_GOB` | `goblet-squat` | Knee 5° → 100° (hip 9° → 94°, ankle 2° → −26°) | Larsen 2025; Kubo 2019, PMID 31230110 | Hip and ankle follow from the drawing (trunk lean): choice |
| `PUSHV_LAT_DB` | `lateral-raise` (front) | Shoulder abduction 4° → 90° | Larsen 2025 (Front Physiol), PMID 40692697: muscle only | Angles: choice |
| `LEGISO_ABD` | `hip-abduction-machine` (front) | Hip abduction 6° → 40° | Attarieh 2026, PMID 42842490: muscle only | Angles: choice |
| `PUSHH_BENCH_DB` | `db-bench-press` | Elbow 4° → 152°, shoulder 88° → 18° | Uribe 2010, PMID 20300023; Patterson 2015, PMID 26528421 | Angles: choice |
| `PUSHV_DB` | `db-shoulder-press` | Elbow 155° → 6°, seated | Kohler 2010, PMID 20072068 (seated); Uribe 2010 | Rig is seated with a back rest; entry lists dumbbells only (decision in Decisions.md, C-08b) |
| `PULLH_BO_DB` | `db-row` | Trunk 68° from vertical (hip 78°), knee 20°, elbow 4° → 138° | Di Fonza 2026 | Angles: choice; trunk 68° matches the frames |
| `HINGE_RDL_DB` | `db-rdl` | Knee 15° fixed, hip 79° → 11° | Hermann 2026, PMID 42516618: exercise only | Gerak.md says "to upright"; the last frame stops at hip 11°. Angles: choice |
| `CORE_DEADBUG` | `dead-bug` | Hip 90° → 25°, knee 90° → 0°, shoulder 90° → 150° | Jeon 2026, PMID 42960974; Kim 2016, PMID 27317910: muscle only | Angles: choice |

Summary: 8 of the 20 rigs animate a range taken from a study (leg extension, leg press, hip thrust top, push-ups, biceps curl, triceps pushdown, calf raise, goblet squat knee); 12 use Gerak.md's own drawing choices for start and end positions, which Gerak.md says openly. One study range is not followed (`seated-leg-curl`, 10–100° vs 0–90°). `squat-machine` (the 21st XNK rig) matches no library entry.

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
| 13 | `LOGWEEK_1` | Minggu rajin mencatat | A week with food logged on at least 5 days |
| 14 | `MEASURE_4` | Empat kali ukur | 4 scheduled measures |
| 15 | `PHOTO_FIRST` | Foto pertama | First progress photo (kept on the phone) |
| 16 | `BACK` | Kembali lagi | Back after 14+ days away, workout finished |
| 17 | `LEVEL_3` | Level 3 | Reached level 3 |
| 18 | `PLAN_WEEK` | Rencana seminggu | Week planner full for 7 days |

Built in C-10 (2026-11-18): [`content/badges.json`](../content/badges.json) holds the 18 codes (stable IDs, stored in `badge.code`), Indonesian titles, machine-readable conditions, one celebration message each, and an icon name from the mockup set. Checked by `node tools/check-badges.mjs` (exactly 18, no duplicate code, title or order, required fields, tone rules).

- **Conditions** use eleven named metrics defined once in the file (for example `finishedWorkouts`, `personalRecords`, `streakWeeks`, `activityMinutesWeek`), each computed from Room tables in Design §3: `workout`, `workout_set`, `activity`, `food_log`, `points_event`, `measure`, `photo`, `meal_plan`. They are deterministic and can be unit-tested from fixture rows.
- **Personal record** (not a table in Design §3): derived from `workout_set`. A done set beats every earlier set of the same exercise by load, or by reps at equal load, or by reps for bodyweight. The first set of an exercise is a baseline. One record per exercise per workout.
- **Earned once, never lost.** Badges never depend on weight, calories eaten below target, or very low intake. The activity badges stop at 300 minutes a week (WHO upper range, S13); there is no volume badge above it (PRD PR-5). `LOGWEEK_1` (was `TARGET_WEEK`; owner decision 2026-11-18) counts weeks with food logged on at least 5 days and never looks at the amounts, so no badge depends on a calorie number. `MEASURE_4` counts measuring, never the value.
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
| C-05 | Household measures: PMK 41/2014 weights in `content/household.json` (§4.6); centong, piring and cooked dishes are 18 estimates ⚠ until weighed | Weigh at home or find a published table |
| C-06 | Packaged food label values: 2 rows built (third-party, ⚠); about 80 not reached, 13 products failed (§4.3) | Label photos or producer tables needed; owner or a later task |
| C-04 | Warung and bought-meal estimates: 66 rows built in `content/foods-estimates.json` (see §4.1) | Done; 10 rows ⚠ |
| C-07 | Bought and regional meal numbers (wider set; replace estimates where a label or official site exists) | Partly done: 108 flagged estimates (§4.4); 0 official `bought` rows yet (PRD §14 item 6) |
| C-01 | Muscle review done 2026-11-18 (§12): 57 entries `verified`, 65 `analog`, 27 `unverified` (praktik umum ⚠). 21 entries had muscles changed | Remaining ⚠ need a study or owner decision |
| C-01b | 34 entries still `review.muscles = "uncertain"` ⚠, each with its reason in `review.note` (§12.3). Owner decided 2026-11-18: `LEGISO_ADD` keeps QUADS as a stand-in; `PULLV_DB_PULLOVER` stays `PULL_V` | Later research |
| C-08 | Figures reused from XNK (§5.1): 20 exercises with a rig, 129 with a written reason; every exercise has the muscle map. XNK commit `6dc57b3` | Done 2026-11-18; rest in C-08b |
| C-08b | Rigs still to draw with sourced joint angles: 67 `derive`, 55 `new`; owner to confirm 7 cardio `map-only` and `PUSHV_DB` seated rig; read XNK `Gerak.md` for the 20 reused rigs; split delts and obliques in the XNK figure build (§5.2) | Needs XNK repo access and a joint-angle source per rig |
| C-09 | English cues for each exercise (3 lines): all 149 drafted by Claude in its own words, none owner-reviewed | Owner review of English (PRD §14 item 3) |
| C-11 | Meal packages built (§4.5): 72 packages, own hook, 7 row needs and 2 row fixes listed there; budget tiers ⚠ | Owner may reorder tiers in `foodTier` (builder) |
| C-10 | Level ↔ exercise mapping for variants | Set when the pattern list is final |

## 9. Sources for exercises and figures

| Item | Source | Status |
| --- | --- | --- |
| Pattern list (§3) | Standard resistance-training pattern classes, ACSM Position Stand 2009 (Rules S14) | ✅ class list; exercise picks ⚠ review |
| Main and helper muscles per exercise | `source.muscles_cues` per entry: EMG studies, biomechanics studies or an anatomy text (list in §12.4) | ✅ 57 verified, ✅/⚠ 65 analog, ⚠ 27 unverified; 34 uncertain (§12.3) |
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
| 2026-11-18 | C-11: `content/meal-packages.json` (72 packages, 6 profiles, own hook), `tools/build-meal-packages.mjs`, `tools/check-meal-packages.mjs` (§4.5). |
| 2026-11-18 | C-02: pattern check passes (minimum 3 per required pattern and setting; CI wiring left to B-01). |
| 2026-11-18 | C-01: `content/exercises.json` with 149 entries and `tools/check-exercises.mjs`. `variantOf` points to the easier variant. |
| 2026-11-18 | C-01 muscle review (Opus): sources per entry, 21 muscle changes, 1 hint change, statuses `verified` / `analog` / `unverified`, file version 2026-11-18.2 (§12). |
| 2026-11-18 | Owner decisions: `LEGISO_ADD` keeps QUADS (stand-in for adductors); `PULLV_DB_PULLOVER` stays `PULL_V`. |
| 2026-11-18 | C-06: `content/foods-packaged.json` with 2 third-party rows (⚠, isEstimate true), `tools/check-foods-packaged.mjs`; 13 failed products listed in §4.3; target about 80 not reached. |
| 2026-11-18 | C-08: XNK muscle map and rig copied to `content/figures/` (XNK commit `6dc57b3`, credits kept, sha256 locked); `rig` field added to `exercises.json` (20 rigged, 129 with reason in `no-rig.json`); upper back moved to TRAPS in the muscle map; `tools/check-figures.mjs` (§5.1, §5.2). |

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
| 2026-11-18 | C-07: 108 regional, street and warteg rows as flagged estimates (§4.4); no official bought rows. |
