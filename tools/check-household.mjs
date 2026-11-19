#!/usr/bin/env node
// Checks content/household.json (task C-05). Usage: node tools/check-household.mjs [file]
// - every foodId exists in foods-tkpi.json or foods-estimates.json and the name matches
// - grams plausible: 1..1000 g per measure and inside a per-unit range (own limits here)
// - source is pmk-41-2014 (with pmkRow + page that exist and match) or estimate (flag ⚠ + method + spread)
// - display shows the label and "±" grams (R_HH)
// - PMK rows marked suspect are not attached to any food; no pork item
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(here, '..');
const file = process.argv[2] || path.join(root, 'content', 'household.json');
const errors = [];
const warns = [];
const err = (id, m) => errors.push(`${id}: ${m}`);

let data;
try { data = JSON.parse(fs.readFileSync(file, 'utf8')); }
catch (e) { console.error(`Cannot read ${file}: ${e.message}`); process.exit(2); }
const load = f => JSON.parse(fs.readFileSync(path.join(root, 'content', f), 'utf8')).foods;
const foodsAll = new Map([...load('foods-tkpi.json'), ...load('foods-estimates.json')].map(f => [f.id, f]));

// Independent limits (g for one unit). Kept here, not read from the file under test.
const PER_UNIT = {
  centong: [50, 200], piring: [100, 500], mangkok: [100, 500], gelas: [40, 260],
  sdm: [3, 25], sdt: [1, 8], potong: [5, 300], iris: [10, 60], butir: [5, 80],
  buah: [1, 1500], biji: [1, 300], ekor: [3, 600], lembar: [5, 60], papan: [5, 60], genggam: [10, 80], bungkus: [20, 600],
};
const LIQUID = /susu|sari kedelai|jus|air jeruk|sirup/i;
const PORK = /babi|\bham\b|pork|lard/i;
const PAGES = new Set([8, 9, 10, 11, 12, 13, 14, 15, 16, 37, 41, 89]);

if (data.schema !== 1 || !Number.isInteger(data.version)) err('file', 'schema/version');
if (!data.sources?.['pmk-41-2014']?.url || !/^[0-9a-f]{64}$/.test(data.sources['pmk-41-2014'].sha256 || '')) err('file', 'PMK source url/sha256 missing');

const rows = new Map();
for (const r of data.pmkRows || []) {
  if (rows.has(r.id)) err(r.id, 'duplicate pmkRow'); rows.set(r.id, r);
  if (!PAGES.has(r.page)) err(r.id, `page ${r.page} is not a URT table page`);
  if (!r.urt || !r.name || !(r.qty > 0) || !(r.grams > 0)) err(r.id, 'pmkRow incomplete');
  if (!PER_UNIT[r.unit]) err(r.id, `unknown unit ${r.unit}`);
  if (Math.abs(r.grams / r.qty - r.perUnitGrams) > Math.max(0.06, 0.005 * r.perUnitGrams)) err(r.id, 'perUnitGrams does not equal grams/qty');
  if (r.suspect && r.mappedTo.length) err(r.id, 'suspect row is attached to a food');
  if (PORK.test(r.name) && r.mappedTo.length) err(r.id, 'pork item attached (R_HALAL)');
  const pu = r.grams / r.qty, [lo, hi] = PER_UNIT[r.unit] || [0, Infinity];
  if (!r.suspect && (pu < lo || pu > hi)) err(r.id, `${pu.toFixed(1)} g per ${r.unit} outside ${lo}-${hi} but not marked suspect`);
  if (!r.suspect && r.unit === 'gelas' && LIQUID.test(r.name) && pu < 150) err(r.id, `liquid ${pu.toFixed(0)} g per gelas not marked suspect`);
  if (r.gramsRange && !(r.gramsRange[0] < r.gramsRange[1] && r.grams === (r.gramsRange[0] + r.gramsRange[1]) / 2)) err(r.id, 'bad gramsRange');
}

