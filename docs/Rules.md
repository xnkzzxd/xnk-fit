# Rules — XNK Fit

| | |
| --- | --- |
| Status | Draft, 2026-11-16 (task D-03). Sources opened and checked 2026-11-18 (task S-07); see §10 and §13. |
| Reads | [PRD.md](PRD.md) (what), [Design.md](Design.md) (how). |
| Rule | PR-6: every number has a written source. A rule without a verified source is marked **⚠** and is **not final**. Code reads each number from one named constant; the rule ID in the table is the constant's name prefix. |

Each rule has: **ID**, the value, the **source** (or "product rule" for owner choices), and status. Status: ✅ source checked, ⚠ source still to verify (the value is a working default), 👤 owner decision.

Sources are listed in §13. Where a source is cited by short code (for example `S3`), see that list.

## 1. Body and energy

| ID | Rule | Value | Source | Status |
| --- | --- | --- | --- | --- |
| R_MSJ_M | BMR men (kcal/day) | 10·kg + 6.25·cm − 5·age + 5 | S1 | ✅ |
| R_MSJ_F | BMR women (kcal/day) | 10·kg + 6.25·cm − 5·age − 161 | S1 | ✅ |
| R_ACT | Activity factor (PAL) from weekly activity (PRD L-14) | Jarang bergerak 1.40 · ringan (1–2 days) 1.55 · sedang (3–4) 1.70 · aktif (5–6) 1.85 · sangat aktif (7 days or physical work) 2.00. Was 1.2 / 1.375 / 1.55 / 1.725 / 1.9 (no primary source). Screen says "perkiraan" | Values inside S2 Table 5.3 PAL bands (1.40–1.69 / 1.70–1.99 / 2.00–2.40); 1.40 is S2's lower limit (§5.3.2: 1.21 "too low"). Mapping to days per week is a product rule. Check against S8: AKG energy for the reference adult ÷ Mifflin BMR ≈ 1.73 (men 19–29) and 1.79 (women 19–29) | ✅ values (S2), 👤 mapping (owner approved 2026-11-18) |
| R_TDEE | Maintenance = BMR × activity factor. Shown; the target uses it, activity calories are **not** added on top (M-1) | — | product rule (M-1) | 👤 |
| R_AGE | Age from birth year: current year − birth year (BMR, fibre, display). The age gates R_GATE_U13 and R_GATE_U18 use the **youngest possible age**, one year less, because the birthday may not have passed (2026-11-18, Decisions.md; safe side) | margin 1 year | product rule | 👤 |
| R_BMI | BMI = kg / m² | — | S3 | ✅ |
| R_BMI_UNDER | Goal "turun" not offered below BMI | < 18.5 | S3 (WHO international cut-off kept, p. 157); PRD S-5 | ✅ |
| R_WHTR | Waist-to-height ratio, shown in Bantuan only | ≥ 0.5 flagged as "perhatikan" (kind wording) | S4 (boundary 0.5, p. 303) | ✅ |
| R_INPUT | Plausibility limits for typed values. Outside them the value is treated as a typo: **no target and no program** (fail-safe, PR-5) | Height 100–250 cm · weight 25–300 kg · waist 40–250 cm · age ≤ 100 | product rule (B-02). Not health limits; they only catch typos (170 typed as 1.7) | 👤 |

## 2. Calorie target and macros

| ID | Rule | Value | Source | Status |
| --- | --- | --- | --- | --- |
| R_GOAL_TURUN | Deficit for "turun" | −500 kcal/day (≈ 0.5 kg/week) | S5: deficit 500–1,000 kcal/day for 1–2 lb (0.45–0.9 kg) per week, p. 26; −500 is the low end | ✅ |
| R_GOAL_NAIK | Surplus for "naik" | +300 kcal/day | S19: surplus ≈ 10–20 % and gain ≈ 0.25–0.5 % of body weight per week. +300 is 10–20 % of maintenance from 1,500 to 3,000 kcal | ✅ |
| R_GOAL_JAGA | "jaga" (maintain) | 0 | product rule | 👤 |
| R_DEF_CAP | Safety cap: deficit never more than −10 % of maintenance for the "other safety" group (PRD S-7). That group is anyone with a risk answer (R_GATE_RISK); the other gates already allow no deficit | ≤ −10 % | owner rule, PRD S-7 | 👤 |
| R_FLOOR_F | Calorie floor women (PRD S-4) | 1,200 kcal/day | owner caution; S5 low-calorie diet for women 1,000–1,200 kcal/day (p. 26), so the floor is at the top of that range | 👤 ✅ |
| R_FLOOR_M | Calorie floor men (PRD S-4) | 1,500 kcal/day | owner caution; S5 low-calorie diet for men 1,200–1,600 kcal/day (p. 26), so the floor is inside that range | 👤 ✅ |
| R_FLOOR_BMR | Target is never below BMR (PRD S-4) | target ≥ BMR | owner caution | 👤 |
| R_PROT | Protein target | 1.6 g per kg body weight; floor 1.4, cap 2.0. Steps down to 1.4 only when the target is too small to leave room for carbohydrate | S6 (range 1.4–2.0 g/kg) | ✅ |
| R_FAT | Fat | 25 % of target kcal, floor 0.8 g/kg | S7: AKG 2019 adult rows give 24.7–26.0 % of energy from fat; S18 15–30 %; floor 0.8 g/kg is inside S19's 0.5–1.5 g/kg | ✅ |
| R_CARB | Carbohydrate | Remaining kcal (never below 0) | arithmetic | ✅ |
| R_FIBRE | Fibre target (shown, not enforced) | AKG 2019 value for the user's sex and age group (table below). Was 30 g/day | S8, Tabel 1, pp. 7–8 | ✅ (owner approved 2026-11-18) |
| R_ROUND | Calorie target rounded to 10 kcal (halves up). When a floor raises the target, the floor is rounded **up** to the next 10 kcal so the target never ends below BMR | — | product rule | 👤 |
| R_KCAL_G | kcal per gram: protein 4, carbohydrate 4, fat 9 | 4 / 4 / 9 | S9 | ✅ |
| R_RECALC | Ask to recalculate the target (PRD M-2, Design §7) when weight differs from the weight used by | ≥ 3 kg | product rule (PRD M-2) | 👤 |

