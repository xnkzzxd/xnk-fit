// Checks the exercise figures (tasks C-08, C-08b). Usage: node tools/check-figures.mjs
// Fails on: unknown rig pattern, rig-local.json not equal to a fresh build of rig-local.src.json, a local
// variant whose animated joint angles differ from its parent, a local entry without source or angles, rig = null without a written reason, muscle code without a map region,
// map region missing from the figure, changed XNK build file (sha256), missing credits, raster images,
// or figure files over their share of the 20 MB app budget (Design §10).
// Prints, without failing: muscle codes the figure cannot tell apart, and where the XNK rig's own
// muscle list differs from exercises.json (the app always uses exercises.json).
import { createHash } from "node:crypto";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { build as buildLocal } from "../content/figures/build-rig-local.mjs";

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
const localSrc = json(new URL("rig-local.src.json", dir));
const local = json(new URL("rig-local.json", dir));
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

// 2. Rig patterns: each used one exists (XNK or local); each null has a reason.
if (JSON.stringify(buildLocal(localSrc, rig)) !== JSON.stringify(local))
  err("rig-local.json differs from a fresh build: run node content/figures/build-rig-local.mjs");
for (const k of Object.keys(local.pats ?? {})) if (rig.pats[k]) err(`rig-local.json: ${k} also exists in the XNK rig`);
const pats = { ...(rig.pats ?? {}), ...(local.pats ?? {}) };
const JOINTS = (p) => Object.keys(p.frames[0].j);
const moving = (p) => JOINTS(p).filter((k) => new Set(p.frames.map((f) => f.j[k])).size > 1);
for (const [k, d] of Object.entries(localSrc.patterns)) {
  if (!d.source?.trim()) err(`rig-local.src.json ${k}: no source`);
  if (!d.for?.length) err(`rig-local.src.json ${k}: no exercise in "for"`);
  for (const id of d.for ?? []) { const e = list.find((x) => x.id === id);
    if (!e) err(`rig-local.src.json ${k}: unknown exercise ${id}`); else if (e.rig !== k) err(`${id}: rig is ${e.rig}, rig-local.src.json says ${k}`); }
  if (d.joints) { if (!d.angles?.trim()) err(`rig-local.src.json ${k}: new pattern without "angles"`); continue; }
  const par = pats[d.rig], v = local.pats[k];
  if (!par) { err(`${k}: parent ${d.rig} unknown`); continue; }
  if (!d.variant?.trim()) err(`${k}: variant without a "variant" label`);
  if (v.frames.length !== par.frames.length) { err(`${k}: frame count differs from ${d.rig}`); continue; }
  const armJ = d.arms ? ["elbow", "shoulder"] : [], armS = d.arms ? ["ua", "fa"] : [];
  const mv = moving(par).filter((j) => !armJ.includes(j));
  if (d.arms && !d.armsNote?.trim() && moving(par).some((j) => armJ.includes(j))) err(`${k}: arms re-posed but ${d.rig} animates the arm`);
  v.frames.forEach((f, i) => {
    for (const j of mv) if (f.j[j] !== par.frames[i].j[j]) err(`${k} frame ${i}: ${j} ${f.j[j]} differs from ${d.rig} ${par.frames[i].j[j]}`);
    for (const sg of Object.keys(par.frames[i].s)) if (!armS.includes(sg) && JSON.stringify(f.s[sg]) !== JSON.stringify(par.frames[i].s[sg]))
      err(`${k} frame ${i}: segment ${sg} differs from ${d.rig}`);
  });
}
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
  else { used.add(e.rig); rigged++; if (noRig[e.id]) err(`${e.id}: has a rig and a no-rig entry`);
    if (local.pats?.[e.rig] && !localSrc.patterns[e.rig]?.for?.includes(e.id)) err(`${e.id}: not listed in "for" of local rig ${e.rig}`); }
}
const ids = new Set(list.map((e) => e.id));
for (const id of Object.keys(noRig)) if (!ids.has(id)) err(`no-rig.json: unknown exercise ${id}`);

