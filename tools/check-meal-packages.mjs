#!/usr/bin/env node
// C-11 checker for content/meal-packages.json.
// Usage: node tools/check-meal-packages.mjs
// Recomputes everything from the two food tables with its own code (it does
// not import the builder). Exit code 1 if any check fails.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const load = (p) => JSON.parse(readFileSync(join(root, p), 'utf8'));
const doc = load('content/meal-packages.json');
const foods = new Map();
for (const f of [...load('content/foods-tkpi.json').foods, ...load('content/foods-estimates.json').foods]) foods.set(f.id, f);

const errors = [];
const err = (m) => errors.push(m);
const SLOTS = ['pagi', 'siang', 'malam', 'camilan'];
const FLOOR = 1500; // stricter of R_FLOOR_F 1200 and R_FLOOR_M 1500
const MAX_PKG_KCAL = 900;

if (doc.schema !== 1) err('schema harus 1');
if (JSON.stringify(doc.slots) !== JSON.stringify(SLOTS)) err('slots harus pagi, siang, malam, camilan');
if (!doc.floorKcal || doc.floorKcal.used !== FLOOR) err(`floorKcal.used harus ${FLOOR}`);

// independent copies of the derivation rules
const scan = {
  egg: /telur|egg|\bmie\b/i,
  seafood: /udang|ikan|teri|cumi|kerang|rajungan|kepiting|siomay|pempek|batagor|tenggiri|lele|patin|bandeng|cakalang|tongkol|sarden|terasi|peda|kembung|tuna|salmon|seafood/i,
  nuts: /kacang tanah|bumbu kacang|selai kacang|peanut|melinjo|emping|kenari|almond|mete/i,
  milk: /susu|keju|yoghurt|yogurt|mentega|butter|krim|cream/i,
};
const pork = /\bbabi\b|\bbir\b|tuak|\barak\b|wine|alkohol|bacon|\bham\b/i;
const claimWords = /sehat|menyehatkan|bergizi|detox|diet\b|kurus|bakar lemak|rendah kalori|tinggi protein|superfood|menurunkan|meningkatkan|khasiat|vitamin|antioksidan|menguatkan|mencegah|obat/i;

const profileIds = doc.profiles.map((p) => p.id);
const seenIds = new Set();
const budgetRank = { Hemat: 1, Standar: 2, Bebas: 3 };
let checkedItems = 0;
const dayRange = {};

