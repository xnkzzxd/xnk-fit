// Checks content/exercises.json (tasks C-01 and C-02). Usage: node tools/check-exercises.mjs [file]
import { readFileSync } from "node:fs";

const file = process.argv[2] ?? new URL("../content/exercises.json", import.meta.url);
const doc = JSON.parse(readFileSync(file, "utf8"));
const MUSCLES = ["CHEST","FRONT_DELT","SIDE_DELT","REAR_DELT","BICEPS","TRICEPS","LATS","TRAPS",
  "QUADS","HAMS","GLUTES","CALVES","ABS","OBLIQUES","LOWER_BACK","FOREARM"];
const SETTINGS = ["gym", "home-bw", "home-db"];
// Content.md §3: settings in which each pattern must have at least 3 choices.
const NEEDS = {
  SQUAT: SETTINGS, HINGE: SETTINGS, PUSH_H: SETTINGS, PUSH_V: SETTINGS, PULL_H: SETTINGS,
  PULL_V: SETTINGS, CURL: ["gym", "home-db"], TRI: SETTINGS, LEG_ISO: ["gym"],
  CORE: SETTINGS, CARRY: ["gym", "home-db"], CARDIO: SETTINGS,
};
const errors = [];
const err = (m) => errors.push(m);
const list = doc.exercises ?? [];
const seenId = new Set(), seenName = new Set();

for (const e of list) {
  const w = e.id ?? "(no id)";
  if (!/^[A-Z][A-Z0-9_]+$/.test(e.id ?? "")) err(`${w}: bad id format`);
  if (seenId.has(e.id)) err(`${w}: duplicate id`);
  seenId.add(e.id);
  const key = (e.name_en ?? "").trim().toLowerCase();
  if (!key) err(`${w}: empty name`);
  if (seenName.has(key)) err(`${w}: duplicate name "${e.name_en}"`);
  seenName.add(key);
  if (!/\([^)]+\)$/.test(e.name_en ?? "") && e.group !== "cardio-core") err(`${w}: name has no equipment in parentheses`);
  if (!NEEDS[e.pattern]) err(`${w}: unknown pattern ${e.pattern}`);
  if (![1, 2, 3].includes(e.level)) err(`${w}: level must be 1-3`);
  if (!e.settings?.length || e.settings.some((s) => !SETTINGS.includes(s))) err(`${w}: bad settings`);
  if (!e.equipment?.length) err(`${w}: no equipment codes`);
  const main = e.muscles?.main ?? [], helper = e.muscles?.helper ?? [];
  if (!main.length) err(`${w}: no main muscle`);
  for (const m of [...main, ...helper]) if (!MUSCLES.includes(m)) err(`${w}: muscle outside the 16: ${m}`);
  if (main.some((m) => helper.includes(m))) err(`${w}: muscle is both main and helper`);
  if (!Array.isArray(e.cues_en) || e.cues_en.length !== 3 || e.cues_en.some((c) => !c?.trim())) err(`${w}: need exactly 3 non-empty cues`);
  if (!e.hint_id?.trim()) err(`${w}: empty hint_id`);
  if (!e.source?.pattern || !e.source?.muscles_cues) err(`${w}: missing source`);
}
const ids = new Set(list.map((e) => e.id));
for (const e of list) {
  if (e.variantOf && !ids.has(e.variantOf)) err(`${e.id}: variantOf ${e.variantOf} does not exist`);
  let x = e, n = 0;
  while (x?.variantOf && n++ < 50) x = list.find((y) => y.id === x.variantOf);
  if (n >= 50) err(`${e.id}: variantOf cycle`);
}
const counts = {};
for (const e of list) if (e.active !== false) for (const s of e.settings ?? []) {
  counts[e.pattern] ??= {}; counts[e.pattern][s] = (counts[e.pattern][s] ?? 0) + 1;
}
for (const [p, settings] of Object.entries(NEEDS)) for (const s of settings) {
  const n = counts[p]?.[s] ?? 0;
  if (n < 3) err(`pattern ${p} has only ${n} choice(s) in ${s} (need 3)`);
}

const groups = {}, status = {}, review = {};
for (const e of list) {
  groups[e.group] = (groups[e.group] ?? 0) + 1;
  status[e.source?.status] = (status[e.source?.status] ?? 0) + 1;
  review[e.review?.muscles] = (review[e.review?.muscles] ?? 0) + 1;
}
console.log(`entries: ${list.length}`);
console.log("groups:", JSON.stringify(groups));
console.log("source status:", JSON.stringify(status), "| muscle review:", JSON.stringify(review));
console.log("choices per pattern x setting:");
for (const p of Object.keys(NEEDS)) console.log(`  ${p.padEnd(8)}`, SETTINGS.map((s) => `${s}=${counts[p]?.[s] ?? 0}`).join("  "));
if (errors.length) { console.error(`\nFAILED: ${errors.length} problem(s)`); errors.forEach((m) => console.error("  - " + m)); process.exit(1); }
console.log("\nOK: all checks passed");