Fibre by sex and age (S8 Tabel 1, g/day). The fixed 30 g matched only women 30–49 and men 50–64; AKG fibre is about 14 g per 1,000 kcal of the AKG energy value, so it does **not** fall with a deficit and is read from the table, not from the target.

| Age | 13–15 | 16–18 | 19–29 | 30–49 | 50–64 | 65–80 | 80+ |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Laki-laki | 34 | 37 | 37 | 36 | 30 | 25 | 22 |
| Perempuan | 29 | 29 | 32 | 30 | 25 | 22 | 20 |

Macro grams: protein 4 kcal/g, carbohydrate 4 kcal/g, fat 9 kcal/g (S9).

Activity calories (L-14): shown, **never added** to the target (M-1). Activity calories = MET × body kg × hours (§4).

## 3. Safety gates

Applied in this order before any program or target is shown (PR-5, Design §7): R_GATE_U13 → R_INPUT → R_GATE_SCREEN → R_GATE_RISK → R_GATE_U18 → R_GATE_PREG → R_GATE_ED → R_GATE_BMI → R_GATE_FLOOR. The first three end with **no target and no program**. Code: `domain.rules.evaluateTargets` (B-02).

| ID | Gate | Effect | Source | Status |
| --- | --- | --- | --- | --- |
| R_GATE_U13 | Age under 13 (from birth year) | App explains it is for 13 and up and does not continue; no target, no program | PRD O-5 (owner rule). Added in B-02: O-5 had no Rules ID | 👤 |
| R_GATE_SCREEN | Full screening not done (any of the seven questions unanswered; "Hamil atau menyusui?" may stay "—" for men only) | No program, no target; screening opens | PRD O-2, S-1 | 👤 |
| R_GATE_RISK | Health question "yes" (risk answers: heart or blood pressure, chest pain on activity, injury still felt, other treated condition, restriction from a doctor) | Lighter program (§5 level 1), text: "Konsultasi dokter sebelum latihan" | PRD S-1 | 👤 |
| R_GATE_U18 | Age under 18 | Light mode, technique-first, **no deficit** (goal "turun" changed to "jaga") | PRD S-2 (owner rule). Support: S10 (ages 5–17 average 60 min/day, muscle and bone strengthening ≥ 3 days/week, p. 1); S8 energy for 16–18 (2,650 / 2,100 kcal) is at or above adult values. No source states "no deficit"; it is the owner's safe-side rule | 👤 |
| R_GATE_PREG | Pregnancy **or breastfeeding** (self-reported, one question "Hamil atau menyusui?", as the screening mockup) | **No deficit**; text advising a doctor | PRD S-3 (owner rule). Support: S8 Tabel 1 p. 8, pregnancy adds +180 kcal (trimester 1) and +300 kcal (trimesters 2, 3); S13 p. 6 (≥ 150 min/week moderate activity without contraindication); S11 abstract ; breastfeeding: S8 p. 8 adds +330 kcal (first 6 months) and +400 kcal (second 6 months). S21 allows a 500 kcal/day deficit for overweight women once lactation is established; the app stays on the safe side (no deficit) and the text points to a doctor or midwife | 👤 (owner left the choice to Claude, 2026-11-18) |
| R_GATE_ED | History of eating disorder | Maintain target (no deficit), **no-numbers mode**: calories hidden, meal plan and ticks only, note to talk to a doctor | PRD S-6 (owner rule). Support: S20 (105 patients with an eating disorder: about 75 % used a calorie tracker, 73 % of them felt it contributed to their symptoms, p. 14). No guideline sets a "no-numbers mode"; it is the owner's safe-side rule | 👤 |
| R_GATE_BMI | BMI < 18.5 | No "turun" option (R_BMI_UNDER) | PRD S-5; S3 | ✅ |
| R_GATE_FLOOR | Target below floor | Raised to the floor (R_FLOOR_*) and note shown | PRD S-4 | 👤 |
| R_PACE | Fat-loss pace: loss faster than 1 % of body weight per week for two weeks. Read on the last three weekly weights: both weeks show a loss and the average over the two weeks is more than 1 % of the first weight per week; a missing week gives no suggestion | Suggests raising calories (+100 kcal), user decides (M-2) | PRD S-8; S18 (loss of about 0.5–1 % of body weight per week, p. 1) | 👤 ✅ |
| R_DISCLAIMER | "Bukan pengganti dokter / ahli gizi" text on target and program screens | Always shown once in Bantuan and on the target card | PRD S-7 | ✅ text |

## 4. Activity (MET)

| ID | Rule | Status |
| --- | --- | --- |
| R_MET | Activity calories = MET × body kg × hours, MET values in the table below (S12). Activity with no vigorous value uses the moderate value | ✅ |
| R_WHO_MIN | Weekly minutes: target 150, up to 300 moderate-equivalent; 1 vigorous minute = 2 moderate (S13) | ✅ |

Calories from activity = MET × body kg × hours (S12). Values used:

| Activity code | MET (moderate effort) | Vigorous MET | Source | Status |
| --- | --- | --- | --- | --- |
| `jalan` (walking, brisk) | 3.5 | — | S12 | ✅ |
| `lari` (running, ~8 km/h) | 8.3 | 11.0 (~11 km/h) | S12 | ✅ |
| `sepeda` (cycling, leisure) | 6.8 | 8.0 (vigorous) | S12 | ✅ |
| `renang` (swimming, laps) | 5.8 | 9.8 | S12 | ✅ |
| `angkat` (weight training, general) | 3.5 | 6.0 | S12 | ✅ |
| `senam` (aerobics, general) | 6.5 | 7.3 | S12 | ✅ |
| `bulutangkis` (badminton, recreational) | 5.5 | 7.3 | S12 | ✅ |
| `sepak` (football, casual) | 7.0 | 10.0 | S12 | ✅ |

The list grows in Content.md; each new code needs a row here.

