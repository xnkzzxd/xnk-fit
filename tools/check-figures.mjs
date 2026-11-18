// Checks the exercise figures (task C-08). Usage: node tools/check-figures.mjs
// Fails on: unknown rig pattern, rig = null without a written reason, muscle code without a map region,
// map region missing from the figure, changed XNK build file (sha256), missing credits, raster images,
// or figure files over their share of the 20 MB app budget (Design §10).
// Prints, without failing: muscle codes the figure cannot tell apart, and where the XNK rig's own
// muscle list differs from exercises.json (the app always uses exercises.json).
import { createHash } from "node:crypto";
import { readFileSync, readdirSync, statSync } from "node:fs";

const root = new URL("../", import.meta.url);
const dir = new URL("content/figures/", root);
const read = (u) => readFileSync(u, "utf8");
const json = (u) => JSON.parse(read(u));
const errors = [];
const err = (m) => errors.push(m);

const ex = json(new URL("content/exercises.json", root));
const fig = json(new URL("xnk-source/figure.min.json", dir));
const rig = json(new URL("xnk-source/rig.min.json", dir));
const mm = json(new URL("muscle-map.json", dir));
const noRig = json(new URL("no-rig.json", dir)).exercises;
const src = json(new URL("source.json", dir));
const list = ex.exercises;

// 1. XNK build files unchanged, credits kept.
for (const [name, meta] of Object.entries(src.files)) {
  const sha = createHash("sha256").update(readFileSync(new URL(name, dir))).digest("hex");
  if (sha !== meta.sha256) err(`${name}: sha256 ${sha} differs from source.json (build output edited by hand?)`);
}
if (!src.xnkCommit) err("source.json: XNK commit not recorded");
const credits = read(new URL("xnk-source/CREDITS.md", dir));
for (const s of ["react-muscle-highlighter", "MIT License", "My Muscle Contributors", "rig.min.json"])
  if (!credits.includes(s)) err(`xnk-source/CREDITS.md: missing "${s}"`);
const lic = read(new URL("xnk-source/LICENSE-react-muscle-highlighter.txt", dir));
if (!/MIT License/.test(lic) || !/My Muscle Contributors/.test(lic)) err("xnk-source/LICENSE-react-muscle-highlighter.txt: MIT licence text missing");
if (!/react-muscle-highlighter/.test(fig.credit ?? "")) err("figure.min.json: credit field missing");

// 2. Rig patterns: each used one exists; each null has a reason.
const pats = rig.pats ?? {};
const used = new Set();
let rigged = 0;
for (const e of list) {
  if (!("rig" in e)) { err(`${e.id}: no rig field`); continue; }
  if (e.rig === null) {
    const r = noRig[e.id];
    if (!r?.reason?.trim()) err(`${e.id}: rig is null and no-rig.json has no reason`);
    else if (!["derive", "new", "map-only"].includes(r.kind)) err(`${e.id}: bad no-rig kind ${r.kind}`);
    else if (r.kind === "derive" && !pats[r.base]) err(`${e.id}: base ${r.base} is not an XNK rig pattern`);
  } else if (!pats[e.rig]) err(`${e.id}: rig ${e.rig} not in rig.min.json`);
  else { used.add(e.rig); rigged++; if (noRig[e.id]) err(`${e.id}: has a rig and a no-rig entry`); }
}
const ids = new Set(list.map((e) => e.id));
for (const id of Object.keys(noRig)) if (!ids.has(id)) err(`no-rig.json: unknown exercise ${id}`);

// 3. Muscles: every code maps to regions that the muscle map draws (both sexes).
const regionsIn = (s) => new Set(["front", "back"].flatMap((v) => Object.keys(fig.views?.[s]?.[v]?.parts ?? {})));
const figM = regionsIn("m"), figF = regionsIn("f");
const rigRegions = new Set([...(rig.muscles ?? []).map((m) => m[1]),
  ...Object.values(pats).flatMap((p) => (p.fm ?? []).map((m) => m[1]))]);
