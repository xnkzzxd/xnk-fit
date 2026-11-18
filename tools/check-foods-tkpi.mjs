#!/usr/bin/env node
// Checks content/foods-tkpi.json (task C-03). Usage: node tools/check-foods-tkpi.mjs [file]
// Hard errors = parser or schema defects. The `flags` on a row mark disagreement INSIDE the printed TKPI table
// (copied as printed, not corrected); the checker verifies the flags are right and that they stay rare.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const file = process.argv[2] || path.join(here, '..', 'content', 'foods-tkpi.json');
const errors = [];
const err = (id, m) => errors.push(`${id}: ${m}`);

let data;
try { data = JSON.parse(fs.readFileSync(file, 'utf8')); }
catch (e) { console.error(`Cannot read ${file}: ${e.message}`); process.exit(2); }

if (data.schema !== 1 || !Number.isInteger(data.version)) err('file', 'schema/version missing');
const foods = Array.isArray(data.foods) ? data.foods : [];
const hand = Array.isArray(data.needsHandCheck) ? data.needsHandCheck : [];
if (foods.length < 1000 || foods.length > 1250) err('file', `row count ${foods.length} not near 1,100 (1000..1250)`);
if (data.counts?.rows !== foods.length) err('file', 'counts.rows differs from foods length');
if (data.counts?.needsHandCheck !== hand.length) err('file', 'counts.needsHandCheck differs from list length');

const REQUIRED = ['id', 'code', 'source', 'name_id', 'name_en', 'group', 'groupName', 'processing', 'kcalPer100', 'protein', 'carb',
  'fat', 'fibre', 'sodium', 'micro', 'bdd', 'sourceRef', 'page', 'household', 'isEstimate', 'halal', 'allergens', 'flags'];
const MICRO = ['water', 'ash', 'calcium', 'phosphorus', 'iron', 'potassium', 'copper', 'zinc', 'retinol', 'betaCarotene',
  'totalCarotene', 'thiamin', 'riboflavin', 'niacin', 'vitC'];
const MAX = { // per 100 g edible portion; anything above is physically impossible or a shifted column
  kcalPer100: 920, protein: 100, carb: 100, fat: 100, fibre: 100, water: 100, ash: 100, bdd: 100,
  sodium: 40000, calcium: 5000, phosphorus: 5000, iron: 300, potassium: 10000, copper: 50, zinc: 100,
  retinol: 30000, betaCarotene: 60000, totalCarotene: 100000, thiamin: 20, riboflavin: 20, niacin: 200, vitC: 3000,
};
const seen = new Set();
const codes = new Set();
let implausible = 0, flagged = 0, kcalFlag = 0, sumFlag = 0, nullFibre = 0, nullBdd = 0;
const flagWrong = [];

