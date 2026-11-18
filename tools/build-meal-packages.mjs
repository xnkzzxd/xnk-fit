#!/usr/bin/env node
// C-11: build content/meal-packages.json.
// Usage: node tools/build-meal-packages.mjs
//
// The package definitions below list only foodId + grams (+ "m" for the main
// item). kcal, macros, halal, allergens, lactose, budget label and limit tags
// are all derived from content/foods-tkpi.json and content/foods-estimates.json.
// Never type a total by hand.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const tkpi = JSON.parse(readFileSync(join(root, 'content/foods-tkpi.json'), 'utf8'));
const est = JSON.parse(readFileSync(join(root, 'content/foods-estimates.json'), 'utf8'));
const foods = new Map();
for (const f of [...tkpi.foods, ...est.foods]) foods.set(f.id, f);

const SLOTS = ['pagi', 'siang', 'malam', 'camilan'];

const PROFILES = [
  { id: 'jawa', kind: 'region', label: 'Jawa' },
  { id: 'sunda', kind: 'region', label: 'Sunda' },
  { id: 'padang', kind: 'region', label: 'Padang' },
  { id: 'umum', kind: 'region', label: 'Umum' },
  { id: 'beli', kind: 'bought', label: 'Beli (warung, warteg)' },
  { id: 'kos', kind: 'kos', label: 'Anak kos (hemat, minim alat)' },
];

// ---- derivation rules (the checker re-implements these independently) ----
const RX = {
  egg: /telur|egg|\bmie\b/i, // mie kuning usually has egg: conservative
  seafood: /udang|ikan|teri|cumi|kerang|rajungan|kepiting|siomay|pempek|batagor|tenggiri|lele|patin|bandeng|cakalang|tongkol|sarden|terasi|peda|kembung|tuna|salmon|seafood/i,
  nuts: /kacang tanah|bumbu kacang|selai kacang|peanut|melinjo|emping|kenari|almond|mete/i,
  milk: /susu|keju|yoghurt|yogurt|mentega|butter|krim|cream/i,
  dairy: /susu|keju|yoghurt|yogurt|krim|cream/i,
  pork: /\bbabi\b|\bbir\b|tuak|\barak\b|wine|alkohol|bacon|\bham\b/i,
  santan: /santan|gulai|opor|rendang|lodeh|uduk|kolak|gudeg/i,
  fried: /goreng|geprek|mendoan|bakwan|cireng|risoles|tahu isi/i,
};
const ALLERGEN_OVERRIDE = {
  // TKPI does not list the bread recipe; assume egg and milk until a label says otherwise.
  TKPI_AP024: { allergens: ['egg', 'milk'], lactose: true },
};
const TKPI_GROUP_ALLERGEN = { H: 'egg', G: 'seafood', J: 'milk' };

function itemText(f) {
  return `${f.name_id} ${(f.recipe || []).join(' ')}`;
}
function itemAllergens(f) {
  const s = new Set(Array.isArray(f.allergens) ? f.allergens : []);
  const text = itemText(f);
  for (const k of ['egg', 'seafood', 'nuts', 'milk']) if (RX[k].test(text)) s.add(k);
  if (f.source === 'tkpi' && TKPI_GROUP_ALLERGEN[f.group]) s.add(TKPI_GROUP_ALLERGEN[f.group]);
  const ov = ALLERGEN_OVERRIDE[f.id];
  if (ov) for (const a of ov.allergens) s.add(a);
  let lactose = f.lactose === true || RX.dairy.test(f.name_id) || (f.source === 'tkpi' && f.group === 'J');
  if (ov && ov.lactose) lactose = true;
  return { allergens: s, lactose };
}
function itemHalal(f) {
  return f.halal !== false && !RX.pork.test(itemText(f));
}