const codes = new Set([...(ex.muscles ?? []), ...list.flatMap((e) => [...e.muscles.main, ...e.muscles.helper])]);
for (const c of codes) {
  const rs = mm.map[c];
  if (!rs?.length) { err(`muscle ${c}: not in muscle-map.json`); continue; }
  for (const r of rs) {
    if (!figM.has(r) || !figF.has(r)) err(`muscle ${c}: region "${r}" not drawn in the muscle map (m: ${figM.has(r)}, f: ${figF.has(r)})`);
    if (!rigRegions.has(r)) err(`muscle ${c}: region "${r}" has no mark on the rig`);
  }
}
for (const c of Object.keys(mm.map)) if (!(ex.muscles ?? []).includes(c)) err(`muscle-map.json: ${c} is not one of the 16 codes`);

// 4. Size and first frame (Design §10): vector data only, small share of the 20 MB budget.
const BUDGET = 20 * 1024 * 1024, FIG_SHARE = 1024 * 1024;
let total = 0, raster = 0;
for (const f of readdirSync(dir, { recursive: true })) {
  if (statSync(new URL(f, dir)).isDirectory()) continue;
  const size = statSync(new URL(f, dir)).size;
  total += size;
  if (/\.(png|jpe?g|gif|webp|bmp|avif|heic)$/i.test(f)) { raster++; err(`${f}: raster image in content/figures (vector only)`); }
  if (/data:image\//.test(read(new URL(f, dir)))) { raster++; err(`${f}: embedded raster image`); }
}
if (total > FIG_SHARE) err(`content/figures is ${total} bytes, over its ${FIG_SHARE} byte share`);

// 5. Report (no failure).
const level = (e) => { const lv = {}; // region -> 1 main, 2 helper
  for (const [codesOf, l] of [[e.muscles.main, 1], [e.muscles.helper, 2]]) for (const c of codesOf)
    for (const r of mm.map[c] ?? []) lv[r] = Math.min(lv[r] ?? 9, l);
  return lv; };
const sharedCodes = new Set(Object.values(mm.shared).flat());
const sharedHits = list.filter((e) => [...e.muscles.main, ...e.muscles.helper].some((c) => sharedCodes.has(c)));
console.log(`exercises: ${list.length} | with XNK rig: ${rigged} | without: ${list.length - rigged}`);
const kinds = {}; for (const r of Object.values(noRig)) kinds[r.kind] = (kinds[r.kind] ?? 0) + 1;
console.log("without rig by kind:", JSON.stringify(kinds));
console.log(`rig patterns used: ${used.size}/${Object.keys(pats).length}; unused: ${Object.keys(pats).filter((p) => !used.has(p)).join(", ") || "none"}`);
console.log(`figure files: ${total} bytes (${(100 * total / BUDGET).toFixed(2)} % of the 20 MB app budget); raster images: ${raster}`);
console.log(`muscle codes: ${codes.size}, unmapped: ${[...codes].filter((c) => !mm.map[c]).join(", ") || "none"}. Shared regions: ${Object.entries(mm.shared).map(([r, cs]) => `${r} <- ${cs.join("/")}`).join("; ")}`);
console.log(`exercises using a shared region (figure cannot show which head/part): ${sharedHits.length}`);
console.log("rigged exercises, XNK rig's own muscles vs exercises.json (app uses exercises.json):");
for (const e of list.filter((x) => pats[x.rig])) {
  const p = pats[e.rig], want = level(e), own = {};
  p.muscles[1].forEach((m) => { own[m] = 2; }); p.muscles[0].forEach((m) => { own[m] = 1; });
  const diff = [...new Set([...Object.keys(want), ...Object.keys(own)])].filter((r) => want[r] !== own[r])
    .map((r) => `${r} ${own[r] ? (own[r] === 1 ? "main" : "helper") : "-"}→${want[r] ? (want[r] === 1 ? "main" : "helper") : "-"}`);
  const marks = p.view === "front" ? new Set((p.fm ?? []).map((m) => m[1])) : new Set((rig.muscles ?? []).map((m) => m[1]));
  const hidden = Object.keys(want).filter((r) => !marks.has(r));
  console.log(`  ${e.id.padEnd(18)} ${e.rig.padEnd(22)} ${diff.length ? diff.join(", ") : "same"}${hidden.length ? ` | no mark in this view: ${hidden.join(", ")}` : ""}`);
}
if (errors.length) { console.error(`\nFAILED: ${errors.length} problem(s)`); errors.forEach((m) => console.error("  - " + m)); process.exit(1); }
console.log("\nOK: all checks passed");
