# PRD — XNK Fit

| | |
| --- | --- |
| Product | XNK Fit: a free, offline-first workout and meal app for the public (Android first) |
| Owner | xnkzzxd |
| Status | Draft, 2026-11-16 (task D-01) |
| Source | Owner plan "XNK Fit" (24 rounds of owner answers, 2026-11-16) |
| Related | Design.md (D-02) · Rules.md (D-03) · Content.md (D-04) · TODO.md · mockups (D-05, `docs/mockups/`). |

Requirement IDs (G-, U-, O-, …) are stable. Later docs and tasks cite them; never renumber, only add.

## 1. Background

The XNK coaching app (repo `xnkzzxd/XNK`) already has a workout logger, an automatic program generator, a meal planner, levels, badges and share cards, all built for clients of one coach and stored in Google Sheets. The old plans for the logger and meal plan were marked "redo", and three Android attempts overlap in that repo.

The owner wants one new product instead: an app **for people without a coach**, that works fully offline, uses the new Phase X portal design, and is its own product in its own repo. There is no deadline; quality comes first. The process is **docs, then clickable mockups, then code**. No app code is written before the owner approves the mockups (D-05).

## 2. Product summary

| | |
| --- | --- |
| Name | **XNK Fit**. The name carries XNK, but the app is a separate product: no link to the panel or Sheets, no coaching offers, no mention of coaching. |
| Users | The public. Two groups: gym beginners, and people on a diet or losing weight. |
| Market | Indonesia first. Every text is translatable so English can follow. Metric units only. |
| Money | Free at launch; a business model is decided later. Running cost stays near zero. |
| Promise | **"Data di HP Anda. Cadangan di Google Drive Anda."** No analytics SDK, no ads. |
| Publisher | Owner's personal name. Rated 13+. Worldwide availability, Indonesian listing. |
| Platforms | Android first: Android 8+, and it must feel fast on 2–3 GB RAM phones. iPhone later; no v1 choice may block it. |
| Release | One complete v1, after the Play closed test (12 testers for 14 days). No developer account exists yet. |

## 3. Problems

1. **Beginners without a coach don't know what to do in the gym**, at home with a pair of dumbbells, or with no equipment at all, and give up when progress isn't visible.
2. **Calorie apps are built for Western food.** Indonesian meals (nasi, lauk, warteg, padang, anak kos cooking) are missing or entered in grams nobody weighs. Household measures (centong, potong, sendok) are what people actually know.
3. **Most fitness apps need an account and a signal**, send personal data to their servers, and push ads or upsells. Gyms in Indonesia often have poor signal.
4. **Diet apps can be harsh**: red numbers for going over, weight front and centre, aggressive deficits with no safety checks for teens, pregnancy or eating-disorder history.

## 4. Goals and non-goals

**Goals**
- G1. A beginner with no coach gets a safe, sensible program for the equipment they actually have, and the app moves it forward automatically with a reason they can read.
- G2. Logging a workout or a meal is quick and never loses data, even with no signal, a killed app or a dead battery mid-workout.
- G3. Meal plans and the food log use Indonesian food and household measures first.
- G4. Everything works without an account. The user's data stays on the phone; the only copy elsewhere is the user's own Google Drive, and only if they turn backup on.
- G5. The tone is kind and the rules are safe for people training and dieting alone.
- G6. Fast on low-end Android phones, enforced by a speed budget that fails the build.

**Non-goals (v1)**
- Any server of ours that stores user data, any user account system of ours, analytics, ads.
- Coaching, coach links, or anything tied to the XNK coaching app.
- Live multi-device sync (backup and restore only, one phone at a time).
- Medical diets, meal-timing advice, an added "burned calories" credit to the food target.
- The items listed in §11 (v1.1 or later).

## 5. Principles

| ID | Principle |
| --- | --- |
| PR-1 | **Offline-first.** The phone database is the truth. Every feature except Drive backup and content updates works in airplane mode. No account is needed. |
| PR-2 | **Every tap is saved at once.** No send step, no draft, no queue states. A killed app resumes the workout where it stopped. |
| PR-3 | **No backend of ours.** Network use is limited to: static data updates (checksummed, no user data sent), the user's own Google Drive, and store crash reports. |
| PR-4 | **Kind tone.** No red for going over calories or for weight gain. Trends over single numbers. Weight can be hidden. Neutral wording (no "kamu" or "Kak"). |
| PR-5 | **Safe without a coach.** Health screening, lighter plans, a calorie floor, no deficit under 18 or in pregnancy, and "bukan nasihat medis" where it matters. |
| PR-6 | **Every number has a written source** (the XNK rule). Rules without a verified source are marked and are not final. Estimates are flagged as estimates. |

## 6. Users and main jobs