// Ordinal budget tier: 1 Hemat, 2 Standar, 3 Bebas. Judgement, not a price (no Rupiah). Default 2.
const TIER1 = /^(W_NASI_PUTIH|W_NASI_UDUK|W_TEMPE_GORENG|W_TAHU_GORENG|W_TELUR_DADAR|W_TELUR_BALADO|W_OREK_TEMPE|W_PERKEDEL|W_SAYUR_LODEH|W_SAYUR_ASEM|W_SAYUR_BENING|W_TUMIS_KANGKUNG|W_CAPCAY|W_PECEL|W_GADO_GADO|W_DAUN_SINGKONG|W_GULAI_NANGKA|W_LONTONG_SAYUR|W_BUBUR_AYAM|W_BAKWAN|W_MENDOAN|W_TAHU_ISI|W_CIRENG|W_PISANG_GORENG|W_KOLAK_PISANG|W_BUBUR_KACANG_IJO|W_LEMPER|W_TEH_MANIS_HANGAT|W_ES_TEH_MANIS|W_MIE_AYAM|W_NASI_CAMPUR|W_RISOLES|TKPI_(AP006|AP024|BP011|CP005|CP013|CP054|CP061|CP062|DP\d+|DR\d+|ER\d+|HR\d+|GP004|GP042|NP005|KR012))$/;
const TIER3 = /^(W_RENDANG|W_NASI_PADANG_RENDANG|W_NASI_PADANG_AYAM|W_RAWON|W_SOTO_BETAWI|W_MARTABAK_MANIS|W_BOBA_MILK_TEA|W_ES_KOPI_SUSU|W_ES_CAMPUR|W_JUS_ALPUKAT|TKPI_(ER001|JP003|GR082|GR083|GR084))$/;
function tierOf(id) {
  if (TIER3.test(id)) return 3;
  if (TIER1.test(id)) return 1;
  return 2;
}
const BUDGET = { 1: 'Hemat', 2: 'Standar', 3: 'Bebas' };

// ---- package definitions: [foodId, grams, 'm'?] -----------------------
const m = 'm';
const NASI = 'W_NASI_PUTIH';
const defs = [];
// pk(profile, slot, name, mode, items, extra)
const pk = (profile, slot, name, mode, items, extra = {}) =>
  defs.push({ profile, slot, name, mode, items, ...extra });

// Jawa
pk('jawa', 'pagi', 'Nasi uduk, telur dadar, tempe goreng', 'masak', [['W_NASI_UDUK', 200], ['W_TELUR_DADAR', 60, m], ['W_TEMPE_GORENG', 50], ['TKPI_DR109', 50]]);
pk('jawa', 'pagi', 'Lontong sayur dan tempe goreng', 'beli', [['W_LONTONG_SAYUR', 400, m], ['W_TEMPE_GORENG', 50]]);
pk('jawa', 'pagi', 'Pecel, nasi, tempe goreng', 'masak', [['W_PECEL', 250, m], [NASI, 150], ['W_TEMPE_GORENG', 50]]);
pk('jawa', 'siang', 'Nasi, ayam bakar, sayur bening, tempe goreng', 'masak', [[NASI, 200], ['W_AYAM_BAKAR', 90, m], ['W_SAYUR_BENING', 200], ['W_TEMPE_GORENG', 50]]);
pk('jawa', 'siang', 'Nasi gudeg komplit dan tahu goreng', 'beli', [['W_GUDEG_NASI', 350, m], ['W_TAHU_GORENG', 60]]);
pk('jawa', 'siang', 'Nasi, sayur lodeh, tahu dan tempe goreng, perkedel', 'masak', [[NASI, 200], ['W_SAYUR_LODEH', 150], ['W_TAHU_GORENG', 60, m], ['W_TEMPE_GORENG', 50], ['W_PERKEDEL', 50]]);
pk('jawa', 'malam', 'Soto ayam, nasi, tempe goreng', 'masak', [['W_SOTO_AYAM', 350, m], [NASI, 200], ['W_TEMPE_GORENG', 50]]);
pk('jawa', 'malam', 'Nasi, lele goreng, tumis kangkung', 'masak', [[NASI, 200], ['W_LELE_GORENG', 100, m], ['W_TUMIS_KANGKUNG', 100]]);
pk('jawa', 'malam', 'Rawon, nasi, daun singkong rebus', 'masak', [['W_RAWON', 350, m], [NASI, 200], ['W_DAUN_SINGKONG', 100]]);
pk('jawa', 'camilan', 'Pisang kepok', 'tanpa-masak', [['TKPI_ER081', 150, m]]);
pk('jawa', 'camilan', 'Kolak pisang', 'masak', [['W_KOLAK_PISANG', 200, m]]);
pk('jawa', 'camilan', 'Ubi jalar kukus', 'masak', [['TKPI_BP011', 200, m]]);