// 3. Muscles: every code maps to regions that the muscle map draws (both sexes), schema 2 (Content §5.2):
// "region" = both views, "front:region" / "back:region" = one view, a split name = listed paths of another region.
if (mm.schema !== 2) err(`muscle-map.json: schema ${mm.schema}, checker expects 2`);
const split = mm.split ?? {};
for (const [name, sp] of Object.entries(split)) for (const s of ["m", "f"]) {
  const all = fig.views?.[s]?.[sp.view]?.parts?.[sp.from] ?? [], ix = sp.paths?.[s] ?? [];
  if (!ix.length || ix.some((i) => !Number.isInteger(i) || i < 0 || i >= all.length)) err(`split ${name} (${s}): bad path indices`);
  else if (ix.length === all.length) err(`split ${name} (${s}): takes every path of ${sp.from}`);
  const sha = createHash("sha256").update(ix.map((i) => all[i]).join("\n")).digest("hex");
  if (sha !== sp.sha256?.[s]) err(`split ${name} (${s}): sha256 of the listed paths differs (figure changed?)`);
}
const parse = (ref) => { const m = /^(front|back):(.+)$/.exec(ref); return m ? { views: [m[1]], region: m[2] } : { views: ["front", "back"], region: ref }; };
const figHas = (s, ref) => { const { views, region } = parse(ref), sp = split[region];
  if (sp) return views.includes(sp.view) && !!fig.views?.[s]?.[sp.view]?.parts?.[sp.from];
  const has = views.map((v) => !!fig.views?.[s]?.[v]?.parts?.[region]);
  return ref.includes(":") ? has.every(Boolean) : has.some(Boolean); };
const rigRegion = (ref) => { const { region } = parse(ref); return split[region]?.from ?? region; };
const rigRegions = new Set([...(rig.muscles ?? []).map((m) => m[1]),
  ...Object.values(pats).flatMap((p) => (p.fm ?? []).map((m) => m[1]))]);
const codes = new Set([...(ex.muscles ?? []), ...list.flatMap((e) => [...e.muscles.main, ...e.muscles.helper])]);
for (const c of codes) {
  const rs = mm.map[c];
  if (!rs?.length) { err(`muscle ${c}: not in muscle-map.json`); continue; }
  for (const r of rs) {
    if (!figHas("m", r) || !figHas("f", r)) err(`muscle ${c}: region "${r}" not drawn in the muscle map (m: ${figHas("m", r)}, f: ${figHas("f", r)})`);
    if (!rigRegions.has(rigRegion(r))) err(`muscle ${c}: region "${r}" has no mark on the rig`);
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
    for (const r of mm.map[c] ?? []) { const g = rigRegion(r); lv[g] = Math.min(lv[g] ?? 9, l); }
  return lv; };
// An exercise is ambiguous on a figure when two of its codes land on one region at different levels.
const clash = (e, toRegions) => { const lv = {};
  for (const [cs, l] of [[e.muscles.main, 1], [e.muscles.helper, 2]]) for (const c of cs)
    for (const r of new Set((mm.map[c] ?? []).flatMap(toRegions))) (lv[r] ??= new Set()).add(l);
  return Object.values(lv).some((x) => x.size > 1); };
const mapRegions = (ref) => { const { views, region } = parse(ref); return views.map((v) => `${v}:${region}`); };
const mapClash = list.filter((e) => clash(e, mapRegions)).length, rigClash = list.filter((e) => clash(e, (r) => [rigRegion(r)])).length;
const nLocal = list.filter((e) => local.pats?.[e.rig]).length;
console.log(`exercises: ${list.length} | with a rig: ${rigged} (XNK ${rigged - nLocal}, local ${nLocal}: ${list.filter((e) => local.pats?.[e.rig]?.rig).length} variants, ${list.filter((e) => local.pats?.[e.rig] && !local.pats[e.rig].rig).length} new patterns) | without: ${list.length - rigged}`);
const kinds = {}; for (const r of Object.values(noRig)) kinds[r.kind] = (kinds[r.kind] ?? 0) + 1;
console.log("without rig by kind:", JSON.stringify(kinds));
console.log(`rig patterns used: ${used.size}/${Object.keys(pats).length} (local entries: ${Object.keys(local.pats ?? {}).length}); unused: ${Object.keys(pats).filter((p) => !used.has(p)).join(", ") || "none"}`);
console.log(`figure files: ${total} bytes (${(100 * total / BUDGET).toFixed(2)} % of the 20 MB app budget); raster images: ${raster}`);
console.log(`muscle codes: ${codes.size}, unmapped: ${[...codes].filter((c) => !mm.map[c]).join(", ") || "none"}. Shared regions: map ${Object.entries(mm.shared.map).map(([r, cs]) => `${r} <- ${cs.join("/")}`).join("; ")}; rig ${Object.entries(mm.shared.rig).map(([r, cs]) => `${r} <- ${cs.join("/")}`).join("; ")}`);
console.log(`exercises with two codes at different levels on one region (figure cannot show the difference): muscle map ${mapClash}, rig ${rigClash}`);
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