| User | Main jobs |
| --- | --- |
| Gym beginner | Get a program for my gym, know what to do next set, see that I'm getting stronger. |
| Home trainer (bodyweight, dumbbells, bands) | Same, with only what I own; harder variants when the easy ones get easy. |
| Diet / weight-loss user | Know how much to eat, pick meals I can cook or buy, log food in measures I know, see a kind trend. Gets a gentle activity target even if they never lift. |

## 7. v1 requirements

### 7.1 Onboarding

| ID | Requirement |
| --- | --- |
| O-1 | Short flow: goal · sex, birth year, height, weight · main interest (Latihan / Makan / both) · nickname (optional) · one health question · permissions screen. |
| O-2 | The health question: "yes" opens the full screening form now; "no" defers it to the first program. **A program is never made before the full form is done.** |
| O-3 | Permissions screen covers notifications, camera, Health Connect and Google backup. Each is skippable and asked again at the moment of use. |
| O-4 | The training profile is asked when Latihan is first opened; food preferences when Makan is first opened. |
| O-5 | Under 13 (from birth year): the app explains it is for 13 and up and does not continue. |
| O-6 | No account and no sign-in in onboarding. |

### 7.2 Navigation

| ID | Requirement |
| --- | --- |
| N-1 | Five tabs: **Hari ini · Latihan · ＋ · Makan · Saya**. |
| N-2 | ＋ opens: Mulai latihan, Catat makan, Catat berat, Catat aktivitas. |
| N-3 | Phone back button / gesture steps back through sheets and screens. |
| N-4 | Tablets use two columns with a side rail. |

### 7.3 Hari ini

| ID | Requirement |
| --- | --- |
| H-1 | Order: next workout (or rest-day) card, calorie ring, week strip, level and streak. |
| H-2 | Users who don't lift see a weekly activity target (WHO 150 minutes) instead of the workout card. |
| H-3 | Empty cards say what fills them and offer one button. No sample data. |

### 7.4 Latihan

| ID | Requirement |
| --- | --- |
| L-1 | Automatic program from the XNK program rules, in four equipment settings: full gym, home bodyweight, home dumbbells / bands, tick-your-own equipment. |
| L-2 | New progression for bodyweight and limited weights: reps to the top of the range, then +1 set, then the next harder variant. Bands are logged as band level + reps, not kg. |
| L-3 | A movement pattern with no possible exercise for the user's equipment is told plainly ("Butuh palang atau karet untuk gerakan tarik"), never filled with a wrong pattern. |
| L-4 | Progression is automatic and explained. A periodic review proposes changes with a one-line reason; the user accepts or declines. |
| L-5 | The user always wins: a routine the user edits becomes "own" and is never overwritten; auto-programming can be turned off. |
| L-6 | Logger with two first-class modes, chosen at the first workout with neither preselected (owner, D-06): **Dipandu** (one exercise, one set at a time) and **Bebas** (table). |
| L-7 | Logger features: set kinds, supersets, rest timer, swap exercise mid-workout, weight suggestions, previous numbers. No warm-up sets in programs or the logger. |
| L-8 | The rest timer notifies when it ends, even with the screen off or the app killed. |
| L-9 | Own routines, empty workout, and a small Jelajahi shelf: full body 3x, upper / lower, home bodyweight, dumbbell-only. |
| L-10 | Exercise library of **about 150 curated exercises**, each with our own animated figure, muscle map and cues. Exercise names, muscles and hints in English. Split: about 60 gym, 40 dumbbell / band, 35 bodyweight (with easier and harder variants), 15 cardio and core; at least three choices per pattern in each setting. |
| L-11 | Muscle map per workout and per week; a "Kenapa gerakan ini" hint per exercise. |
| L-12 | Self-rated form: "Ragu" holds the weight until two "Rapi" in a row. Reps and sets may still progress. |
| L-13 | Workout summary: numbers and records, the session's muscle map, feeling 1–5 and a note, a share button. |
| L-14 | Activity log: type + minutes + effort, calories from published MET values. Any logged activity counts toward 150–300 minutes a week; vigorous effort counts double. |
| L-15 | Steps from Health Connect, shown (today, weekly average) but adding no calories and no level points. The feature hides itself where Health Connect is missing. |
| L-16 | Coming back after weeks: quiet restart, no "streak lost" banner, and the first workout is eased by the return-after-absence rule. |

### 7.5 Makan

