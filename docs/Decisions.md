# Decisions taken by Claude while the owner is away

The owner asked on 2026-11-18 that all sessions keep working without them. When a session needs a decision that is the owner's, it takes the safest option (PR-5, Rules), writes one row here, marks the affected text ⚠ if a number or rule is involved, and continues. The owner can reverse any row; the "Undo" column says how.

Rules for sessions:
1. Never wait for the owner. Never stop to ask. Choose the most conservative option, record it, go on.
2. Never loosen a safety limit (calorie floor, age, pregnancy, screening) and never invent a source or a number. A missing source stays ⚠.
3. Owner-only actions are not done and not faked: Play developer account (S-08), signing keys, purchases, trademark filing, store submissions. Prepare the files and a checklist, mark the step "owner".
4. A blocked tool or network is retried later; meanwhile do the rest of the task and report the block in the final message and in TODO.
5. Finish each task with checks run, TODO updated, commit and push to `claude/bold-cori-69oc96` (merge first; no PR, no force-push, no rebase).

| Date | Session / task | Decision | Why | Undo |
| --- | --- | --- | --- | --- |
| 2026-11-18 | Orchestrator, C-10 | `TARGET_WEEK` replaced by `LOGWEEK_1` | Owner choice | n/a |
| 2026-11-18 | Orchestrator, Rules | `R_PTS_TARGET` points now follow weeks with food logged on 5+ days | Keep points consistent with the badge; no calorie number rewarded (PR-5) | Restore the ±10 % wording in Rules §6 and the `target` kind in Design §3 |
| 2026-11-18 | B-01 | Lint check `OldTargetApi` turned off | Fires when the CI runner gets a newer platform; target SDK is a recorded decision (Design §1) | Remove the line in `app/build.gradle.kts` |
| 2026-11-18 | C-08 | An exercise gets an XNK rig only when the rig shows the same lift with the same setup (20); the other 129 stay `rig = null` with a reason in `content/figures/no-rig.json` | A borrowed rig would show wrong equipment or angles not sourced for that lift | Set `rig` on the entry; the checker accepts any pattern in `rig.min.json` |
| 2026-11-18 | C-08 | Muscle map: figure region `upper back` moved from `LATS` to `TRAPS` (mockup had `LATS`) | That area is middle trapezius and rhomboids, not latissimus | Move `upper back` back to `LATS` in `content/figures/muscle-map.json` |
| 2026-11-18 | C-08 | `PUSHV_DB` keeps the seated XNK `db-shoulder-press` rig although the entry lists no bench ⚠ | Same arm path; seated is the safer default | Set `rig: null` and add a `derive` row in `no-rig.json` |
| 2026-11-18 | C-08 | 7 cardio entries proposed `map-only` (muscle map and cues, no rig) | A 13-frame loop teaches little for gait, pedalling, rowing, rope | Change their `kind` to `new` in `no-rig.json` |
| 2026-11-18 | B-02 | Age gates (R_GATE_U13, R_GATE_U18) use the youngest possible age: current year − birth year − 1 (Rules R_AGE, §12 #9). A user born in 2008 keeps the under-18 rules (no deficit, light mode) through 2026; born 2013 is stopped as under 13 | Birth year alone can overstate age by up to a year; a 17-year-old must not get a deficit (PR-5) | Set `R_AGE_GATE_MARGIN_YEARS` to 0 in `domain/rules/BodyRules.kt`, or ask birth month and compute the exact age |
| 2026-11-18 | B-02 | Readings of rules written into Rules.md without changing values: gate order; R_DEF_CAP applies to risk answers; floor rounded up; protein steps to 1.4 g/kg only when carbohydrate would go negative; R_PACE needs both weeks down and an average over 1 %/week; R_TRN_RETURN load rounded down to the equipment step. New IDs for numbers that had none: R_INPUT (plausibility limits, 👤), R_GATE_U13, R_KCAL_G, R_RECALC, R_MET, R_WHO_MIN, R_TRN_LEVELS | PR-6: each number needs an ID; the code needed one reading of each rule | Edit the row in Rules.md and the matching constant or function in `domain` |
| 2026-11-19 | B-04 | Three small tables added to Room schema v1 (`content_meta`, `content_doc`, `meal_package`); `app/schemas/.../1.json` regenerated, no migration | B-03 had no table for meal packages, badge definitions or the installed content version; v1 is unreleased | Drop the tables from `Entities.kt`/`XnkDatabase.kt`, regenerate the schema JSON, keep the loader's file-based variant |
| 2026-11-19 | B-04 | Public key in the app is a placeholder whose private half was never stored; `PUBLIC_KEY_IS_PLACEHOLDER = true` turns the update check off | Rule 3: signing keys are the owner's; no unsigned or test-signed update may ever pass | Owner pastes their key in `ContentConfig.kt` and sets the flag to false (Content-Update.md §7) |
| 2026-11-19 | B-04 | Packaged rows with no per-100 g basis (serving in ml, no grams) are not loaded: 1 of 2 rows today | Rule 2: no density is invented | Add `grams` or `per100g` to the row in `foods-packaged.json` |
| 2026-11-19 | B-04 | Exercises with `active=false` are not loaded; `name_id` falls back to `name_en` (the file has no Indonesian names yet) | Table has no `active` column; a retired exercise must not be offered | Add the column in a later schema version |
| 2026-11-19 | B-04 | Base URL `https://xnkzzxd.github.io/xnk-fit-content/` (owner's GitHub account + repo name from S-06) | Content-Update.md §1 names the repo `xnk-fit-content`; the account is the one that owns this repo | Edit `ContentConfig.BASE_URL` |

