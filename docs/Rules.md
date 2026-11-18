# Rules — XNK Fit

| | |
| --- | --- |
| Status | Draft, 2026-11-16 (task D-03) |
| Reads | [PRD.md](PRD.md) (what), [Design.md](Design.md) (how). |
| Rule | PR-6: every number has a written source. A rule without a verified source is marked **⚠** and is **not final**. Code reads each number from one named constant; the rule ID in the table is the constant's name prefix. |

Each rule has: **ID**, the value, the **source** (or "product rule" for owner choices), and status. Status: ✅ source checked, ⚠ source still to verify (the value is a working default), 👤 owner decision.

Sources are listed in §13. Where a source is cited by short code (for example `S3`), see that list.

## 1. Body and energy

| ID | Rule | Value | Source | Status |
| --- | --- | --- | --- | --- |
| R_MSJ_M | BMR men (kcal/day) | 10·kg + 6.25·cm − 5·age + 5 | S1 | ✅ |
| R_MSJ_F | BMR women (kcal/day) | 10·kg + 6.25·cm − 5·age − 161 | S1 | ✅ |
| R_ACT | Activity factor from weekly activity (PRD L-14) | Sedentary 1.2 · light (1–2 days) 1.375 · moderate (3–4) 1.55 · active (5–6) 1.725 · very active (7) 1.9 | S2 (ranges), labels product rule | ⚠ check exact cut-offs in S2 |
| R_TDEE | Maintenance = BMR × activity factor. Shown; the target uses it, activity calories are **not** added on top (M-1) | — | product rule (M-1) | 👤 |
| R_AGE | Age from birth year: current year − birth year | — | product rule | ✅ |
| R_BMI | BMI = kg / m² | — | S3 | ✅ |
| R_BMI_UNDER | Goal "turun" not offered below BMI | < 18.5 | S3 (Asia-Pacific cut-off) | ⚠ and PRD S-5 |
| R_WHTR | Waist-to-height ratio, shown in Bantuan only | ≥ 0.5 flagged as "perhatikan" (kind wording) | S4 | ⚠ |

## 2. Calorie target and macros

| ID | Rule | Value | Source | Status |
| --- | --- | --- | --- | --- |
| R_GOAL_TURUN | Deficit for "turun" | −500 kcal/day (≈ 0.5 kg/week) | S5 | ⚠ verify |
| R_GOAL_NAIK | Surplus for "naik" | +300 kcal/day | S5 (smaller surplus for lean gain) | ⚠ verify |
| R_GOAL_JAGA | "jaga" (maintain) | 0 | product rule | 👤 |
| R_DEF_CAP | Safety cap: deficit never more than −10 % of maintenance for the "other safety" group (PRD S-7) | ≤ −10 % | owner rule, PRD S-7 | 👤 |
| R_FLOOR_F | Calorie floor women (PRD S-4) | 1,200 kcal/day | owner caution, see §3 | 👤 ⚠ |
| R_FLOOR_M | Calorie floor men (PRD S-4) | 1,500 kcal/day | owner caution | 👤 ⚠ |
| R_FLOOR_BMR | Target is never below BMR (PRD S-4) | target ≥ BMR | owner caution | 👤 |
| R_PROT | Protein target | 1.6 g per kg body weight; floor 1.4, cap 2.0 | S6 (range 1.4–2.0 g/kg) | ✅ |
| R_FAT | Fat | 25 % of target kcal, floor 0.8 g/kg | S7 | ⚠ verify |
| R_CARB | Carbohydrate | Remaining kcal | arithmetic | ✅ |
| R_FIBRE | Fibre target (shown, not enforced) | 30 g/day | S8 (AKG 2019) | ⚠ verify number |
| R_ROUND | Calorie target rounded to 10 kcal | — | product rule | 👤 |

Macro grams: protein 4 kcal/g, carbohydrate 4 kcal/g, fat 9 kcal/g (S9).

Activity calories (L-14): shown, **never added** to the target (M-1). Activity calories = MET × body kg × hours (§4).

## 3. Safety gates

Applied in this order before any program or target is shown (PR-5, Design §7).

| ID | Gate | Effect | Source | Status |
| --- | --- | --- | --- | --- |
| R_GATE_SCREEN | Full screening not done | No program, no target; screening opens | PRD O-2, S-1 | 👤 |
| R_GATE_RISK | Health question "yes" (risk answers) | Lighter program (§5 level 1), text: "Konsultasi dokter sebelum latihan" | PRD S-1 | 👤 |
| R_GATE_U18 | Age under 18 | Light mode, technique-first, **no deficit** (goal "turun" changed to "jaga") | PRD S-2; sumber pendukung: S10 | 👤 ⚠ |
| R_GATE_PREG | Pregnancy (self-reported) | **No deficit**; text advising a doctor | PRD S-3; S11 | 👤 ⚠ |
| R_GATE_ED | History of eating disorder | Maintain target (no deficit), **no-numbers mode**: calories hidden, meal plan and ticks only, note to talk to a doctor | PRD S-6 | 👤 ⚠ clinical source to add |
| R_GATE_BMI | BMI < 18.5 | No "turun" option (R_BMI_UNDER) | PRD S-5 | ⚠ |
| R_GATE_FLOOR | Target below floor | Raised to the floor (R_FLOOR_*) and note shown | PRD S-4 | 👤 |
| R_PACE | Fat-loss pace: loss faster than 1 % of body weight per week for two weeks | Suggests raising calories (+100 kcal), user decides (M-2) | PRD S-8; S5 | 👤 ⚠ |
| R_DISCLAIMER | "Bukan pengganti dokter / ahli gizi" text on target and program screens | Always shown once in Bantuan and on the target card | PRD S-7 | ✅ text |

