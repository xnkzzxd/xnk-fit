# Review: meal planner rules (B-10)

Review of the planner selection rules that a Sonnet session wrote in B-10 (Decisions.md "aturan planner perlu review Opus"). Reviewed 2026-11-19 by Opus 5.5. Scope: `domain/food/Planner.kt`, `domain/food/FoodRules.kt`, their tests, and how `ui/makan` feeds them.

## 1. What was checked

| Check | Against | Result |
| --- | --- | --- |
| Every planned day at or above the calorie floor | PR-5, R_PLAN_FLOOR, R_GATE_FLOOR | **Bug F1** (fixed) |
| Days that cannot reach the floor are marked | R_PLAN_FLOOR | **Bug F1b** (fixed) |
| Allergy, lactose and halal filters never broken by any automatic pick, option, eat-out or fallback | R_LIMIT_ALLERGY, R_HALAL, M-9 | **Bug F2** (fixed); otherwise holds: the pool is filtered before every step, the last-resort fallback only lifts R_LIMIT_SANTAN/GORENG/MAIN and the repeat cap |
| Options for a cell respect the floor | R_PLAN_FLOOR, R_OPTIONS | **Bug F3** (fixed) |
| Santan, fried and main-protein limits, repeat cap | R_LIMIT_*, R_PLAN_REPEAT | Hold in automatic picks; lifted only in the last-resort step (see §4) |
| Determinism | Decisions B-10 | Holds: `String.hashCode` and the seed (`weekStart.hashCode()`) are stable; no unordered iteration decides a pick. Checked on 300 random weeks |
| Budget is a weight only, no Rupiah claim | R_BUDGET, Content §4.5 ⚠ | Holds: only `budgetCost` reads it; no amount anywhere |
| Target from `domain.rules`, not recalculated | B-02, Design §7 | Holds: `foodTargetsFrom` → `evaluateTargets`; floor = max(R_FLOOR_F/M, BMR) |
| Under 18, pregnant | R_GATE_U18, R_GATE_PREG | Holds: the target comes from the gate (maintenance, no deficit); the planner adds nothing of its own |
| R_GATE_ED no-numbers mode | R_GATE_ED | Holds in the planner screen (`numbersHidden` hides day totals and option kcal); covered by `R_GATE_ED_noNumbersAnywhereInTheState` |
| Protein and macros | R_PROT | **Not checked by the planner** (risk R1) |
| Very low / very high target, all options blocked, fully fixed day ("libur") | edge cases | Covered by new tests; no crash, floor kept |

Tests: `PlannerReviewTest.kt` (7 edge cases + one seeded property test over 300 random pools: halal, allergens, milk→lactose, profiles, slot, one meal per cell, fixed cells kept, determinism, and "a day is marked exactly when it is under the floor, and only when no eligible combination could reach it"). The existing golden tests (`plannerGolden_*`) pass unchanged.

## 2. Findings and fixes

**F1 — floor missed although reachable.** The relaxation order (shortlist → whole pool → past repeat cap → past limits) moved on only when a step found *no* combination. A step that found combinations all below the floor ended the search, so the 6 cheapest-by-cost candidates could leave a day under the floor while a dearer package (budget tier above, or used before) reached it. Budget and variety are weights; the floor is a hard rule (PR-5), so the order was wrong. Fix: keep widening until a combination reaches the floor; if none does, keep the highest total that still respects the limits. Test: `floorIsReachedWhenOnlyPackagesOutsideTheShortlistReachIt`, property test.

**F1b — empty day not marked.** A day with no eligible option at all (for example every package blocked by an allergy) had 0 kcal and was not in `daysBelowFloor`, so the "plan is limited" note did not show. Fix: the mark is now computed from the final day totals. Test: `dayWithNoEligibleOptionIsMarkedBelowFloor`.

**F2 — milk allergy did not remove lactose-flagged packages.** The filter compared tags one to one. A package tagged `lactose` but not `milk` would be offered to a user who avoids milk. The current `meal-packages.json` has no such package (all 4 lactose packages also carry `milk`), but `foods-regional.json` (C-07) and later content can. Fix: `Set<Allergen>.withLactose()` — avoiding milk also avoids lactose; used in `eligible` and `allowedAsAutomaticOption`. The reverse (lactose → milk) is not applied: lactose-free milk products exist. Test: `milkAllergyAlsoRemovesLactosePackages`.

**F3 — options could pull the day under the floor.** The three options per cell were ranked by distance to the target only; with a target at the floor, an option 10 kcal under it ranked before one 20 kcal over it. Fix: options that keep the day at or above the floor come first; the user can still pick any (M-4). Test: `optionsKeepingTheDayAtTheFloorComeFirst`.

## 3. Read, not changed

- R_PLAN_FIT, R_PLAN_REPEAT, R_PLAN_BUY, R_PLAN_COOK_ONCE weights (6, 2/150, 100, 3 days) and R_BUDGET (300/20): reasonable as first values; budget above variety is a sound order for a "Hemat" user. They stay ⚠ (no source); they are preferences, not safety.
- Last-resort step (FREE) lifts R_LIMIT_SANTAN/GORENG/MAIN and the repeat cap only when the limits leave no combination, or when only that step reaches the floor. Allergy, halal and profile filters are never lifted. Order "floor before moderation limits" is a reading of PR-5 (recorded in Rules.md R_PLAN_FLOOR).
- Portions are not scaled (Decisions B-10). With today's packages a full day (one package per slot) ranges 1,549–2,127 kcal; a target above that gives the largest days, which is safe.
- `withCookOnce`, `withChoice`, `withEatOut` are user actions (M-4): they do not block on limits or the floor; eat-out still keeps halal and allergy filters.

## 4. Remaining risks

| # | Risk | Why it stays |
| --- | --- | --- |
| R1 | The planner does not look at protein. Package protein per day is about 45–112 g (mean ≈ 75 g); R_PROT for 60 kg is 84–120 g. Many planned days will be under the protein target | A protein weight would be a new unsourced number; needs an owner decision (and maybe portion scaling) |
| R2 | When no target exists (gates that end with no target, such as R_GATE_U13), `MakanViewModel` falls back to 1,750 / 1,500 kcal and the planner still runs with numbers shown | UI code is outside this task; see recommendation 2 |
| R3 | Worst case (floor unreachable, 18 packages per slot) runs the whole search up to three times: ≈ 0.1 s on the JVM here, more on a slow phone; `autoFill` runs on the main dispatcher | Not measured on a device (no emulator) |
| R4 | Only the first `mainProtein` counts for R_LIMIT_MAIN | Unchanged (Decisions B-10) |
| R5 | Santan/fried/main limits can be lifted in the last-resort step to reach the floor | Deliberate (floor first); shown by the "plan is limited" note only when the floor is still missed |

## 5. Recommendations for the owner

1. Decide whether the planner should aim at protein (a cost per gram under R_PROT, or portion scaling). Until then the protein target is guidance on the Hari ini card only. — **Dijawab pemilik 2026-11-19: YA**, aim at R_PROT (Decisions.md "B-10 owner").
2. Hide the week planner (or show it without numbers) when there is no target (U13, screening not done, risk answers), instead of the 1,750 / 1,500 defaults. — **Dijawab pemilik 2026-11-19: tampilkan tanpa angka** (planner stays, no kcal/grams/protein).
3. Run `planWeek` off the main thread (`Dispatchers.Default`) in `MakanViewModel.autoFill`. — **Dijawab pemilik 2026-11-19: YA.**
4. Keep R_PLAN_* ⚠ until a dietitian or the owner confirms the weights; the safety parts (floor, filters) are now tested hard rules.
