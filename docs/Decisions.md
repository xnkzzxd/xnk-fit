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