// Sunda
pk('sunda', 'pagi', 'Nasi, oncom pepes, tahu goreng, lalapan', 'masak', [[NASI, 200], ['TKPI_CP054', 100, m], ['W_TAHU_GORENG', 60], ['TKPI_DR109', 50], ['TKPI_DR161', 50]]);
pk('sunda', 'pagi', 'Bubur ayam dan tahu isi', 'beli', [['W_BUBUR_AYAM', 350, m], ['W_TAHU_ISI', 50]]);
pk('sunda', 'pagi', 'Nasi, telur dadar, tumis kangkung', 'masak', [[NASI, 200], ['W_TELUR_DADAR', 60, m], ['W_TUMIS_KANGKUNG', 100]]);
pk('sunda', 'siang', 'Nasi, ayam goreng, sayur asem, tempe goreng, lalapan', 'masak', [[NASI, 200], ['W_AYAM_GORENG', 80, m], ['W_SAYUR_ASEM', 200], ['W_TEMPE_GORENG', 50], ['TKPI_DR109', 50]]);
pk('sunda', 'siang', 'Nasi, pepes ikan mas, sayur asem, tahu goreng', 'masak', [[NASI, 200], ['TKPI_GP019', 100, m], ['W_SAYUR_ASEM', 200], ['W_TAHU_GORENG', 60]]);
pk('sunda', 'siang', 'Sate ayam, nasi, sayur bening', 'beli', [['W_SATE_AYAM', 150, m], [NASI, 150], ['W_SAYUR_BENING', 200]]);
pk('sunda', 'malam', 'Nasi, tempe dan tahu goreng, lalapan sambal', 'masak', [[NASI, 200], ['W_TEMPE_GORENG', 50, m], ['W_TAHU_GORENG', 60], ['TKPI_DR109', 60], ['TKPI_DR145', 30]]);
pk('sunda', 'malam', 'Nasi, bandeng presto, tumis kangkung', 'masak', [[NASI, 200], ['TKPI_GP005', 80, m], ['W_TUMIS_KANGKUNG', 100]]);
pk('sunda', 'malam', 'Sop ayam, nasi, tahu isi', 'masak', [['W_SOP_AYAM', 350, m], [NASI, 200], ['W_TAHU_ISI', 100]]);
pk('sunda', 'camilan', 'Cireng', 'beli', [['W_CIRENG', 50, m]]);
pk('sunda', 'camilan', 'Pisang goreng', 'beli', [['W_PISANG_GORENG', 60, m]]);
pk('sunda', 'camilan', 'Jambu biji', 'tanpa-masak', [['TKPI_ER031', 200, m]]);

// Padang
pk('padang', 'pagi', 'Lontong sayur dan telur balado', 'beli', [['W_LONTONG_SAYUR', 400, m], ['W_TELUR_BALADO', 70]]);
pk('padang', 'pagi', 'Nasi, telur dadar, daun singkong, gulai nangka', 'beli', [[NASI, 150], ['W_TELUR_DADAR', 60, m], ['W_DAUN_SINGKONG', 100], ['W_GULAI_NANGKA', 100]]);
pk('padang', 'pagi', 'Nasi, teri kering, daun singkong, tahu goreng', 'masak', [[NASI, 200], ['TKPI_GP042', 20, m], ['W_DAUN_SINGKONG', 100], ['W_TAHU_GORENG', 60]]);
pk('padang', 'siang', 'Nasi padang dengan rendang', 'beli', [['W_NASI_PADANG_RENDANG', 450, m]]);
pk('padang', 'siang', 'Nasi padang ayam bakar', 'beli', [['W_NASI_PADANG_AYAM', 450, m]]);
pk('padang', 'siang', 'Nasi, gulai ikan, daun singkong, gulai nangka', 'beli', [[NASI, 200], ['TKPI_GP065', 150, m], ['W_DAUN_SINGKONG', 100], ['W_GULAI_NANGKA', 100]]);
pk('padang', 'malam', 'Nasi, ayam bakar, daun singkong, gulai nangka', 'masak', [[NASI, 200], ['W_AYAM_BAKAR', 90, m], ['W_DAUN_SINGKONG', 100], ['W_GULAI_NANGKA', 100]]);
pk('padang', 'malam', 'Nasi, ikan patin bakar, tumis kangkung, perkedel', 'masak', [[NASI, 200], ['TKPI_GP024', 120, m], ['W_TUMIS_KANGKUNG', 100], ['W_PERKEDEL', 50]]);
pk('padang', 'malam', 'Nasi, orek tempe, sayur lodeh, tahu goreng', 'masak', [[NASI, 200], ['W_OREK_TEMPE', 50, m], ['W_SAYUR_LODEH', 150], ['W_TAHU_GORENG', 60]]);
pk('padang', 'camilan', 'Bubur kacang hijau', 'masak', [['W_BUBUR_KACANG_IJO', 200, m]]);
pk('padang', 'camilan', 'Pisang goreng', 'beli', [['W_PISANG_GORENG', 60, m]]);
pk('padang', 'camilan', 'Lemper ayam dan semangka', 'beli', [['W_LEMPER', 60, m], ['TKPI_ER105', 200]]);