for (const p of doc.packages) {
  const tag = p.id;
  if (seenIds.has(p.id)) err(`${tag}: id ganda`);
  seenIds.add(p.id);
  if (!profileIds.includes(p.profile)) err(`${tag}: profil ${p.profile} tidak dikenal`);
  if (!SLOTS.includes(p.slot)) err(`${tag}: slot ${p.slot} tidak dikenal`);
  if (!['masak', 'beli', 'tanpa-masak'].includes(p.mode)) err(`${tag}: mode ${p.mode}`);
  if (!p.name_id || p.name_id.length > 70) err(`${tag}: nama kosong atau terlalu panjang`);
  if (claimWords.test(p.name_id)) err(`${tag}: nama memuat klaim kesehatan: ${p.name_id}`);
  if (/\d/.test(p.name_id)) err(`${tag}: nama memuat angka: ${p.name_id}`);

  let kcal = 0, protein = 0, carb = 0, fat = 0, fibre = 0, fibreOk = true;
  const al = new Set();
  let lactose = false, halal = true, tier = 1;
  const mains = [];
  if (!Array.isArray(p.items) || p.items.length === 0) { err(`${tag}: tanpa item`); continue; }
  for (const it of p.items) {
    checkedItems++;
    const f = foods.get(it.foodId);
    if (!f) { err(`${tag}: foodId ${it.foodId} tidak ada di tabel`); continue; }
    if (f.name_id !== it.name_id) err(`${tag}: nama item ${it.foodId} tidak cocok dengan tabel`);
    if (!(it.grams > 0)) err(`${tag}: gram ${it.foodId} harus > 0`);
    if (f.flags && f.flags.length) err(`${tag}: ${it.foodId} bertanda ${f.flags.join(',')}`);
    if (it.household) {
      const h = (f.household || []).find((x) => x.code === it.household.code);
      if (!h) err(`${tag}: takaran ${it.household.code} tidak ada pada ${it.foodId}`);
      else if (Math.abs(h.grams * it.household.qty - it.grams) > 0.01) err(`${tag}: takaran ${it.foodId} tidak sama dengan gram`);
    }
    const k = it.grams / 100;
    kcal += f.kcalPer100 * k; protein += f.protein * k; carb += f.carb * k; fat += f.fat * k;
    if (typeof f.fibre === 'number') fibre += f.fibre * k; else fibreOk = false;
    // allergens: whatever the row says + keyword scan of name and recipe + table group
    const text = `${f.name_id} ${(f.recipe || []).join(' ')}`;
    for (const a of Array.isArray(f.allergens) ? f.allergens : []) al.add(a);
    for (const [a, rx] of Object.entries(scan)) if (rx.test(text)) al.add(a);
    if (f.source === 'tkpi') { if (f.group === 'H') al.add('egg'); if (f.group === 'G') al.add('seafood'); if (f.group === 'J') al.add('milk'); }
    if (f.lactose === true || /susu|keju|yoghurt|yogurt|krim|cream/i.test(f.name_id) || (f.source === 'tkpi' && f.group === 'J')) lactose = true;
    if (f.halal === false || pork.test(text)) halal = false;
    if (it.main) mains.push(it.foodId);
    const t = doc.foodTier[it.foodId];
    if (![1, 2, 3].includes(t)) err(`${tag}: ${it.foodId} tanpa tingkat anggaran`);
    else tier = Math.max(tier, t);
  }
  const near = (a, b, tol) => Math.abs(a - b) <= tol;
  if (!near(p.kcal, kcal, 0.51)) err(`${tag}: kkal ${p.kcal} != hitungan ${kcal.toFixed(1)}`);
  if (!near(p.protein, protein, 0.06)) err(`${tag}: protein ${p.protein} != ${protein.toFixed(2)}`);
  if (!near(p.carb, carb, 0.06)) err(`${tag}: karbo ${p.carb} != ${carb.toFixed(2)}`);
  if (!near(p.fat, fat, 0.06)) err(`${tag}: lemak ${p.fat} != ${fat.toFixed(2)}`);
  if (!near(p.fibre, fibre, 0.06)) err(`${tag}: serat ${p.fibre} != ${fibre.toFixed(2)}`);
  if (p.fibreComplete !== fibreOk) err(`${tag}: fibreComplete salah`);
  // 4/4/9 sanity: printed table kcal may differ, but a package far off signals a bad row
  const atwater = 4 * p.protein + 4 * p.carb + 9 * p.fat;
  if (Math.abs(atwater - p.kcal) > 0.25 * p.kcal) err(`${tag}: kkal ${p.kcal} jauh dari 4/4/9 (${atwater.toFixed(0)})`);
  if (p.kcal > MAX_PKG_KCAL) err(`${tag}: kkal ${p.kcal} di atas ${MAX_PKG_KCAL}`);

  // allergens: every detected allergen must be listed (nothing undetected)
  for (const a of al) if (!p.allergens.includes(a)) err(`${tag}: alergen ${a} tidak tercatat`);
  for (const a of p.allergens) if (!['egg', 'seafood', 'nuts', 'milk'].includes(a)) err(`${tag}: kode alergen ${a}`);
  if (lactose && !p.lactose) err(`${tag}: laktosa tidak tercatat`);
  if (al.has('milk') && /susu|keju|yoghurt|yogurt|krim|cream/i.test(p.items.map((i) => i.name_id).join(' ')) && !p.lactose) err(`${tag}: produk susu tanpa laktosa`);
  if (halal !== p.halal) err(`${tag}: halal ${p.halal} != ${halal}`);
  if (!p.halal) err(`${tag}: paket tidak halal (R_HALAL)`);
  if (p.isEstimate !== p.items.some((i) => foods.get(i.foodId)?.isEstimate)) err(`${tag}: isEstimate salah`);

  const budget = ['', 'Hemat', 'Standar', 'Bebas'][tier];
  if (p.budget !== budget) err(`${tag}: anggaran ${p.budget} != ${budget}`);
  if (!p.tags || JSON.stringify(p.tags.mainProtein) !== JSON.stringify(mains) || mains.length === 0) err(`${tag}: mainProtein tidak cocok dengan item utama`);

  if (p.profile === 'beli' && p.mode !== 'beli') err(`${tag}: profil beli harus mode beli`);
  if (p.profile === 'kos') {
    if (p.mode === 'beli') err(`${tag}: anak kos tidak boleh mode beli`);
    if (budgetRank[p.budget] > 2) err(`${tag}: anak kos harus Hemat atau Standar`);
    const eq = p.equipment;
    if (!Array.isArray(eq) || eq.length === 0 || eq.length > 3 || eq.some((x) => !['tanpa', 'rice-cooker', 'kompor', 'panci', 'wajan'].includes(x))) err(`${tag}: peralatan anak kos tidak valid`);
  }
}

