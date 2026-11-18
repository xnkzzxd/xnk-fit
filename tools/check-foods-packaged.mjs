#!/usr/bin/env node
// Checks content/foods-packaged.json (task C-06). Usage: node tools/check-foods-packaged.mjs [file]
// Rules: Content.md §4 (packaged), Design §3 (food), Rules S9 (4/4/9), R_EST, R_HALAL.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const file = process.argv[2] || path.join(here, '..', 'content', 'foods-packaged.json');
const errors = [];
const err = (id, m) => errors.push(`${id}: ${m}`);

let data;
try { data = JSON.parse(fs.readFileSync(file, 'utf8')); }
catch (e) { console.error(`Cannot read ${file}: ${e.message}`); process.exit(2); }

if (!Number.isInteger(data.schema) || !Number.isInteger(data.version)) err('file', 'schema/version missing');
if (data.source !== 'packaged') err('file', 'source must be packaged');
const foods = Array.isArray(data.foods) ? data.foods : [];
if (!foods.length) err('file', 'no foods');

const REQUIRED = ['id', 'source', 'brand', 'product', 'name_id', 'serving', 'perServing', 'per100g',
  'sourceType', 'sourceUrl', 'readDate', 'isEstimate', 'flag', 'halal', 'allergens', 'lactose', 'household', 'note'];
const ALLERGENS = new Set(['egg', 'seafood', 'nuts', 'milk']);
const SOURCE_TYPES = new Set(['producer', 'third-party']);
const seen = new Set();
let producer = 0, thirdParty = 0, flagged = 0;

const isNum = v => typeof v === 'number' && isFinite(v);
const isNullOrNonNeg = v => v === null || (isNum(v) && v >= 0);

function macroCheck(id, label, p) {
  const calc = 4 * p.protein + 4 * p.carb + 9 * p.fat;
  // Label values are rounded per nutrient, so allow 15 % (and at least 5 kcal for very small values)
  if (Math.abs(calc - p.kcal) > Math.max(0.15 * p.kcal, 5)) {
    err(id, `${label}: kcal ${p.kcal} vs 4/4/9 macros ${calc.toFixed(1)} differ > 15 %`);
  }
}

for (const f of foods) {
  const id = f.id || '(no id)';
  for (const k of REQUIRED) if (!(k in f)) err(id, `missing field ${k}`);
  if (seen.has(f.id)) err(id, 'duplicate id'); seen.add(f.id);
  if (f.source !== 'packaged') err(id, 'source must be packaged');
  if (typeof f.brand !== 'string' || !f.brand) err(id, 'brand required');
  if (typeof f.product !== 'string' || !f.product) err(id, 'product required');
  if (typeof f.name_id !== 'string' || !f.name_id) err(id, 'name_id required');

  // provenance
  if (!SOURCE_TYPES.has(f.sourceType)) err(id, 'sourceType must be producer or third-party');
  if (typeof f.sourceUrl !== 'string' || !/^https:\/\/[^\s]+$/.test(f.sourceUrl)) err(id, 'sourceUrl must be an https URL');
  if (typeof f.readDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(f.readDate)) err(id, 'readDate must be YYYY-MM-DD');
  if (f.sourceType === 'producer') {
    producer++;
    if (f.isEstimate !== false) err(id, 'producer source must have isEstimate = false');
    if (f.flag !== null) err(id, 'producer source must have flag = null');
  } else if (f.sourceType === 'third-party') {
    thirdParty++;
    if (f.isEstimate !== true) err(id, 'third-party source must have isEstimate = true');
    if (f.flag !== '⚠') err(id, 'third-party source must carry flag ⚠');
  }
  if (f.flag === '⚠') flagged++;
  if (f.flag != null && f.flag !== '⚠') err(id, 'flag must be ⚠ or null');

  // halal and allergens only when stated (never guessed)
  if (f.halal !== null && f.halal !== true) err(id, 'halal must be null or true');
  if (f.halal === true && (typeof f.halalSourceUrl !== 'string' || !f.halalSourceUrl)) err(id, 'halal true needs halalSourceUrl (only when the label or site says so)');
  if (f.allergens !== null && (!Array.isArray(f.allergens) || f.allergens.some(a => !ALLERGENS.has(a)))) err(id, 'bad allergens');
  if (f.lactose !== null && typeof f.lactose !== 'boolean') err(id, 'lactose must be null or boolean');

  // serving
  const s = f.serving || {};
  if (typeof s.label !== 'string' || !s.label) err(id, 'serving.label required');
  if (!(s.grams === null || (isNum(s.grams) && s.grams > 0 && s.grams <= 1500))) err(id, 'serving.grams must be null or 1..1500');
  if (!(s.ml === null || (isNum(s.ml) && s.ml > 0 && s.ml <= 3000))) err(id, 'serving.ml must be null or 1..3000');
  if (s.grams == null && s.ml == null) err(id, 'serving needs grams or ml');

  // household (grams must match the serving when both are known)
  if (!Array.isArray(f.household)) err(id, 'household must be a list');
  else for (const h of f.household) {
    if (!h.code || !h.label || !h.source) err(id, 'household entry needs code, label, source');
    if (h.grams != null && !(h.grams > 0)) err(id, 'household grams must be > 0');
    if (h.grams != null && s.grams != null && h.grams !== s.grams) err(id, 'household grams differ from serving grams');
  }

  // per serving, as printed
  const p = f.perServing || {};
  const nutr = ['kcal', 'protein', 'fat', 'carb'];
  if (nutr.some(k => !isNum(p[k]))) { err(id, 'perServing kcal, protein, fat, carb must be numbers'); continue; }
  for (const k of ['sugar', 'fibre', 'sodium']) if (!isNullOrNonNeg(p[k])) err(id, `perServing.${k} must be null or >= 0`);
  if (nutr.some(k => p[k] < 0)) err(id, 'negative value per serving');
  if (p.kcal > 2000) err(id, `kcal ${p.kcal} per serving > 2000`);
  if (p.sodium != null && p.sodium > 5000) err(id, `sodium ${p.sodium} mg per serving > 5000`);
  if (p.sugar != null && p.sugar > p.carb + 0.05) err(id, 'sugar > carb per serving');
  if (p.fibre != null && p.fibre > p.carb + 0.05) err(id, 'fibre > carb per serving');
  if (s.grams != null && (p.protein + p.carb + p.fat) > s.grams * 1.02) err(id, 'macros (g) exceed serving grams');
  macroCheck(id, 'per serving', p);

  // per 100 g, only when printed
  if (f.per100g !== null) {
    const q = f.per100g;
    if (nutr.some(k => !isNum(q[k]))) err(id, 'per100g kcal, protein, fat, carb must be numbers');
    else {
      if (nutr.some(k => q[k] < 0)) err(id, 'negative value per 100 g');
      if (q.kcal > 900) err(id, `kcal ${q.kcal} per 100 g > 900`);
      if (q.protein + q.carb + q.fat > 100.5) err(id, 'macros per 100 g exceed 100 g');
      macroCheck(id, 'per 100 g', q);
    }
  }

  if (typeof f.note !== 'string') err(id, 'note must be a string (empty allowed)');
}

console.log(`${file}\nrows: ${foods.length}, producer label: ${producer}, third-party ⚠: ${thirdParty}, flagged ⚠: ${flagged}`);
if (errors.length) { console.log(`FAIL (${errors.length})`); errors.forEach(e => console.log(' - ' + e)); process.exit(1); }
console.log('OK');