WHO minutes (L-14, PRD): target 150 minutes moderate per week, up to 300 (S13). **Vigorous minutes count double** (1 min vigorous = 2 min moderate; S13). Any logged activity counts toward the weekly minutes. Steps (L-15) do not count toward minutes or points.

Weekly activity target shown to non-lifters (H-2): 150 min moderate-equivalent per week.

## 5. Training

| ID | Rule | Value | Source | Status |
| --- | --- | --- | --- | --- |
| R_TRN_WEEK | Strength sessions per week by level | Beginner 2–3 (full body), intermediate 3–4 (3 full body or 4 upper/lower split) | S14 Table 2, p. 700 | ✅ |
| R_TRN_SETS | Working sets per exercise | Level 1: 2–3. Level 2 and up: 3–4. Was "2–3 rising to 3–4 after 4 weeks" | S14 Table 2, p. 700: novice 1–3 sets; multiple sets for progression | ✅ (owner approved 2026-11-18) |
| R_TRN_REPS | Rep range | Level 1 weighted lifts: 8–12. Level 2 and up: 3–5 (strength) or 8–12 (hypertrophy). Bodyweight and bands: 10–15 at every level. Was "3–5 / 8–12" with no level split | S14 p. 687 and Table 2, p. 700: novice 8–12 RM at 60–70 % 1 RM; heavy 1–6 RM for intermediate and advanced; 10–15 reps for endurance | ✅ (owner approved 2026-11-18) |
| R_TRN_REST | Rest between sets | 60–90 s isolation; 2–3 min compound (default) | S14 Table 2, p. 700 (core lifts ≥ 2–3 min, assistance 1–2 min); S15 (3 min beat 1 min, p. 1805) | ✅ |
| R_TRN_RIR | Target effort: 1–3 reps in reserve for most sets | Taught in Bantuan (glossary) | S16 | ✅ |
| R_TRN_PROG_UP | Upper body load increase when top of range hit with form "rapi" | +2.5 kg, limited by R_TRN_PROG_STEP | product rule, common practice | 👤 |
| R_TRN_PROG_LEG | Legs load increase | +5 kg, limited by R_TRN_PROG_STEP | product rule | 👤 |
| R_TRN_PROG_STEP | Load step limit when the top of the range is hit | Step = the smaller of the fixed step and 10 % of the current load, then rounded **down** to the smallest step the user's equipment allows (default 1 kg for dumbbells, 2.5 kg for barbell/machine). If even that smallest step is above 10 %, it is proposed only after the top of the range + 2 reps on all working sets | S14 p. 687 (+2–10 % when 1–2 reps over the target); owner approved 2026-11-18 | ✅ |
| R_TRN_VOL | Weekly working sets per muscle group (16 groups, Content.md §5) | Level 1: at least 5. Level 2 and up: at least 10. Upper limit 20 for every level | S22 (graded dose-response; categories < 5, 5–9, ≥ 10 sets per muscle per week, p. 1073); upper limit is a product rule (S23: diminishing returns) | ✅ (limit 20 👤) |
| R_TRN_VOL_COUNT | How sets are counted for R_TRN_VOL | Main muscle of the exercise = 1 set; each helper muscle = 0.5 set. Warm-up sets do not exist (R_TRN_NO_WARMUP) | S23 ("fractional" counting fit best, p. 481) | ✅ |
| R_TRN_VOL_SCOPE | Groups with a minimum | All except `FOREARM`, `LOWER_BACK`, `OBLIQUES`, `TRAPS`: these are counted and shown, with no minimum (they get helper sets from compound lifts) | product rule | 👤 |
| R_TRN_VOL_FILL | When a group is under its minimum | The generator first adds sets within R_TRN_SETS, then one accessory from a valid pattern. If the equipment cannot reach it, it says so plainly (as L-3) and never fills with a wrong pattern. Over 20: the lowest-priority accessory set is removed | product rule (L-3, L-17) | 👤 |
| R_TRN_PROG_BW | Bodyweight / limited weights progression: reps to top → +1 set → next harder variant | — | product rule (L-2) | 👤 |
| R_TRN_BAND | Bands logged as band level + reps (no kg) | Levels 1–5 | product rule (L-2) | 👤 |
| R_TRN_FORM | "Ragu" holds load until two "rapi" in a row | 2 | product rule (L-12) | 👤 |
| R_TRN_REVIEW | Automatic review of the program | Every 4 weeks or after 3 completed sessions | product rule (L-4) | 👤 |
| R_TRN_RETURN | Return after absence | ≥ 14 days: 60 % of last loads (rounded down to the equipment step), one fewer set (never below one) | product rule (L-16). No source found that sets a return load (G9) | 👤 ⚠ |
| R_TRN_LEVEL1 | Gate for "risk" answers | Level 1 programs only (§3) | PRD S-1 | 👤 |
| R_TRN_NO_WARMUP | No warm-up sets in programs or the logger | — | PRD L-7 | 👤 |
| R_TRN_LEVELS | Level 1 lasts the first 8 weeks unless the sign-up experience sets a higher start level (note below) | 8 weeks | product rule (Design §6). ID added in B-02: the number had none | 👤 |
| R_TRN_EQUIP | Equipment each fixed setting assumes (Content.md §2) | `home-bw`: floor (`none`, `mat`), wall, stairs, chair; no table, door, towel, bar or rope. `home-db`: the home items + dumbbells and bands; the "optional" pull-up bar, a bench and a kettlebell are not assumed. `gym`: all gym codes. `custom`: floor items + what the user ticks, from any setting. An exercise is offered only when every item it lists is there | Content.md §2 (B-06 reading) | 👤 |
| R_TRN_EDIT | Limits of the own-routine editor (typo guards only; the user's numbers win, L-5). Start values for an exercise added by hand use R_TRN_SETS, R_TRN_REPS and R_TRN_REST | Sets 1–10 · reps up to 50 · rest 0–600 s in steps of 15 | product rule (B-08), not a health limit | 👤 |
| R_JEL_SHELF | Jelajahi shelf (L-9): full body 3× gym, upper / lower 4× gym, home bodyweight 3×, home dumbbell 3×, each 45 min, built by the same generator (a level 1 user gets at most the days R_TRN_WEEK allows) | 4 cards | PRD L-9 | 👤 |
| R_MAP_TONE | Weekly muscle map tone (L-11): none, low below the weekly minimum of the program's level (R_TRN_VOL), mid inside the range, high above R_TRN_VOL_MAX; every tone also shown as text | 3 tones | product rule (B-08) from R_TRN_VOL | 👤 |
| R_ACT_MINUTES | One activity entry is 1–600 minutes (typo guard; light effort has no MET and counts no minutes, "Lainnya" uses the walking MET) | 1–600 min | product rule (B-08) on R_MET, R_WHO_MIN | 👤 |
| R_TRN_GEN | How the generator builds a week (Design §6) | Exercises per session by length: 30 min → 4, 45 → 5 (mockup), 60 → 6. Full body A/B/C for 2–3 days; upper/lower split only for 4 days at level 2+. Main exercise = first library entry the level and equipment allow (the library lists the base lift first); accessory = the entry whose main muscles overlap least with the main's (library order on a tie). Level 1 starts at 2 sets (bottom of R_TRN_SETS). Rest: compound 120 s, 180 s for strength work, isolation 60 s. Cardio and carries are not put in generated programs. Day estimate = sets × (rest + 45 s), for the "±N menit" label only. Rules version 1 | product rule (B-06); estimate 45 s is not a training number | 👤 |
| R_TRN_LOGGER | Logger readings (B-07, Design §4) | Set kinds: normal, drop, sampai batas (no warm-up, R_TRN_NO_WARMUP). Held exercises `CORE_PLANK`, `CORE_SIDEPLANK`, `CORE_HOLLOW` are logged in seconds (stepper 5 s, first hold 20 s); other work in reps. Rest bar "+15 dtk". Rest is skipped between the exercises of a superset and taken after the last one. Unfinished workout from today or yesterday → "Lanjutkan latihan?"; older → closed at its last done set. Field limits (typo guard, not training limits): reps 0–100, hold 0–600 s, load 0–500 kg. A planned set is prefilled with the suggestion, else the same set last time, else the bottom of the rep range; kg is never invented | product rule (B-07); first hold 20 s is a starting value the user changes, not a training number | 👤 |

Note on R_TRN_PROG_STEP: example, a 12 kg dumbbell press: 10 % is 1.2 kg, so the step is 1 kg (not 2.5 kg). A 40 kg squat: 10 % is 4 kg, so the step is 2.5 kg (not 5 kg). From 50 kg (legs) and 25 kg (upper) the fixed steps apply unchanged.

Note on R_TRN_VOL: example, level 1, full body 3 days, goblet squat 3 sets: QUADS +3, GLUTES +1.5 per session, so 9 and 4.5 per week. The weekly muscle map (L-11) shows sets per group against the minimum.

Levels: 1 beginner (first 8 weeks, or sign-up experience), 2 intermediate, 3 advanced. The sign-up experience sets the starting level (Design §6).

## 6. Food rules

| ID | Rule | Value | Source | Status |
| --- | --- | --- | --- | --- |
| R_HALAL | Halal by default: no pork or alcohol in bundled foods | — | product rule (M-9) | 👤 |
| R_LIMIT_SANTAN | Santan (coconut milk) in automatic options | ≤ 1 per day, ≤ 3 per week | product rule (planner, M-4), from XNK nutrition rule set | 👤 |
| R_LIMIT_GORENG | Fried main in automatic options | ≤ 1 per day | product rule | 👤 |
| R_LIMIT_MAIN | Same main protein twice in a day | not allowed in automatic options | product rule | 👤 |
| R_LIMIT_ALLERGY | Allergy filters (egg, seafood, nuts, milk), lactose | removed from automatic options | PRD M-9 | ✅ |
| R_BUDGET | Budget preference: Hemat / Standar / Bebas (no Rupiah amounts) | Option weight in automatic choice only: cost 300 per tier above the preference (more than the repeat cost, so a budget is kept before variety), 20 per tier below (weights written in B-10 without a source, see Decisions.md "aturan planner perlu review Opus") | PRD M-9 | 👤 |
| R_MEAL_SLOTS | Slots: pagi, siang, malam, camilan | — | product rule | 👤 |
| R_OPTIONS | Meal options per slot | 3 | PRD M-3 | ✅ |
| R_HH | Household measures shown first; grams beside, with "±" (format "1 centong (±100 g)") | Measure weights from PMK 41/2014 (S17) where printed (140 measures, `content/household.json`); centong, piring, fried and cooked dishes are our estimates with a written method (18, ⚠) | S17 p. 8–16, 37, 41, 89; estimates flagged | ✅ PMK part; ⚠ estimates (G7) |
| R_EST | Estimates (warung, bought, own cooking) are flagged `isEstimate` and labelled "perkiraan" | — | PRD PR-6 | ✅ |
| R_LOG | Food log: kcal and macros are copied into the row when it is written (to 0.1) and never recomputed; quick-add stores kcal only; a copied meal or day keeps the stored values; "Favorit" = a food logged 3 or more times | — | Design §3, §7; PRD M-7 | 👤 |
| R_PLAN_FLOOR | A planned day never adds up to less than the calorie floor (R_FLOOR_F or R_FLOOR_M, never below BMR). If no combination reaches it, the highest one is used and the day is marked | floor of R_GATE_FLOOR | PR-5; Design §7 | 👤 |
| R_PLAN_FIT | Automatic choice per day: the combination of one option per free slot that respects R_LIMIT_* and lies nearest the daily target, after budget, repeat and "buy" costs; portions are not scaled | 6 candidates per slot; tie-break from package id and week seed | planner rule, first version (B-10) | ⚠ |
| R_PLAN_REPEAT | The same package at most twice a week in automatic choice; each earlier use adds a cost of 150 | 2 per week; cost 150 | planner rule, first value without a source (B-10) | ⚠ |
| R_PLAN_BUY | A user who often buys food always sees one bought or no-cook option among the three; a user who rarely buys pays a cost of 100 on bought options | cost 100 | PRD M-5; cost is a first value (B-10) | ⚠ |
| R_PLAN_COOK_ONCE | "Masak sekali" repeats the option in the same slot on the chosen day and the next 2 days; locked cells are not overwritten | 3 days | mockup `planner.html` (B-10) | ⚠ |

Food data sources are in Content.md §4 (TKPI 2020, about 1,100 foods; packaged foods by name, flagged by label source).

## 7. Points, levels, streaks, badges

Product rules (👤). Numbers can change before release; each change is recorded here with a date.

| ID | Rule | Value |
| --- | --- | --- |
| R_PTS_WORKOUT | Points per finished workout | 10 |
| R_PTS_ACT | Points per 30 activity minutes (any type, cap 1 per day) | 3 |
| R_PTS_FOOD | Points per day with food logged for ≥ 3 slots | 2 |
| R_PTS_MEASURE | Points per weight measure taken on the scheduled day (weekly) | 2 |
| R_PTS_STREAK | Points per streak week | 5 |
| R_PTS_TARGET | Points per week with food logged on at least 5 days (the amounts are never judged). Was "logged average within ±10 % of target"; changed 2026-11-18 by Claude to match the `LOGWEEK_1` badge, so no points or badge depend on a calorie number (PR-5). Owner can reverse | 5 |
| R_PTS_NOWEIGHT | Weight change never gives or takes points (PRD P-3) | — |
| R_LEVEL | Level thresholds (cumulative points): 0, 100, 250, 450, 700, 1,000, 1,400, 1,900, 2,500, then +600 per level | ⚠ tuning (not a source gap; owner decision §12 #3) |
| R_LEVEL_NOLOSE | Level never drops (PRD P-3) | — |
| R_STREAK | A week counts when at least 2 days are active (workout, activity or food day) (PRD P-4) | 2 days |
| R_STREAK_GRACE | One empty week in every four does not break the streak (PRD P-4) | 1 of 4 |
| R_BADGE_COUNT | 18 badges; none for weight lost (PRD P-5). Codes and conditions in Content.md §6 | 18 |

## 8. Reminders and tone

| ID | Rule | Value | Status |
| --- | --- | --- | --- |
| R_REM_MAX | Max reminders per day | 3 | 👤 |
| R_REM_SKIP | Skip when the action is already done today | — | ✅ (PRD R-2) |
| R_REM_HOURS | Default hours: Latihan 17:00 on scheduled days; Makan pagi 07:00, siang 12:00, malam 18:00; Timbang Sunday 07:00 | — | 👤 |
| R_TONE | No red for calories or weight; no blame; neutral words (no "kamu" or "Kak") | PRD PR-4 | ✅ |
| R_TONE_WORDS | No shaming words in any text, for example "gagal"; no commands such as "harus" or "jangan" in reminders | product rule | 👤 |
| R_REM_QUIET | No reminder from 21:00 until 07:00 | 21:00–07:00 | 👤 |
| R_REM_LATE | A reminder still goes out up to 3 h after its hour, then it is dropped for the day | 3 h | 👤 |
| R_REM_GAP | Latihan reminder only when the last workout was 2 or more days ago (rest days stay quiet; the program stores no weekdays) | 2 days | 👤 |
| R_REM_BACK | "Kembali" after 7 days without any record (workout, food, measure); a second one after 14; never more than 2 until a new record; never when the phone holds no data | 7 / 2 | 👤 |
| R_REM_WEIGH_SKIP | Timbang skipped when a weight was saved in the last 3 days | 3 days | 👤 |
| R_REM_ROTATE | Each kind has at least 5 text variants; the variant is chosen by day (same day, same text) and never repeats on two days in a row | 5 / 2 | 👤 |

## 9. Privacy and data rules

Summarised from PRD §9; these are checks, not numbers.

| ID | Rule |
| --- | --- |
| R_PRIV_NO_SDK | No analytics, ads, or crash SDK that sends user data |
| R_PRIV_NET | Network only for: content update (no user data), Drive (user's own, backup on), store crash reports |
| R_PRIV_PHOTO | Photos on phone only unless photo backup is on (PV-3) |
| R_PRIV_BACKUP_DEFAULT | Backup off until the user turns it on; automatic backup runs on open/resume on Wi-Fi when older than 24 h (B-2) |
| R_PRIV_KEEP | Last three Drive backups kept (B-1) |
| R_SHARE_NOKG | Share cards hide every kg value (body weight change and lifted load) by default; the switch is per card; when Saya hides weight, kg stays hidden on cards too. A card never shows an absolute body weight (PRD P-6, PR-5) |
| R_SHARE_BRAND | App name and QR are on by default and can be turned off (PRD P-6, D-06) |
| R_SHARE_FACE | Face mosaic is on by default when a card includes a progress photo (PRD P-6, PV-3) |
| R_SHARE_PX | Card size in pixels: 1,080 wide; story 1,920 tall, square 1,080 tall. Cards are drawn on the phone and shared through the system share sheet; nothing is uploaded (PV-1, PR-3). Tuning default |
| R_SHARE_DATA | A card is offered only when the phone holds the data for it; no card states a health claim; a record count of 0 is not printed (R_TONE). Food card window: the last 7 days |

## 10. Known gaps (⚠ list)

Closed only with a source (see Content.md §8, open items). Checked 2026-11-18 (S-07).

| # | Gap | Result of S-07 | Status | Effect until closed |
| --- | --- | --- | --- | --- |
| G1 | Activity factor (R_ACT) | No Indonesia-specific individual factor found: S8 uses one fixed factor 1.26 (ages 4–64, Tabel 1 note 2, p. 8) and does not name its BMR equation; Kemenkes activity classes (ringan / sedang / berat) give no multiplier; no validation of BMR equations in Indonesian adults found. Owner chose S2 PAL values (§1), which sit closer to the AKG reference (≈ 1.73–1.79 on Mifflin BMR) than the old 1.2 | ✅ closed 2026-11-18 | — |
| G2 | Deficit and surplus (R_GOAL_*) | −500 kcal is the low end of S5 (500–1,000 kcal/day, p. 26). +300 kcal is inside S19's 10–20 % surplus for maintenance 1,500–3,000 kcal. Pace R_PACE matches S18 (0.5–1 %/week) | ✅ closed | — |
| G3 | Fat share (R_FAT) and fibre (R_FIBRE) | Fat 25 % matches AKG 2019 adult rows (24.7–26.0 %); floor 0.8 g/kg inside S19 0.5–1.5 g/kg. Fibre: AKG 2019 is 20–37 g by sex and age, not a fixed 30 g; value changed to the AKG table (§2) | ✅ closed; fibre change approved 2026-11-18 | — |
| G4 | Clinical source for R_GATE_ED | S20 added (calorie-tracker harm reported by patients). No guideline prescribes a no-numbers mode; the gate stays an owner safe-side rule | ✅ closed (support found) | — |
| G5 | Source for under-18 and pregnancy gates | Under 18: S10 p. 1; AKG energy for 16–18 is at or above adults. Pregnancy: AKG adds +180 / +300 / +300 kcal by trimester (p. 8); S13 p. 6. "No deficit" is the owner's rule; no source loosens it. ACOG 804 (S11): only the abstract could be opened (full text behind a paywall, HTTP 402) | ✅ closed (gate unchanged) | — |
| G6 | Training numbers (R_TRN_*) | WEEK and REST confirmed (S14 Table 2, p. 700; S15). SETS and REPS differ from S14 for beginners: values changed (§5) | ✅ closed; changes approved 2026-11-18; weekly volume R_TRN_VOL added | — |
| G7 | Household measure weights (URT) | PMK 41/2014 (S17) opened in C-05 from the JDIH Kemenkes copy (BPK still HTTP 403). Its exchange tables print URT and grams (p. 8–16 staples, protein, fruit; p. 37 sugar; p. 41 fats; p. 14 and 89 vegetables 1 gelas cooked = 100 g); 124 of 161 rows are attached to TKPI rows in `content/household.json`. **Not in PMK:** centong, piring, mangkok, and weights of fried or cooked dishes (tempe and tahu goreng, telur dadar, pepes, ikan bakar, gulai). These 18 measures are our estimates ⚠ with method and spread, none weighed. PMK itself has 3 conflicts (gula 1 sdm 10 g p. 37 vs 20 g p. 89; ikan 40 g p. 11 vs 45 g p. 89; alpokat 50 g p. 15 vs 60 g p. 41) and 3 printed values that cannot be right per unit (margarin ¼ sdt 5 g; sari kedelai 2½ gelas 185 g; jeruk nipis 1¼ gelas 135 g), which are not used. PMK does not say raw or cooked for most rows; rows are attached to the raw TKPI row except nasi and cooked vegetables | ⚠ partly closed (C-05): PMK weights sourced; centong and cooked dishes stay estimates until weighed | Flagged estimates |
| G8 | Bought and regional meal numbers | No published source covers warung and bought meals. C-07 added 108 regional rows from our own recipes and a generic ingredient table (not TKPI, `sodium` empty); 0 official `bought` rows (PRD open item 6) | ⚠ open (C-07 partly done) | Flagged estimates |
| G9 | Return-after-absence load (R_TRN_RETURN, 60 %) | No source found that prescribes a return load; 60 % and one fewer set is a conservative owner rule | ⚠ open (owner rule, no source) | Rule on as written |

## 11. Changes log

| Date | Change |
| --- | --- |
| 2026-11-16 | First draft (D-03). |
| 2026-11-18 | S-07: sources opened; exact figures and pages written in §13. Closed G2–G6. Values changed (owner to confirm, §12): R_FIBRE 30 g → AKG table by sex and age (30 g fitted only two groups); R_TRN_SETS "3–4 after 4 weeks" → 3–4 from level 2 (S14 novice is 1–3 sets); R_TRN_REPS 3–5 strength range moved to level 2 and up (S14 novice is 8–12 RM). Open: G1, G7, G8, G9. S5 re-cited to the NHLBI Practical Guide (Donnelly 2009 gives no pace number); S18–S20 added; S15 title corrected. No safety limit loosened. |
| 2026-11-18 | Owner decision §12 #4: R_ACT changed to S2 PAL values 1.40 / 1.55 / 1.70 / 1.85 / 2.00 (maintenance about 7–17 % higher at low activity); G1 closed. Floors, BMR floor and pace check unchanged. |
| 2026-11-18 | B-02 (domain code): added IDs for numbers that had none: R_INPUT, R_GATE_U13 (PRD O-5), R_KCAL_G, R_RECALC (PRD M-2), R_MET, R_WHO_MIN, R_TRN_LEVELS. Written down how the code reads existing rules (no value changed, no limit loosened): gate order in §3; R_DEF_CAP group = risk answers; floor rounded up (R_ROUND); protein steps to 1.4 g/kg only when carbohydrate would go negative; R_PACE on weekly weights; R_TRN_RETURN rounded down to the equipment step. R_AGE: age gates use the youngest possible age (current year − birth year − 1), so born-2008 users stay under the R_GATE_U18 rules for 2026 and born-2013 users are stopped by R_GATE_U13 (tightened, not loosened; Decisions.md, §12 #9). |
| 2026-11-18 | Owner decisions on §12 #5–#8: R_FIBRE and R_TRN_SETS / R_TRN_REPS approved; added R_TRN_VOL, R_TRN_VOL_COUNT, R_TRN_VOL_SCOPE, R_TRN_VOL_FILL (weekly sets per muscle group) and R_TRN_PROG_STEP (10 % step limit); R_GATE_PREG now covers breastfeeding. Sources S21–S23 added. |
| 2026-11-19 | B-06 (generator): added R_TRN_EQUIP and R_TRN_GEN (👤) for the readings the generator needs; no value of an existing rule changed. Readings: R_TRN_WEEK holds level 1 at 2–3 days and lets level 2+ keep 2; R_TRN_VOL_FILL adds the one accessory on as many days as needed, and when the 20 cap blocks a minimum the cap wins and the group is reported (L-17); R_TRN_RETURN gap is counted from the last session of any kind; R_TRN_PROG_BW harder variant must fit the equipment and the program level. |

## 12. Owner decisions still open

| # | Decision | Default in this draft |
| --- | --- | --- |
| 1 | Under-18 gate: adjust goal or block the goal | Goal changed to "jaga", note shown |
| 2 | Calorie floor values (1,200 / 1,500) | As PRD S-4 (supported by S5, see §2) |
| 3 | Level thresholds (R_LEVEL) | As listed above |
| 9 | Age from birth year only (R_AGE) can be up to one year too high: someone born in 2008 counts as 18 for all of 2026, even before the birthday, so the age gates could miss a 17-year-old (or a 12-year-old) | Safe side taken by Claude (Decisions.md): the age gates use current year − birth year − 1. Alternative for the owner: ask birth month too |

Decided 2026-11-18 (owner): #5 fibre from the AKG table (approved); #6 sets and reps by level (approved), plus weekly sets per muscle group (R_TRN_VOL); #7 load step limited to 10 % (R_TRN_PROG_STEP); #8 left to Claude: breastfeeding shares the pregnancy gate, no deficit (R_GATE_PREG). #4 activity factor: S2 PAL values 1.40 / 1.55 / 1.70 / 1.85 / 2.00 (R_ACT).

## 13. Sources

Checked 2026-11-18 (S-07). "p." is the printed page of the document. "Abstract only" means the full text could not be opened.

| Code | Source | Exact figure and page | Used for |
| --- | --- | --- | --- |
| S1 | Mifflin MD, St Jeor ST, et al. (1990). A new predictive equation for resting energy expenditure in healthy individuals. *Am J Clin Nutr* 51(2):241–247. | Abstract, p. 241: men 10 × kg + 6.25 × cm − 5 × age + 5; women … − 161. | BMR |
| S2 | FAO/WHO/UNU (2004). *Human energy requirements*. Food and Nutrition Technical Report Series 1. | §5.3.1, Table 5.3: sedentary or light 1.40–1.69; active or moderately active 1.70–1.99; vigorous 2.00–2.40 (> 2.40 hard to keep up). §5.3.2: 1.21 "too low". Basis of R_ACT since 2026-11-18 (the earlier 1.2–1.9 set is not in S2). Note: S2 applies PAL to BMR from the Schofield equations; the app uses Mifflin (S1), about 4 % lower for the AKG reference man. | Activity factor (R_ACT) |
| S3 | WHO Expert Consultation (2004). Appropriate body-mass index for Asian populations and its implications for policy and intervention strategies. *Lancet* 363:157–163. | Abstract, p. 157: WHO cut-offs "retained as international classifications" (underweight < 18.5); action points 23.0, 27.5, 32.5, 37.5. Full text behind a paywall (HTTP 403); table page not opened. | BMI cut-offs |
| S4 | Ashwell M, Hsieh SD (2005). Six reasons why the waist-to-height ratio is a rapid and effective global indicator for health risks of obesity … *Int J Food Sci Nutr* 56(5):303–307. | Abstract, p. 303: "A boundary value of WHTR = 0.5 indicates increased risk for men and women" and for different ethnic groups. | WHtR |
| S5 | NHLBI Obesity Education Initiative (2000). *The Practical Guide: Identification, Evaluation, and Treatment of Overweight and Obesity in Adults*. NIH Pub. No. 00-4084. | p. 26: deficit 500–1,000 kcal/day for 1–2 lb per week; "1,000 to 1,200 kcal/day … for most women; … between 1,200 kcal/day and 1,600 kcal/day … for men". p. 27, Table 4 note 1 (same deficit). p. 19: "for individuals who are overweight, a deficit of 300 to 500 kcal/day "may be more appropriate". Formerly also cited Donnelly JE et al. (2009) ACSM Position Stand, *Med Sci Sports Exerc* 41:459–471: abstract checked, it gives activity minutes (150–250, > 250 min/week), **no** pace figure, so it is no longer used for R_PACE. | Deficit, floors (G2) |
| S6 | Jäger R et al. (2017). ISSN position stand: protein and exercise. *J Int Soc Sports Nutr* 14:20. | 1.4–2.0 g/kg/day (unchanged, ✅ in D-03). | Protein |
| S7 | Fat share: S8 Tabel 1 (fat g × 9 / energy). Adults: men 19–29 75 g / 2,650 kcal = 25.5 %; 30–49 70 / 2,550 = 24.7 %; 50–64 60 / 2,150 = 25.1 %; women 19–29 65 / 2,250 = 26.0 %; 30–49 60 / 2,150 = 25.1 %; 50–64 50 / 1,800 = 25.0 %. Range check: S18 15–30 %; S5 Table 4 p. 27 ≤ 30 %. Floor: S19. (WHO fat guidance from the D-03 draft was not opened and is dropped.) | as left | Fat (G3) |
| S8 | Permenkes RI No. 28 Tahun 2019, Angka Kecukupan Gizi yang Dianjurkan untuk Masyarakat Indonesia. Lampiran I, Tabel 1, pp. 6–8 (copy from jdih.kemkes.go.id). | Fibre (g/day): men 13–15 34, 16–18 37, 19–29 37, 30–49 36, 50–64 30, 65–80 25, 80+ 22; women 13–15 29, 16–18 29, 19–29 32, 30–49 30, 50–64 25, 65–80 22, 80+ 20. Pregnancy +180 / +300 / +300 kcal (trimesters 1–3); breastfeeding +330 / +400 kcal (p. 8). Note 2 (p. 8): activity factor 1.26 for ages 4–64. | Fibre, fat, gates |
| S9 | Atwater factors, standard in Indonesian TKPI (Tabel Komposisi Pangan Indonesia 2020). | 4 / 4 / 9 kcal per g (standard; not re-opened in S-07). | kcal per gram |
| S10 | WHO (2020). *WHO guidelines on physical activity and sedentary behaviour* (ISBN 978-92-4-001512-8), children and adolescents 5–17. | Executive summary p. 1: "at least an average of 60 minutes per day of moderate- to vigorous-intensity" activity; vigorous and muscle- and bone-strengthening "at least 3 days a week". | Under-18 gate (G5) |
| S11 | ACOG Committee Opinion No. 804 (2020). Physical activity and exercise during pregnancy and the postpartum period. *Obstet Gynecol* 135(4):e178–e188. | Abstract only (full text HTTP 402): physical activity in pregnancy "safe and desirable" without complications or contraindications. No energy or deficit figure read. | Pregnancy gate (G5) |
| S12 | Ainsworth BE et al. (2011). 2011 Compendium of Physical Activities. *Med Sci Sports Exerc* 43:1575–1581. | MET values as §4 (✅ in D-03; not re-opened in S-07). | MET values |
| S13 | WHO (2020), as S10, adults and pregnancy. | p. 2: adults 150–300 min moderate or 75–150 min vigorous "or an equivalent combination" (so 1 vigorous min = 2 moderate), muscle strengthening ≥ 2 days/week. p. 6: pregnant and postpartum women without contraindication "at least 150 minutes of moderate-intensity" activity per week. | 150 / 300 minutes; vigorous × 2; pregnancy |
| S14 | Ratamess NA et al. (2009). ACSM position stand: progression models in resistance training for healthy adults. *Med Sci Sports Exerc* 41(3):687–708. | p. 687 (abstract): novice 8–12 RM; intermediate–advanced 1–12 RM with emphasis on 1–6 RM; load +2–10 % when 1–2 reps over target; frequency 2–3 / 3–4 / 4–5 d/week. Table 2, p. 700: novice "1–3 sets per exercise"; novice–intermediate 60–70 % 1 RM × 8–12; "novice individuals train the entire body 2–3 d/wk"; intermediate 3 d/wk full body or 4 d/wk upper/lower split; rest "at least 2–3 min" for core lifts with heavier loads, "1–2 min" for assistance lifts; endurance 10–15 reps. | Sets, reps, frequency, rest |
| S15 | Schoenfeld BJ, Pope ZK, Benik FM, et al. (2016). Longer interset rest periods enhance muscle strength and hypertrophy in resistance-trained men. *J Strength Cond Res* 30(7):1805–1812. | Abstract, p. 1805: 21 trained men, 8 weeks, 3 sets of 8–12 RM; 3-min rest gave greater 1 RM squat and bench and thigh muscle thickness than 1-min rest. | Rest times |
| S16 | Zourdos MC et al. (2016). Novel resistance training-specific RPE scale measuring repetitions in reserve. *J Strength Cond Res* 30(1):267–275. | Abstract, p. 267: RPE 10 = 0 RIR, RPE 9 = 1 RIR. | RIR |
| S17 | Peraturan Menteri Kesehatan RI No. 41 Tahun 2014 tentang Pedoman Gizi Seimbang, Lampiran. Copy: jdih.kemkes.go.id/storage/documents/pdfs/2014permenkes041.pdf (96 pages, sha256 6b030d76…ef71b90), read 2026-11-19 in C-05. | URT tables, page numbers as printed: p. 8–9 penukar nasi (nasi ¾ gelas = 100 g); p. 10 nabati (tempe 2 potong sedang = 50 g; tahu 2 potong sedang = 100 g); p. 11–13 hewani (telur ayam 1 butir = 55 g; daging sapi 1 potong sedang = 35 g; susu sapi 1 gelas = 200 g); p. 14 and 89 sayuran 1 porsi = 1 gelas setelah dimasak = 100 g; p. 15–16 buah, berat bersih; p. 37 gula 1 sdm = 10 g; p. 41 minyak 1 sdt = 5 g. PMK cites DBMP FKUI 1997 and Penuntun Diet 2007 (p. 42). | Household measures (R_HH, G7) |
| S18 | Helms ER, Aragon AA, Fitschen PJ (2014). Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation. *J Int Soc Sports Nutr* 11:20. | Abstract, p. 1: weight loss "approximately 0.5 to 1%/wk"; "15–30% of calories from fat". | Pace, fat range |
| S19 | Iraki J, Fitschen P, Espinar S, Helms E (2019). Nutrition recommendations for bodybuilders in the off-season: a narrative review. *Sports* 7(7):154. | Abstract, p. 1: surplus "~10–20%", gain "~0.25–0.5% of bodyweight/week" for novice and intermediate; fat "0.5–1.5 g/kg/day". | Surplus, fat floor |
| S20 | Levinson CA, Fewell L, Brosof LC (2017). My Fitness Pal calorie tracker usage in the eating disorders. *Eat Behav* 27:14–16. | Abstract, p. 14: N = 105 with an eating disorder; ~75 % used the app; 73 % of users felt it contributed to their eating disorder. | Eating-disorder gate (G4) |

| S21 | Lovelady C (2011). Balancing exercise and food intake with lactation to promote post-partum weight loss. *Proc Nutr Soc* 70(2):181–184. | Abstract, p. 181: "once lactation is established, overweight women may restrict their energy intake by 2092 kJ/d [500 kcal] and exercise aerobically 4 d/week to promote a weight loss of 0·5 kg/week". Used only as context; the app does not apply a deficit. | Breastfeeding gate |
| S22 | Schoenfeld BJ, Ogborn D, Krieger JW (2017). Dose-response relationship between weekly resistance training volume and increases in muscle mass: a systematic review and meta-analysis. *J Sports Sci* 35(11):1073–1082. | Abstract, p. 1073: 15 studies; each extra weekly set +0.37 % gain; categories < 5, 5–9 and 10+ sets per muscle per week (trend P = 0.074); "graded dose-response relationship". | Weekly sets |
| S23 | Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC (2026). The resistance training dose response: meta-regressions exploring the effects of weekly volume and frequency on muscle hypertrophy and strength gains. *Sports Med* 56(2):481–505. | Abstract, p. 481: 67 studies, 2,058 participants; indirect sets counted 0.5 ("fractional") fit best; gains rise with volume "with diminishing returns". | Set counting, upper limit |

Population note: S5, S18 and S19 come from adults with overweight (S5) or trained lifters (S18, S19). They support the working numbers; they do not make them medical advice. R_DISCLAIMER stays.
| 2026-11-19 | B-08: added R_TRN_EDIT, R_JEL_SHELF, R_MAP_TONE and R_ACT_MINUTES (👤) for the readings the Latihan screen needs; no value of an existing rule changed. |