| ID | Requirement |
| --- | --- |
| M-1 | Automatic calorie and macro target from the XNK nutrition formulas. Activity calories are shown but **never added** to the target. No meal-timing advice. |
| M-2 | Recalculation is asked, with the reason ("Berat turun 3 kg sejak target dihitung. Hitung ulang target?"); the user accepts or keeps. |
| M-3 | Meal plan with **three options per meal**, and a **week planner** (swap, lock, masak sekali, makan di luar). Daily and weekly limits still apply behind the scenes. |
| M-4 | The user's own planner choices are never blocked; limits steer only the automatic options, and the screen says so kindly ("Minggu ini santan sudah 3 kali"). |
| M-5 | Variety: more regions, bought meals (warteg, padang, bakso; flagged as estimates), anak kos cooking, the user's own meals. One bought / no-cook option per slot when the user says they often buy food. |
| M-6 | **Household measures first** (centong, potong, sdm) shown with "±", grams beside. |
| M-7 | Food log: full TKPI 2020 (about 1,100 foods), packaged foods by name, warung estimates, own foods and recipes; recent and favourites, copy a meal or yesterday, quick-add calories. |
| M-8 | Shopping list from the week plan. |
| M-9 | Halal by default. Allergy filters (egg, seafood, nuts, milk) and lactose. Budget preference Hemat / Standar / Bebas, with no Rupiah amounts. |
| M-10 | On phones the week planner shows one day per screen with swipe between days; tablets show a 7-column table. |

### 7.6 Saya, progress and motivation

| ID | Requirement |
| --- | --- |
| P-1 | Progress: weight and waist by default, six more measures opt-in, charts, private photos stored on the phone only. |
| P-2 | Perjalanan (journey moments), athlete card, Kartu saya. |
| P-3 | Level from points that only ever add up; the level never drops. Points come from workouts, activities, food days, measurements on schedule, streak weeks and hitting targets, **never from the weight number itself**. |
| P-4 | Streak: a week counts with at least 2 active days (workout, activity or food day); one empty week in every four does not break it. |
| P-5 | 18 kind badges; none for weight lost. |
| P-6 | Share cards: workout + PR, progress, consistency + level, food. Simple editor (pick card, pick style, share). App name + QR on by default. Progress-photo cards keep the "no kg" and face-mosaic switches. |
| P-7 | Settings, in order: Profil, Latihan, Makan, Pengingat, Tampilan, Data (backup, export, delete), Bantuan. |
| P-8 | Bantuan + tentang: how each target is calculated (with source), one-time welcome cards per tab, a short how-to list, an Indonesian glossary (RIR, superset, set kinds, macros), privacy and sources. Feedback via a form link. |

### 7.7 Reminders

| ID | Requirement |
| --- | --- |
| R-1 | Local notifications only: workout days, meals / log food, weigh-in. |
| R-2 | Varied, short, neutral text ("Latihan hari ini: Full Body A, sekitar 45 menit."), at the user's hours, about three a day at most, skipped when already done. |

### 7.8 Backup, restore, export

| ID | Requirement |
| --- | --- |
| B-1 | Google sign-in only for backup, into the user's hidden Drive app folder. The last three backups are kept. |
| B-2 | Automatic backup runs **when the app is opened or resumed** on Wi-Fi and the last backup is over a day old, plus a manual button. No background service. |
| B-3 | Photos are backed up only if the user switches that on. |
| B-4 | One format for backup and export: a zip of JSON per table plus a manifest with checksums; the export adds readable CSVs. Import uses the same code as restore. |
| B-5 | Restore **replaces everything after a clear warning**; it never merges. A backup from a newer app version is refused ("perbarui aplikasi dulu"). |
| B-6 | A backup made on another phone asks before overwriting. |
| B-7 | "Hapus semua data" in settings removes everything on the phone. |

### 7.9 Display and accessibility

| ID | Requirement |
| --- | --- |
| D-1 | Light and dark, seven accents, big text, the Phase X look. |
| D-2 | 44 px touch targets, the right keyboard (or + / − steppers in the logger), works at 360–430 px wide and on tablets. |

## 8. Safety requirements