// Umum
pk('umum', 'pagi', 'Nasi goreng dan ketimun', 'masak', [['W_NASI_GORENG', 250, m], ['TKPI_DR109', 50]]);
pk('umum', 'pagi', 'Roti, telur dadar, susu sapi', 'masak', [['TKPI_AP024', 60], ['W_TELUR_DADAR', 60, m], ['TKPI_JR006', 200]]);
pk('umum', 'pagi', 'Bubur ayam dan tempe mendoan', 'beli', [['W_BUBUR_AYAM', 350, m], ['W_MENDOAN', 80]]);
pk('umum', 'siang', 'Nasi campur warung dan semangka', 'beli', [['W_NASI_CAMPUR', 400, m], ['TKPI_ER105', 150]]);
pk('umum', 'siang', 'Gado-gado, nasi, ayam bakar', 'masak', [['W_GADO_GADO', 300, m], [NASI, 100], ['W_AYAM_BAKAR', 90]]);
pk('umum', 'siang', 'Soto ayam, nasi, tempe goreng', 'masak', [['W_SOTO_AYAM', 350, m], [NASI, 200], ['W_TEMPE_GORENG', 50]]);
pk('umum', 'malam', 'Nasi, ayam geprek, ketimun', 'masak', [[NASI, 200], ['W_AYAM_GEPREK', 150, m], ['TKPI_DR109', 50]]);
pk('umum', 'malam', 'Nasi, lele goreng, capcay', 'masak', [[NASI, 200], ['W_LELE_GORENG', 100, m], ['W_CAPCAY', 150]]);
pk('umum', 'malam', 'Nasi, tahu dan tempe goreng, sayur asem, sayur bening', 'masak', [[NASI, 200], ['W_TAHU_GORENG', 60, m], ['W_TEMPE_GORENG', 50], ['W_SAYUR_ASEM', 200], ['W_SAYUR_BENING', 200]]);
pk('umum', 'camilan', 'Pisang ambon', 'tanpa-masak', [['TKPI_ER074', 150, m]]);
pk('umum', 'camilan', 'Yoghurt dan pepaya', 'tanpa-masak', [['TKPI_JP011', 150, m], ['TKPI_ER073', 150]]);
pk('umum', 'camilan', 'Kacang tanah rebus', 'masak', [['TKPI_CP013', 60, m]]);

// Beli (warung, warteg)
pk('beli', 'pagi', 'Bubur ayam, tempe mendoan, teh manis hangat', 'beli', [['W_BUBUR_AYAM', 350, m], ['W_MENDOAN', 40], ['W_TEH_MANIS_HANGAT', 200]]);
pk('beli', 'pagi', 'Nasi uduk, telur balado, tempe goreng', 'beli', [['W_NASI_UDUK', 200], ['W_TELUR_BALADO', 70, m], ['W_TEMPE_GORENG', 50]]);
pk('beli', 'pagi', 'Lontong sayur, bakwan, teh manis hangat', 'beli', [['W_LONTONG_SAYUR', 400, m], ['W_BAKWAN', 40], ['W_TEH_MANIS_HANGAT', 200]]);
pk('beli', 'siang', 'Nasi campur warteg dan es teh manis', 'beli', [['W_NASI_CAMPUR', 400, m], ['W_ES_TEH_MANIS', 250]]);
pk('beli', 'siang', 'Bakso kuah, tahu goreng, es jeruk', 'beli', [['W_BAKSO', 400, m], ['W_TAHU_GORENG', 60], ['W_ES_JERUK', 250]]);
pk('beli', 'siang', 'Gado-gado dan nasi putih', 'beli', [['W_GADO_GADO', 300, m], [NASI, 200]]);
pk('beli', 'malam', 'Mie ayam dan tahu goreng', 'beli', [['W_MIE_AYAM', 350, m], ['W_TAHU_GORENG', 60]]);
pk('beli', 'malam', 'Soto ayam, nasi, tempe goreng', 'beli', [['W_SOTO_AYAM', 350, m], [NASI, 200], ['W_TEMPE_GORENG', 50]]);
pk('beli', 'malam', 'Sate ayam dan nasi', 'beli', [['W_SATE_AYAM', 150, m], [NASI, 150]]);
pk('beli', 'camilan', 'Pisang goreng', 'beli', [['W_PISANG_GORENG', 60, m]]);
pk('beli', 'camilan', 'Risoles', 'beli', [['W_RISOLES', 70, m]]);
pk('beli', 'camilan', 'Tahu isi', 'beli', [['W_TAHU_ISI', 100, m]]);