## 4. Activity (MET)

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
| R_TRN_WEEK | Strength sessions per week by level | Beginner 2–3 (full body), intermediate 3–4 (split) | S14 (ACSM 2009) | ⚠ verify |
| R_TRN_SETS | Working sets per exercise, beginner | 2–3 (rising to 3–4 after 4 weeks) | S14 | ⚠ verify |
| R_TRN_REPS | Rep range: strength / hypertrophy | 3–5 / 8–12 for weighted lifts; 10–15 for bodyweight and bands | S14 | ⚠ verify |
| R_TRN_REST | Rest between sets | 60–90 s isolation; 2–3 min compound (default) | S15 | ⚠ verify |
| R_TRN_RIR | Target effort: 1–3 reps in reserve for most sets | Taught in Bantuan (glossary) | S16 | ✅ |
| R_TRN_PROG_UP | Upper body load increase when top of range hit with form "rapi" | +2.5 kg | product rule, common practice | 👤 |
| R_TRN_PROG_LEG | Legs load increase | +5 kg | product rule | 👤 |
| R_TRN_PROG_BW | Bodyweight / limited weights progression: reps to top → +1 set → next harder variant | — | product rule (L-2) | 👤 |
| R_TRN_BAND | Bands logged as band level + reps (no kg) | Levels 1–5 | product rule (L-2) | 👤 |
| R_TRN_FORM | "Ragu" holds load until two "rapi" in a row | 2 | product rule (L-12) | 👤 |
| R_TRN_REVIEW | Automatic review of the program | Every 4 weeks or after 3 completed sessions | product rule (L-4) | 👤 |
| R_TRN_RETURN | Return after absence | ≥ 14 days: 60 % of last loads, one fewer set | product rule (L-16) | 👤 ⚠ |
| R_TRN_LEVEL1 | Gate for "risk" answers | Level 1 programs only (§3) | PRD S-1 | 👤 |
| R_TRN_NO_WARMUP | No warm-up sets in programs or the logger | — | PRD L-7 | 👤 |

Levels: 1 beginner (first 8 weeks, or sign-up experience), 2 intermediate, 3 advanced. The sign-up experience sets the starting level (Design §6).

## 6. Food rules

| ID | Rule | Value | Source | Status |
| --- | --- | --- | --- | --- |
| R_HALAL | Halal by default: no pork or alcohol in bundled foods | — | product rule (M-9) | 👤 |
| R_LIMIT_SANTAN | Santan (coconut milk) in automatic options | ≤ 1 per day, ≤ 3 per week | product rule (planner, M-4), from XNK nutrition rule set | 👤 |
| R_LIMIT_GORENG | Fried main in automatic options | ≤ 1 per day | product rule | 👤 |
| R_LIMIT_MAIN | Same main protein twice in a day | not allowed in automatic options | product rule | 👤 |
| R_LIMIT_ALLERGY | Allergy filters (egg, seafood, nuts, milk), lactose | removed from automatic options | PRD M-9 | ✅ |
| R_BUDGET | Budget preference: Hemat / Standar / Bebas (no Rupiah amounts) | Option weight in automatic choice only | PRD M-9 | 👤 |
| R_MEAL_SLOTS | Slots: pagi, siang, malam, camilan | — | product rule | 👤 |
| R_OPTIONS | Meal options per slot | 3 | PRD M-3 | ✅ |
| R_HH | Household measures shown first; grams beside, with "±" | Measure weights from PMK 41/2014 (S17) where listed; cooked dishes are estimates | S17, estimates flagged | ⚠ see §10 |
| R_EST | Estimates (warung, bought, own cooking) are flagged `isEstimate` and labelled "perkiraan" | — | PRD PR-6 | ✅ |

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
| R_PTS_TARGET | Points per week the logged average is within ±10 % of target | 5 |
| R_PTS_NOWEIGHT | Weight change never gives or takes points (PRD P-3) | — |
| R_LEVEL | Level thresholds (cumulative points): 0, 100, 250, 450, 700, 1,000, 1,400, 1,900, 2,500, then +600 per level | ⚠ tuning |
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

## 9. Privacy and data rules

Summarised from PRD §9; these are checks, not numbers.