| ID | Requirement |
| --- | --- |
| S-1 | Full health screening before the first program. Risk answers give a lighter program and text advising a doctor. |
| S-2 | Under 18: light mode, technique first, text advising training with an adult, **no calorie deficit**. |
| S-3 | Pregnancy: **no calorie deficit**. |
| S-4 | Calorie floor: never below 1,200 kcal (women) / 1,500 kcal (men), and never below BMR (owner's own caution, stated as such). |
| S-5 | Goal "turun" is not offered below BMI 18.5. |
| S-6 | A history of eating disorder gives a maintain target and a **no-numbers mode** (meal plan and ticks only, calories hidden), with a gentle note to talk to a doctor. |
| S-7 | Other safety cases keep a deficit of at most −10 % and the "bukan pengganti dokter / ahli gizi" text. |
| S-8 | Fat-loss pace check: faster than about 1 % of body weight a week for two weeks suggests raising calories. |

The full rules, every number and its source, belong in Rules.md (D-03).

## 9. Privacy and data

| ID | Requirement |
| --- | --- |
| PV-1 | No analytics SDK, no ads, no tracking. |
| PV-2 | Content updates download static files only; no user data is sent. |
| PV-3 | Photos never leave the phone unless the user turns on photo backup to their own Drive. |
| PV-4 | The Play data-safety form and privacy policy state exactly the above. |

## 10. Non-functional requirements

| ID | Requirement |
| --- | --- |
| NF-1 | Speed budget on one slow reference phone (2–3 GB RAM), proposed: cold start to a usable Hari ini 2.0 s · tab switch 100 ms · set tick to saved on disk 50 ms · food search per keystroke 100 ms · installed size 20 MB. Final numbers set in Design (D-02). CI fails the build when a throttled check exceeds the budget. |
| NF-2 | Crash safety: an unfinished workout survives a kill at every step (tested). |
| NF-3 | All UI text lives in one strings file; a test fails on a text literal in a screen, so English is a second file later. |
| NF-4 | Android 8+; a minimum WebView version set in Design. Nothing in v1 may block a later iPhone build. |
| NF-5 | Quality gates: Node logic tests, Playwright screen tests (phone and tablet, both themes), the speed check, migration tests from every released schema. |

## 11. Not in v1

Barcode scan · share-editor tools and video · Hevy / Strong import · vegetarian switch · Ramadan mode · water tracker · GPS · weekly Rupiah budget · live multi-device sync · medical diets · coach link · English UI · imperial units · a live countdown inside the rest-timer notification · iPhone (HealthKit, probably Sign in with Apple) · desktop shell.

## 12. Name

**XNK Fit** was chosen by the owner on 2026-11-16 (xnkfit.com had no registration record that day). Shortlists considered and dropped are recorded in the owner's plan.

| Check | Status |
| --- | --- |
| xnkfit.com | Free on 2026-11-17: the .com registry (RDAP) answers "not found" (404). Still not registered; owner registers if wanted. |
| Play Store search | Checked 2026-11-17 (public search page "XNK Fit" and "XNK"). No app named XNK Fit or XNK. Similar names, all fitness: XC Fit, X-Fit, XFIT, XOfit, xForFit, Fitxlink, Smartix Fit X, FIT X GYM, Knit Fit. None identical; X-Fit / XFIT are the closest in sound. Search shows only the first results, so this is not a full listing. |
| App Store search | Checked 2026-11-17 (Apple search API, "XNK Fit" and "XNK", Indonesia store). No app named XNK Fit. Similar names: XN2FIT (Health & Fitness, Ex Nihilo Nihil Fit LLC), NK Fitness App, XFIT, X-Fit, Xplicit Fit, XWEAR. "XNK" alone only finds "Thuận Lợi XNK" (Business): in Vietnamese XNK abbreviates xuất nhập khẩu (import-export), unrelated to fitness. |
| Indonesian trademark register (PDKI, classes 9 and 41 / 44) | **Not checked, cannot be checked.** pdki-indonesia.dgip.go.id loads results with JavaScript and returned an empty page to the fetch tool; web search found no trademark records. Owner or an IP consultant must search "XNK Fit" and "XNK" (classes 9, 41, 44) on PDKI by hand before filing. No conclusion either way. |
| Fit beside "XNK Coach", the brand kept for the paused coaching product | To judge in S-01. Not a name-availability matter; left for the owner. |

## 13. Success criteria for v1

- Every requirement in §7–§10 is met and checked on a real low-end phone.
- The speed budget passes on the reference phone before release.
- The Play closed test (12 testers, 14 days) completes and the app is approved for production.
- No user data leaves the phone except into the user's own Drive.

## 14. Open items

| # | Item | Recommendation / status |
| --- | --- | --- |
| 1 | Name checks (store, trademark, domain) | Do early in S-01; every later asset uses the name. |
| 2 | ~150 own exercise figures | The largest content job (each pattern needs sourced joint angles). Start on day one; it sets the launch date. |
| 3 | English exercise cues for Indonesian beginners | Decided in D-06 (2026-11-17): English cues and names stay. |
| 4 | Google OAuth verification (Drive app folder) and the Play Health Connect / health-apps declarations | The Health apps declaration (with the Health Connect questions) is required for every app and its review time is not published; file it with the first closed-test build ([Play-Health.md](Play-Health.md), S-04). Google sign-in for `drive.appdata` is a non-sensitive scope, so no sensitive-scope review is expected; checklist in [Google-OAuth.md](Google-OAuth.md) (S-03). |
| 5 | Household measures coverage | First set sourced from PMK 41/2014; "centong" and cooked dishes are our own flagged estimates until verified (C-05). |
| 6 | Bought and regional meal numbers | Stay flagged estimates and listed. |
| 7 | Unsourced rules | Carried into Rules.md (D-03) with ⚠ until closed. |