// Anak kos (hemat, minim alat)
pk('kos', 'pagi', 'Roti selai kacang dan susu sapi', 'tanpa-masak', [['TKPI_AP024', 90, m], ['TKPI_CP036', 20], ['TKPI_JR006', 200]], { equipment: ['tanpa'] });
pk('kos', 'pagi', 'Nasi, telur dadar, kecap, ketimun', 'masak', [[NASI, 300], ['W_TELUR_DADAR', 60, m], ['TKPI_NP005', 10], ['TKPI_DR109', 50]], { equipment: ['rice-cooker', 'wajan'] });
pk('kos', 'pagi', 'Bihun rebus, telur, sawi', 'masak', [['TKPI_AP006', 70, m], ['TKPI_HR002', 55], ['TKPI_DR141', 80], ['TKPI_KR012', 5]], { equipment: ['panci', 'kompor'] });
pk('kos', 'siang', 'Nasi, tempe goreng, tumis kangkung', 'masak', [[NASI, 300], ['W_TEMPE_GORENG', 50, m], ['W_TUMIS_KANGKUNG', 100]], { equipment: ['rice-cooker', 'wajan'] });
pk('kos', 'siang', 'Nasi, tahu goreng, sayur bening', 'masak', [[NASI, 300], ['W_TAHU_GORENG', 120, m], ['W_SAYUR_BENING', 200]], { equipment: ['rice-cooker', 'wajan', 'panci'] });
pk('kos', 'siang', 'Nasi, oncom pepes, kangkung rebus, orek tempe', 'masak', [[NASI, 300], ['TKPI_CP054', 100, m], ['TKPI_DP014', 100], ['W_OREK_TEMPE', 50]], { equipment: ['rice-cooker', 'panci'] });
pk('kos', 'malam', 'Nasi, orek tempe, sayur bening', 'masak', [[NASI, 300], ['W_OREK_TEMPE', 50, m], ['W_SAYUR_BENING', 200]], { equipment: ['rice-cooker', 'wajan', 'panci'] });
pk('kos', 'malam', 'Nasi, telur balado, buncis rebus', 'masak', [[NASI, 300], ['W_TELUR_BALADO', 70, m], ['TKPI_DP003', 100]], { equipment: ['rice-cooker', 'wajan', 'panci'] });
pk('kos', 'malam', 'Nasi, ikan asin, bayam rebus, tempe goreng', 'masak', [[NASI, 300], ['TKPI_GP004', 30, m], ['TKPI_DP002', 100], ['W_TEMPE_GORENG', 50]], { equipment: ['rice-cooker', 'wajan', 'panci'] });
pk('kos', 'camilan', 'Pisang kepok', 'tanpa-masak', [['TKPI_ER081', 150, m]], { equipment: ['tanpa'] });
pk('kos', 'camilan', 'Ubi jalar kukus', 'masak', [['TKPI_BP011', 200, m]], { equipment: ['panci', 'kompor'] });
pk('kos', 'camilan', 'Kacang hijau rebus', 'masak', [['TKPI_CP005', 200, m]], { equipment: ['panci', 'kompor'] });