for (const f of foods) {
  const id = f.id || '(no id)';
  for (const k of REQUIRED) if (!(k in f)) err(id, `missing field ${k}`);
  if (seen.has(f.id)) err(id, 'duplicate id'); seen.add(f.id);
  if (codes.has(f.code)) err(id, 'duplicate TKPI code'); codes.add(f.code);
  if (!/^[A-Z]{2}\d{3}$/.test(f.code || '') || f.id !== `TKPI_${f.code}`) err(id, 'code/id format');
  if (f.processing !== (f.code?.[1] === 'R' ? 'raw' : 'processed')) err(id, 'processing does not match code letter');
  if (f.source !== 'tkpi') err(id, 'source must be tkpi');
  if (f.isEstimate !== false) err(id, 'isEstimate must be false');
  if (!f.name_id || typeof f.name_id !== 'string' || f.name_id.length < 2 || f.name_id.length > 90) err(id, 'name_id missing or odd length');
  if (/OLAHAN|PRODUK|KOMPOSIT|SINGLE/.test(f.name_id || '')) err(id, 'name_id holds a sub-heading word');
  if (!Number.isInteger(f.page) || f.page < 15 || f.page > 148) err(id, 'page out of range');
  if (f.household === undefined || !Array.isArray(f.household)) err(id, 'household must be an array');
  if (!f.sourceRef) err(id, 'sourceRef (SUMBER column) missing');
  const m = f.micro || {};
  const all = { kcalPer100: f.kcalPer100, protein: f.protein, carb: f.carb, fat: f.fat, fibre: f.fibre, sodium: f.sodium, bdd: f.bdd,
    ...Object.fromEntries(MICRO.map(k => [k, m[k]])) };
  for (const k of ['kcalPer100', 'protein', 'carb', 'fat']) if (typeof all[k] !== 'number') err(id, `${k} must be a number`);
  for (const k of MICRO) if (!(k in m)) err(id, `micro.${k} missing`);
  for (const [k, v] of Object.entries(all)) {
    if (v === null || v === undefined) continue;
    if (typeof v !== 'number' || !isFinite(v)) { err(id, `${k} not a number`); continue; }
    if (v < 0) err(id, `${k} negative`);
    if (MAX[k] !== undefined && v > MAX[k] && !f.flags?.includes(`implausible:${k}`)) err(id, `${k} ${v} > ${MAX[k]} (impossible or shifted column) and not flagged`);
    if (MAX[k] !== undefined && v <= MAX[k] && f.flags?.includes(`implausible:${k}`)) err(id, `flag implausible:${k} but value is in range`);
  }
  if (f.fibre === null) nullFibre++;
  if (f.bdd === null) nullBdd++;
  if (typeof f.kcalPer100 !== 'number') continue;
  // shifted-column detectors
  if (f.bdd !== null && f.bdd <= 0) err(id, 'bdd must be > 0 when present');
  if ((f.protein + f.carb + f.fat > 100.5) !== !!f.flags?.includes('macrosOver100')) err(id, 'protein+carb+fat > 100 g per 100 g not matching flag macrosOver100');
  // Atwater 4/4/9 (Rules S9). TKPI also uses food-specific factors (e.g. fibre, alcohol, some legumes and
  // oils), so a wide band; rows outside it must carry the flag 'kcalVsMacros'.
  const calc = 4 * f.protein + 4 * f.carb + 9 * f.fat;
  const kcalOff = Math.abs(calc - f.kcalPer100) > Math.max(0.15 * f.kcalPer100, 10);
  if (kcalOff) kcalFlag++;
  if (kcalOff !== f.flags?.includes('kcalVsMacros')) flagWrong.push(`${id} kcal flag`);
  // water + protein + fat + carb + ash should be about 100 g; a shifted column breaks this
  const sum = (m.water ?? NaN) + f.protein + f.fat + f.carb + (m.ash ?? 0);
  const sumOff = !(Math.abs(sum - 100) <= 6);
  if (sumOff) sumFlag++;
  if (sumOff !== f.flags?.includes('proximateSum')) flagWrong.push(`${id} sum flag`);
  if (f.flags?.length) flagged++;
  implausible += f.flags.filter(x => x.startsWith('implausible:')).length;
  // hard failure: kcal off by more than 3x or near zero while macros are large (a clear mis-assignment)
  if (calc > 0 && (f.kcalPer100 > 3 * calc + 20 || calc > 3 * f.kcalPer100 + 20) && !f.flags?.includes('kcalVsMacros')) err(id, 'kcal vs macros wildly off and not flagged');
}
flagWrong.forEach(m => err('flags', m));
if (flagged > 0.10 * foods.length) err('file', `${flagged} flagged rows (> 10 %): parser probably shifting columns`);
if (nullFibre > 0.4 * foods.length) err('file', `fibre empty in ${nullFibre} rows: fibre column probably not read`);

// hand-check list must carry a page number and a reason, and must not overlap the table
for (const h of hand) {
  if (!h.reason || h.page === undefined) err('needsHandCheck', `entry without page/reason: ${JSON.stringify(h)}`);
  if (h.code && codes.has(h.code)) err(h.code, 'in both foods and needsHandCheck');
}

// code continuity per prefix: every number 001..max should be a row or in the hand list
const handCodes = new Set(hand.map(h => h.code));
const by = {};
for (const c of codes) (by[c.slice(0, 2)] ||= []).push(+c.slice(2));
let gaps = 0;
for (const [k, arr] of Object.entries(by)) {
  const have = new Set(arr);
  for (let i = 1; i <= Math.max(...arr); i++) if (!have.has(i) && !handCodes.has(`${k}${String(i).padStart(3, '0')}`)) { gaps++; err(k, `code ${k}${String(i).padStart(3, '0')} missing (neither row nor hand-check)`); }
}

console.log(`${file}\nrows: ${foods.length}, hand-check list: ${hand.length}, rows with source-inconsistency flags: ${flagged} (kcal vs 4/4/9: ${kcalFlag}, water+macros+ash sum: ${sumFlag}, implausible single values: ${implausible})`);
console.log(`fibre empty: ${nullFibre}, BDD empty: ${nullBdd}, code gaps: ${gaps}`);
if (errors.length) { console.log(`FAIL (${errors.length})`); errors.slice(0, 60).forEach(e => console.log(' - ' + e)); process.exit(1); }
console.log('OK');
