// Checks content/badges.json (task C-10). Usage: node tools/check-badges.mjs [file]
import { readFileSync } from "node:fs";

const file = process.argv[2] ?? new URL("../content/badges.json", import.meta.url);
const doc = JSON.parse(readFileSync(file, "utf8"));
const list = doc.badges ?? [];
const metrics = doc.metrics ?? {};
const ICONS = ["home","dumbbell","food","timer","scale","run","camera","bell","share","star","flame","cal",
  "trophy","heart","leaf","steps","chart","ruler","medal","check","cart","pin","sun","phone"]; // docs/mockups/mock.js, minus "lock" (locked state)
const OPS = [">=", ">", "=="];
const FIELDS = {
  workout: ["endedAt","startedAt"], workout_set: ["exerciseId","weightKg","reps","done","doneAt","workoutId","bandLevel","kind","form"],
  activity: ["minutes","effort","at"], food_log: ["day","kcal"], points_event: ["kind","points"],
  measure: ["takenAt"], photo: ["takenAt"], meal_plan: ["weekStart","day"],
};
// Tone rules: Rules R_TONE, R_TONE_WORDS, PRD PR-4/PR-5. Whole-word, case-insensitive.
const BANNED = ["kamu","kak","gagal","harus","jangan","malas","gemuk","kurus","lemak","diet","defisit","berat badan",
  "kg","kilo","turun","menurun","kalori","kkal","kcal","puasa","hukuman","bakar","terbakar","target gagal"];
const EMOJI = /\p{Extended_Pictographic}/gu;

const errors = [];
const err = (m) => errors.push(m);
if (list.length !== 18) err(`need exactly 18 badges, found ${list.length}`);
const seen = new Set(), seenTitle = new Set(), seenOrder = new Set();

for (const b of list) {
  const w = b.code ?? "(no code)";
  if (!/^[A-Z][A-Z0-9_]+$/.test(b.code ?? "")) err(`${w}: bad code format`);
  if (seen.has(b.code)) err(`${w}: duplicate code`);
  seen.add(b.code);
  if (!Number.isInteger(b.order) || b.order < 1 || b.order > 18) err(`${w}: order must be 1-18`);
  if (seenOrder.has(b.order)) err(`${w}: duplicate order ${b.order}`);
  seenOrder.add(b.order);
  const t = (b.title_id ?? "").trim();
  if (!t) err(`${w}: empty title_id`);
  if (seenTitle.has(t.toLowerCase())) err(`${w}: duplicate title "${t}"`);
  seenTitle.add(t.toLowerCase());
  if (!ICONS.includes(b.icon)) err(`${w}: unknown icon "${b.icon}"`);
  const c = b.condition ?? {};
  if (!metrics[c.metric]) err(`${w}: metric "${c.metric}" is not defined in metrics`);
  if (!OPS.includes(c.op)) err(`${w}: bad op`);
  if (!Number.isFinite(c.value) || c.value <= 0) err(`${w}: value must be a positive number`);
  if (!c.text_en?.trim()) err(`${w}: empty condition text_en`);
  if (!c.tables?.length || !c.fields?.length) err(`${w}: condition must name Room tables and fields`);
  for (const tb of c.tables ?? []) if (!FIELDS[tb]) err(`${w}: table "${tb}" not in the data model used here`);
  for (const f of c.fields ?? []) {
    const [tb, col] = f.split(".");
    if (!FIELDS[tb]?.includes(col)) err(`${w}: field "${f}" not in Design section 3`);
    if (!c.tables?.includes(tb)) err(`${w}: field "${f}" table not listed in tables`);
  }
  const text = JSON.stringify([b.title_id, b.message_id, c.text_en]);
  if (/weight|berat|kg|lose|loss|fat/i.test(c.metric ?? "")) err(`${w}: metric looks weight-related`);
  // Message rules
  const m = (b.message_id ?? "").trim();
  if (!m) err(`${w}: empty message_id`);
  if (m.length > 110) err(`${w}: message too long (${m.length} > 110)`);
  if ((m.match(EMOJI) ?? []).length > 1) err(`${w}: more than one emoji`);
  if ((m.match(/!/g) ?? []).length > 1) err(`${w}: more than one "!"`);
  for (const word of BANNED) {
    const re = new RegExp(`(^|[^\\p{L}])${word}([^\\p{L}]|$)`, "iu");
    if (re.test(m) || re.test(t)) err(`${w}: banned word "${word}" in title or message`);
  }
  if (!/[.!?🎉🏆🔥]$/u.test(m)) err(`${w}: message should end with punctuation or its one emoji`);
}
for (const k of Object.keys(metrics)) {
  if (!list.some((b) => b.condition?.metric === k)) err(`metric "${k}" is unused`);
}
// Safety (PR-5): the high activity badge stays at the WHO upper range, no daily-volume badges.
const act = list.filter((b) => b.condition?.metric === "activityMinutesWeek");
if (act.some((b) => b.condition.value > 300)) err("activity badge above 300 minutes per week (PR-5)");

console.log(`badges: ${list.length}`);
console.log("by metric:", JSON.stringify(list.reduce((a, b) => ((a[b.condition?.metric] = (a[b.condition?.metric] ?? 0) + 1), a), {})));
console.log("emoji used:", list.filter((b) => EMOJI.test(b.message_id) && ((EMOJI.lastIndex = 0), true)).length, "of", list.length);
if (errors.length) { console.error(`\nFAILED: ${errors.length} problem(s)`); errors.forEach((m) => console.error("  - " + m)); process.exit(1); }
console.log("\nOK: all checks passed");