// three options per slot per profile, variety between them, floor on the day
for (const prof of profileIds) {
  const mins = [];
  for (const slot of SLOTS) {
    const opts = doc.packages.filter((p) => p.profile === prof && p.slot === slot);
    if (opts.length !== 3) { err(`${prof}/${slot}: ${opts.length} opsi, harus 3`); continue; }
    mins.push(Math.min(...opts.map((o) => o.kcal)));
    const mainSet = new Set(opts.flatMap((o) => o.tags.mainProtein));
    const mainCount = opts.reduce((n, o) => n + o.tags.mainProtein.length, 0);
    if (mainSet.size !== mainCount) err(`${prof}/${slot}: item utama sama di dua opsi`);
    const names = new Set(opts.map((o) => o.name_id));
    if (names.size !== 3) err(`${prof}/${slot}: nama paket ganda`);
    for (let i = 0; i < 3; i++) for (let j = i + 1; j < 3; j++) {
      const a = new Set(opts[i].items.map((x) => x.foodId)), b = new Set(opts[j].items.map((x) => x.foodId));
      const inter = [...a].filter((x) => b.has(x)).length;
      const jac = inter / (a.size + b.size - inter);
      if (jac > 0.5) err(`${prof}/${slot}: ${opts[i].id} dan ${opts[j].id} terlalu mirip (Jaccard ${jac.toFixed(2)})`);
    }
  }
  const total = mins.reduce((a, b) => a + b, 0);
  dayRange[prof] = { min: total, max: SLOTS.reduce((s, slot) => s + Math.max(...doc.packages.filter((p) => p.profile === prof && p.slot === slot).map((o) => o.kcal)), 0) };
  if (mins.length === 4 && total < FLOOR) err(`${prof}: hari termurah ${total} kkal < lantai ${FLOOR}`);
}

// own hook
if (!doc.own || doc.own.profile !== 'sendiri' || !Array.isArray(doc.own.packages) || doc.own.packages.length !== 0) err('hook "milik sendiri" tidak ada atau berisi paket bawaan');
else if (JSON.stringify(doc.own.slots) !== JSON.stringify(SLOTS)) err('hook sendiri harus mencakup empat slot');

// foodTier has no unused entries
const used = new Set(doc.packages.flatMap((p) => p.items.map((i) => i.foodId)));
for (const id of Object.keys(doc.foodTier)) if (!used.has(id)) err(`foodTier memuat ${id} yang tidak dipakai`);

const nPkg = doc.packages.length;
console.log(`paket: ${nPkg}  item diperiksa: ${checkedItems}  profil: ${profileIds.length}  slot: ${SLOTS.length}`);
for (const [k, v] of Object.entries(dayRange)) console.log(`  ${k.padEnd(7)} hari termurah ${v.min} kkal, termahal ${v.max} kkal (lantai ${FLOOR})`);
const byBudget = {};
for (const p of doc.packages) byBudget[p.budget] = (byBudget[p.budget] || 0) + 1;
console.log('  anggaran:', JSON.stringify(byBudget), ' perkiraan:', doc.packages.filter((p) => p.isEstimate).length, ' bebas telur/susu/laktosa dihitung dari item');
if (errors.length) {
  console.error(`GAGAL: ${errors.length} masalah`);
  for (const e of errors) console.error(' - ' + e);
  process.exit(1);
}
console.log('OK: semua pemeriksaan lolos');