const seenFood = new Set();
let nPmk = 0, nEst = 0;
for (const f of data.foods || []) {
  const id = f.foodId;
  if (seenFood.has(id)) err(id, 'duplicate foodId'); seenFood.add(id);
  const food = foodsAll.get(id);
  if (!food) { err(id, 'foodId not in foods-tkpi.json or foods-estimates.json'); continue; }
  if (food.name_id !== f.name_id) err(id, `name differs from food table ("${food.name_id}")`);
  if (PORK.test(food.name_id)) err(id, 'pork food (R_HALAL)');
  if (!Array.isArray(f.measures) || !f.measures.length) err(id, 'no measures');
  const keys = new Set();
  for (const m of f.measures || []) {
    const mid = `${id} [${m.label}]`;
    const key = `${m.code}|${m.label}|${m.source}`;
    if (keys.has(key)) err(mid, 'duplicate measure'); keys.add(key);
    if (!PER_UNIT[m.code]) { err(mid, `unknown unit code ${m.code}`); continue; }
    if (!(m.grams >= 1 && m.grams <= 1000)) err(mid, `grams ${m.grams} outside 1-1000`);
    if (!(m.qty > 0)) err(mid, 'qty must be > 0');
    const pu = m.grams / m.qty, [lo, hi] = PER_UNIT[m.code];
    if (pu < lo || pu > hi) err(mid, `${pu.toFixed(1)} g per ${m.code} outside ${lo}-${hi}`);
    if (m.code === 'gelas' && LIQUID.test(food.name_id) && pu < 150) err(mid, `liquid ${pu.toFixed(0)} g per gelas`);
    // R_HH: label first, grams beside with "±"
    if (typeof m.display !== 'string' || !m.display.startsWith(m.label) || !/\(±\d/.test(m.display) || !m.display.includes(' g)')) err(mid, `display "${m.display}" must be "<label> (±<g> g)"`);
    if (m.source === 'pmk-41-2014') {
      nPmk++;
      if (m.flag !== null) err(mid, 'PMK measure must have flag null');
      const r = rows.get(m.ref?.pmkRow);
      if (!r) { err(mid, 'PMK measure without a pmkRow'); continue; }
      if (r.page !== m.ref.page) err(mid, 'page differs from pmkRow');
      if (r.grams !== m.grams || r.urt !== m.label || r.unit !== m.code) err(mid, 'measure differs from pmkRow as printed');
      if (!r.mappedTo.includes(id)) err(mid, 'pmkRow does not list this food');
    } else if (m.source === 'estimate') {
      nEst++;
      if (m.flag !== '⚠') err(mid, 'estimate must carry ⚠');
      if (typeof m.method !== 'string' || m.method.length < 30) err(mid, 'estimate needs a written method');
      if (!(m.spreadPct >= 10 && m.spreadPct <= 60)) err(mid, 'estimate needs spreadPct 10-60');
    } else err(mid, `unknown source ${m.source}`);
  }
}
for (const r of rows.values()) for (const fid of r.mappedTo) {
  const f = (data.foods || []).find(x => x.foodId === fid);
  if (!f || !f.measures.some(m => m.ref?.pmkRow === r.id)) err(r.id, `mapped to ${fid} but no measure there`);
}
const c = data.counts || {};
if (c.measuresPmk !== nPmk || c.measuresEstimate !== nEst || c.foods !== seenFood.size) err('file', 'counts do not match contents');

// Report: which foods used by meal packages still have no household measure
try {
  const mp = JSON.parse(fs.readFileSync(path.join(root, 'content', 'meal-packages.json'), 'utf8'));
  const used = new Set(); JSON.stringify(mp, (k, v) => { if (k === 'foodId') used.add(v); return v; });
  const missing = [...used].filter(x => !seenFood.has(x) && !foodsAll.get(x)?.household?.length);
  if (missing.length) warns.push(`${missing.length} meal-package foods without any household measure: ${missing.join(', ')}`);
} catch { /* meal packages optional */ }

console.log(`household.json: ${seenFood.size} foods, ${nPmk} PMK measures, ${nEst} estimate measures ⚠, ${rows.size} PMK rows (${[...rows.values()].filter(r => r.suspect).length} suspect, not attached)`);
for (const w of warns) console.log(`note: ${w}`);
if (errors.length) { console.error(`${errors.length} error(s):\n` + errors.join('\n')); process.exit(1); }
console.log('OK');