// ---- build ------------------------------------------------------------
const r1 = (x) => Math.round(x * 10) / 10;
const counters = {};
const usedTier = {};
const problems = [];
const packages = defs.map((d) => {
  const key = `${d.profile}.${d.slot}`;
  counters[key] = (counters[key] || 0) + 1;
  const id = `MP_${d.profile.toUpperCase()}_${d.slot.toUpperCase()}_${counters[key]}`;
  let kcal = 0, protein = 0, carb = 0, fat = 0, fibre = 0, fibreComplete = true;
  const allergens = new Set();
  let lactose = false, halal = true, estimate = false, tier = 1;
  const main = [];
  let fried = false, santan = false;
  const items = d.items.map(([foodId, grams, flag]) => {
    const f = foods.get(foodId);
    if (!f) { problems.push(`${id}: foodId ${foodId} tidak ada`); return null; }
    for (const k of ['kcalPer100', 'protein', 'carb', 'fat']) {
      if (typeof f[k] !== 'number') problems.push(`${id}: ${foodId} tanpa ${k}`);
    }
    if (f.flags && f.flags.length) problems.push(`${id}: ${foodId} bertanda ${f.flags.join(',')}`);
    const k = grams / 100;
    kcal += f.kcalPer100 * k; protein += f.protein * k; carb += f.carb * k; fat += f.fat * k;
    if (typeof f.fibre === 'number') fibre += f.fibre * k; else fibreComplete = false;
    const a = itemAllergens(f);
    for (const x of a.allergens) allergens.add(x);
    if (a.lactose) lactose = true;
    if (!itemHalal(f)) halal = false;
    if (f.isEstimate) estimate = true;
    const t = tierOf(foodId);
    usedTier[foodId] = t;
    tier = Math.max(tier, t);
    if (RX.santan.test(itemText(f))) santan = true;
    if (flag === 'm') { main.push(foodId); if (RX.fried.test(f.name_id)) fried = true; }
    const item = { foodId, name_id: f.name_id, grams };
    for (const h of f.household || []) {
      const q = grams / h.grams;
      if ([0.5, 1, 1.5, 2, 3, 4].includes(q)) { item.household = { code: h.code, label: h.label, qty: q }; break; }
    }
    if (flag === 'm') item.main = true;
    return item;
  });
  if (!main.length) problems.push(`${id}: tidak ada item utama`);
  const out = {
    id, profile: d.profile, slot: d.slot, name_id: d.name, mode: d.mode,
    items,
    kcal: Math.round(kcal), protein: r1(protein), carb: r1(carb), fat: r1(fat),
    fibre: r1(fibre), fibreComplete,
    budget: BUDGET[tier], halal, allergens: [...allergens].sort(), lactose,
    isEstimate: estimate,
    tags: { santan, goreng: fried, mainProtein: main },
  };
  if (d.equipment) out.equipment = d.equipment;
  return out;
});

if (problems.length) { console.error(problems.join('\n')); process.exit(1); }

const doc = {
  schema: 1,
  version: 1,
  source: 'meal-packages',
  note: 'Dibangun oleh tools/build-meal-packages.mjs dari foods-tkpi.json dan foods-estimates.json. kkal dan makro dihitung dari tabel (per 100 g x gram); jangan diubah tangan. Paket dengan isEstimate=true tampil sebagai perkiraan (R_EST). Label anggaran adalah urutan Hemat/Standar/Bebas dari tabel foodTier, bukan harga.',
  slots: SLOTS,
  profiles: PROFILES,
  budgets: ['Hemat', 'Standar', 'Bebas'],
  allergenCodes: ['egg', 'seafood', 'nuts', 'milk'],
  floorKcal: { R_FLOOR_F: 1200, R_FLOOR_M: 1500, used: 1500 },
  own: {
    profile: 'sendiri',
    label: 'Milik sendiri',
    slots: SLOTS,
    accepts: ['recipe', 'food'],
    packages: [],
    note: 'Hook untuk resep dan makanan milik pengguna (food.source=own, recipe.origin=own). Tidak ada paket bawaan; kkal dan makro dihitung dari tabel saat pengguna menyusunnya.',
  },
  foodTier: Object.fromEntries(Object.entries(usedTier).sort()),
  packages,
};
writeFileSync(join(root, 'content/meal-packages.json'), JSON.stringify(doc, null, 1) + '\n');

// summary
for (const p of PROFILES) {
  const mins = SLOTS.map((s) => Math.min(...packages.filter((x) => x.profile === p.id && x.slot === s).map((x) => x.kcal)));
  const maxs = SLOTS.map((s) => Math.max(...packages.filter((x) => x.profile === p.id && x.slot === s).map((x) => x.kcal)));
  console.log(`${p.id.padEnd(7)} min hari ${mins.reduce((a, b) => a + b, 0)}  maks hari ${maxs.reduce((a, b) => a + b, 0)}  (min per slot ${mins.join('/')})`);
}
console.log(`${packages.length} paket ditulis ke content/meal-packages.json`);