| ID | Rule |
| --- | --- |
| R_PRIV_NO_SDK | No analytics, ads, or crash SDK that sends user data |
| R_PRIV_NET | Network only for: content update (no user data), Drive (user's own, backup on), store crash reports |
| R_PRIV_PHOTO | Photos on phone only unless photo backup is on (PV-3) |
| R_PRIV_BACKUP_DEFAULT | Backup off until the user turns it on; automatic backup runs on open/resume on Wi-Fi when older than 24 h (B-2) |
| R_PRIV_KEEP | Last three Drive backups kept (B-1) |

## 10. Known gaps (⚠ list)

Closed only with a source (see Content.md §8, open items).

| # | Gap | Effect until closed |
| --- | --- | --- |
| G1 | Exact activity-factor cut-offs (R_ACT) | Use the labelled ranges; screen says "perkiraan" |
| G2 | Deficit % and surplus (R_GOAL_*) | Working default; a unit test flags them unverified |
| G3 | Fat percentage of energy (R_FAT) and fibre target (R_FIBRE, AKG 2019) | Shown as estimates |
| G4 | Clinical source for R_GATE_ED | Feature on, text says "bicarakan dengan dokter" |
| G5 | Source for under-18 and pregnancy gates | Gate on (safe side); source tracked in S10/S11 |
| G6 | Training rep/set/rest numbers (R_TRN_*) | Beginner defaults; verify in S14–S15 |
| G7 | Household measure weights for cooked dishes | Flagged estimates; C-05 |
| G8 | Bought and regional meal numbers | 130 regional rows are flagged estimates from our own recipes and a generic ingredient table (not TKPI, `sodium` empty); 0 official `bought` rows yet (PRD open item 6; C-07) |

## 11. Changes log

| Date | Change |
| --- | --- |
| 2026-11-16 | First draft (D-03). |

## 12. Owner decisions still open

| # | Decision | Default in this draft |
| --- | --- | --- |
| 1 | Under-18 gate: adjust goal or block the goal | Goal changed to "jaga", note shown |
| 2 | Calorie floor values (1,200 / 1,500) | As PRD S-4 |
| 3 | Level thresholds (R_LEVEL) | As listed above |

## 13. Sources

| Code | Source | Used for |
| --- | --- | --- |
| S1 | Mifflin MD, St Jeor ST, et al. (1990). A new predictive equation for resting energy expenditure. *Am J Clin Nutr* 51:241–247. | BMR |
| S2 | FAO/WHO/UNU (2004). *Human energy requirements*. Report of a joint consultation. | Activity factor ranges (cut-offs to check, G1) |
| S3 | WHO Expert Consultation (2004). Appropriate body-mass index for Asian populations. *Lancet* 363:157–163. | BMI cut-offs |
| S4 | Ashwell M, Hsieh SD (2005). Six reasons why the waist-to-height ratio is a rapid and crude indicator. *Int J Food Sci Nutr* 56:303–307. | WHtR |
| S5 | Energy-balance rule of thumb (about 500 kcal per day ≈ 0.5 kg per week), NIH/NHLBI obesity guidance. Pace: ACSM Position Stand (2009), *Med Sci Sports Exerc* 41:459–471. | Deficit, surplus, pace (G2) |
| S6 | Jäger R et al. (2017). ISSN position stand: protein and exercise. *J Int Soc Sports Nutr* 14:20. | Protein 1.4–2.0 g/kg |
| S7 | Fat share and floor: Indonesian AKG 2019 (Permenkes 28/2019) and WHO fat guidance. | Fat (G3, to verify) |
| S8 | Permenkes RI 28/2019, Angka Kecukupan Gizi (AKG) untuk masyarakat Indonesia. | Fibre (G3, to verify) |
| S9 | Atwater factors, standard in Indonesian TKPI (Tabel Komposisi Pangan Indonesia 2020). | kcal per gram |
| S10 | Under-18 training guidance: WHO (2020) Guidelines on physical activity and sedentary behaviour; owner's rule (PRD S-2). | Under-18 gate (G5) |
| S11 | Pregnancy: ACOG Committee Opinion 804 (2020) on physical activity in pregnancy; no deficit is owner's rule (PRD S-3). | Pregnancy gate (G5) |
| S12 | Ainsworth BE et al. (2011). 2011 Compendium of Physical Activities. *Med Sci Sports Exerc* 43:1575–1581. | MET values |
| S13 | WHO (2020). *WHO guidelines on physical activity and sedentary behaviour*. | 150 / 300 minutes; vigorous ×2 |
| S14 | Ratamess NA et al. (2009). ACSM position stand: progression models in resistance training. *Med Sci Sports Exerc* 41:687–708. | Sets, reps, weekly frequency |
| S15 | Schoenfeld BJ, Pope ZK, Benik FM, et al. (2016). Longer interset rest periods enhance muscle hypertrophy in resistance-trained men. *J Strength Cond Res* 30(7):1805–1812. | Rest times |
| S16 | Zourdos MC et al. (2016). Novel resistance training-specific RPE scale measuring repetitions in reserve. *J Strength Cond Res* 30:267–275. | RIR |
| S17 | Peraturan Menteri Kesehatan RI No. 41 Tahun 2014 (Pedoman Gizi Seimbang). | Household measures (partial) |

Sources marked "verify" in the tables are not yet opened and checked line by line. Each is closed in S-01 or a later research task by reading the paper or document and writing the exact figure and page into this list. Until then the number is a working default and the rule keeps its ⚠ flag.
