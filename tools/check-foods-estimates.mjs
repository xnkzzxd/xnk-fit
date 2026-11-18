#!/usr/bin/env node
// Checks content/foods-estimates.json (task C-04). Usage: node tools/check-foods-estimates.mjs [file]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const file = process.argv[2] || path.join(here, '..', 'content', 'foods-estimates.json');
const errors = [];
const err = (id, m) => errors.push(`${id}: ${m}`);

let data;
try { data = JSON.parse(fs.readFileSync(file, 'utf8')); }
catch (e) { console.error(`Cannot read ${file}: ${e.message}`); process.exit(2); }

if (!Number.isInteger(data.schema) || !Number.isInteger(data.version)) err('file', 'schema/version missing');
const foods = Array.isArray(data.foods) ? data.foods : [];
if (!foods.length) err('file', 'no foods');

const REQUIRED = ['id','source','name_id','name_en','kcalPer100','protein','carb','fat','fibre','sodium',
  'household','isEstimate','halal','allergens','lactose','portion','recipe','basis'];
const ALLERGENS = new Set(['egg','seafood','nuts','milk']);
const seen = new Set();
let flagged = 0;

for (const f of foods) {
  const id = f.id || '(no id)';
  for (const k of REQUIRED) if (!(k in f)) err(id, `missing field ${k}`);
  if (seen.has(f.id)) err(id, 'duplicate id'); seen.add(f.id);
  if (f.source !== 'warung-est') err(id, 'source must be warung-est');
  if (f.isEstimate !== true) err(id, 'isEstimate must be true');
  if (f.halal !== true) err(id, 'halal must be true for bundled items');
  if (!Array.isArray(f.allergens) || f.allergens.some(a => !ALLERGENS.has(a))) err(id, 'bad allergens');
  if (f.sodium !== null && !(typeof f.sodium === 'number' && f.sodium >= 0)) err(id, 'sodium must be null or >= 0');
  if (!Array.isArray(f.household) || !f.household.length || f.household.some(h => !h.code || !h.label || !(h.grams > 0) || !h.source)) err(id, 'bad household');
  if (!Array.isArray(f.recipe) || !f.recipe.length) err(id, 'recipe (bahan dan gram) required');
  if (!f.basis || typeof f.basis !== 'string') err(id, 'basis required');
  if (f.flag === '⚠') flagged++;
  const nums = ['kcalPer100','protein','carb','fat','fibre'];
  if (nums.some(k => typeof f[k] !== 'number' || !isFinite(f[k]))) { err(id, 'non-numeric nutrient'); continue; }
  // impossible values (per 100 g)
  if (nums.some(k => f[k] < 0)) err(id, 'negative nutrient');
  if (f.kcalPer100 > 900) err(id, `kcalPer100 ${f.kcalPer100} > 900`);
  if (f.protein + f.carb + f.fat > 100.5) err(id, 'protein+carb+fat > 100 g per 100 g');
  if (f.fibre > f.carb + 0.05) err(id, 'fibre > carb');
  // macros vs kcal (4/4/9, within 10 %)
  const calc = 4 * f.protein + 4 * f.carb + 9 * f.fat;
  if (Math.abs(calc - f.kcalPer100) > Math.max(0.1 * f.kcalPer100, 3)) err(id, `kcal ${f.kcalPer100} vs macros ${calc.toFixed(1)} differ > 10 %`);
  const p = f.portion || {};
  if (!(p.grams >= 1 && p.grams <= 1500)) err(id, 'portion grams out of 1..1500');
  const pk = 4 * p.protein + 4 * p.carb + 9 * p.fat;
  if (Math.abs(pk - p.kcal) > Math.max(0.1 * p.kcal, 5)) err(id, `portion kcal ${p.kcal} vs macros ${pk.toFixed(0)} differ > 10 %`);
  if (!Array.isArray(p.kcalRange) || !(p.kcalRange[0] < p.kcal && p.kcal < p.kcalRange[1])) err(id, 'kcalRange must bracket kcal');
  if (!(p.kcal >= 5 && p.kcal <= 2000)) err(id, 'portion kcal out of 5..2000');
  if (Math.abs(f.kcalPer100 - p.kcal * 100 / p.grams) > 0.05 * f.kcalPer100 + 2) err(id, 'kcalPer100 does not match portion');
  const hp = f.household && f.household[0];
  if (hp && hp.grams !== p.grams) err(id, 'household grams differ from portion grams');
  if (f.flag != null && f.flag !== '⚠') err(id, 'flag must be ⚠ or null');
}

console.log(`${file}\nrows: ${foods.length}, flagged ⚠: ${flagged}`);
if (errors.length) { console.log(`FAIL (${errors.length})`); errors.forEach(e => console.log(' - ' + e)); process.exit(1); }
console.log('OK');
