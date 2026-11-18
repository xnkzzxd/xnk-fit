#!/usr/bin/env node
// Builds content/foods-tkpi.json from the TKPI 2020 PDF (task C-03).
// Usage: node tools/build-tkpi.mjs <tkpi.pdf | bbox.html> [out.json]
//   Run it from a folder other than the download folder. The PDF is NOT in the repo.
// Method: `pdftotext -bbox` gives every word with x/y. Rows are found by their code (AR001...),
// nutrient values are put in columns by x position against the unit header row of each page
// (so blank cells do not shift later values). Anything not clean goes to `needsHandCheck`, not guessed.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const input = process.argv[2];
const outFile = process.argv[3] || path.join(here, '..', 'content', 'foods-tkpi.json');
if (!input) { console.error('Usage: node tools/build-tkpi.mjs <tkpi.pdf|bbox.html> [out.json]'); process.exit(2); }

let htmlPath = input;
if (input.toLowerCase().endsWith('.pdf')) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'tkpi-bbox-'));
  htmlPath = path.join(dir, 'all.html');
  execFileSync('pdftotext', ['-bbox', input, htmlPath], { stdio: 'inherit' });
}
const html = fs.readFileSync(htmlPath, 'utf8');

// Column order as printed in the table (after KODE, NAMA, SUMBER).
const COLS = ['water', 'kcal', 'protein', 'fat', 'carb', 'fibre', 'ash', 'calcium', 'phosphorus', 'iron', 'sodium',
  'potassium', 'copper', 'zinc', 'retinol', 'betaCarotene', 'totalCarotene', 'thiamin', 'riboflavin', 'niacin', 'vitC'];
const GROUPS = { A: 'Serealia dan hasil olahannya', B: 'Umbi berpati dan hasil olahannya', C: 'Kacang, biji, bean dan hasil olahannya',
  D: 'Sayuran dan hasil olahannya', E: 'Buah dan hasil olahannya', F: 'Daging, unggas dan hasil olahannya',
  G: 'Ikan, kerang, udang dan hasil olahannya', H: 'Telur dan hasil olahannya', J: 'Susu dan hasil olahannya',
  K: 'Lemak dan minyak', M: 'Gula, sirup dan konfeksioneri', N: 'Bumbu', Q: 'Minuman' }; // from the PDF section headings 4.1-4.13

// Upper limits per 100 g edible portion; a printed value above one is flagged `implausible:<field>` (kept as printed).
const MAX = { kcalPer100: 920, protein: 100, carb: 100, fat: 100, fibre: 100, water: 100, ash: 100, bdd: 100,
  sodium: 40000, calcium: 5000, phosphorus: 5000, iron: 300, potassium: 10000, copper: 50, zinc: 100,
  retinol: 30000, betaCarotene: 60000, totalCarotene: 100000, thiamin: 20, riboflavin: 20, niacin: 200, vitC: 3000 };
const NUM = /^\d+(\.\d+)?$/;
const CODE = /^[A-Z]{2}\d{3}$/;
const pages = html.split('<page ').slice(1);
const rows = [];
const hand = []; // { page, code, reason }
let tablePages = 0;

for (let pi = 0; pi < pages.length; pi++) {
  const pageNo = pi + 1;
  const words = [...pages[pi].matchAll(/<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="[\d.]+">([^<]*)<\/word>/g)]
    .map(m => ({ x0: +m[1], y: +m[2], x1: +m[3], t: m[4].replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>') }));
  if (!words.some(w => w.t === 'PER') || !words.some(w => w.t === '(Kal)')) continue;
  // unit header row: (g)(Kal)(g)... -> column centers
  const kal = words.find(w => w.t === '(Kal)');
  const units = words.filter(w => Math.abs(w.y - kal.y) < 2 && /^\((g|Kal|mg|mcg)\)$/.test(w.t)).sort((a, b) => a.x0 - b.x0);
  if (units.length !== COLS.length) { hand.push({ page: pageNo, code: null, reason: `header has ${units.length} unit columns, expected ${COLS.length}` }); continue; }
  const centers = units.map(u => (u.x0 + u.x1) / 2);
  tablePages++;
  const heading = words.find(w => /^4\.\d+\.?$/.test(w.t));
  let groupHeading = null;
  if (heading) groupHeading = words.filter(w => Math.abs(w.y - heading.y) < 2 && w.x0 >= heading.x0).sort((a, b) => a.x0 - b.x0).map(w => w.t).join(' ');

  const codes = words.filter(w => w.x0 < 70 && CODE.test(w.t) && w.y > kal.y).sort((a, b) => a.y - b.y);
  const rowY = codes.map(c => c.y);
  const own = (y) => { // nearest row by y
    let best = -1, d = 1e9;
    rowY.forEach((ry, i) => { const dd = Math.abs(ry - y); if (dd < d) { d = dd; best = i; } });
    return best;
  };
  const byRow = codes.map(() => ({ name: [], source: [], vals: [], stray: [] }));
  const dataWords = words.filter(w => w.y > kal.y + 4 && !(w.x0 < 70 && CODE.test(w.t)) && !/^(TABEL|KOMPOSISI|PANGAN|INDONESIA)$/.test(w.t) || false);
  const firstC = centers[0];
  const srcStart = firstC - 66; // source column starts about 62-66 pt left of the water column centre
  for (const w of dataWords) {
    if (w.y > 540) continue; // footer
    if (w.x0 > 815 && /^[A-Z]{2}$/.test(w.t)) continue; // side index tab (group letters), not data
    if (/^(OLAHAN|PRODUK|TUNGGAL\/SINGLE|KOMPOSIT\/COMPOSITE|TUNGGAL|KOMPOSIT|\/|SINGLE|COMPOSITE|OLAHAN\/PRODUK\/KOMPOSIT)$/.test(w.t)) continue; // sub-heading rows
    const i = own(w.y);
    if (i < 0) continue;
    const r = byRow[i];
    const dy = Math.abs(w.y - rowY[i]);
    const c = (w.x0 + w.x1) / 2;
    if (c < firstC - 12) {
      if (w.x0 >= srcStart && dy < 4) r.source.push(w); else r.name.push(w);
      continue;
    }
    if (!NUM.test(w.t)) { r.stray.push(w); continue; }
    if (c > centers[20] + 14) { r.vals.push({ col: 'bdd', w, c }); continue; }
    let best = 0;
    centers.forEach((cc, k) => { if (Math.abs(cc - c) < Math.abs(centers[best] - c)) best = k; });
    r.vals.push({ col: COLS[best], w, c, dist: Math.abs(centers[best] - c) });
  }
  codes.forEach((cw, i) => {
    const r = byRow[i];
    const code = cw.t;
    const problems = [];
    const nm = r.name.sort((a, b) => a.y - b.y || a.x0 - b.x0).map(w => w.t).join(' ').trim();
    const src = r.source.sort((a, b) => a.x0 - b.x0).map(w => w.t).join(' ').replace(/-\s+/, '-').trim();
    const v = {};
    for (const e of r.vals) {
      if (e.dist > 13 && e.col !== 'bdd') problems.push(`value ${e.w.t} ${e.dist.toFixed(0)}pt off column ${e.col}`);
      if (e.col in v) problems.push(`two values in column ${e.col}`);
      v[e.col] = +e.w.t;
    }
    if (r.stray.length) problems.push(`non-numeric text in value area: ${r.stray.map(s => s.t).join(' ')}`);
    if (!nm) problems.push('no name');
    for (const c of ['water', 'kcal', 'protein', 'fat', 'carb']) if (v[c] === undefined) problems.push(`missing core value ${c}`);
    // BDD may be blank in the PDF (section 2.7: some foods have no BDD) -> null, not a defect
    if (problems.length) { hand.push({ page: pageNo, code, name: nm || null, reason: problems.join('; ') }); return; }
    rows.push({ code, page: pageNo, name: nm, src, v, group: groupHeading });
  });
}

// duplicates: keep both out of the table and report
const count = {};
rows.forEach(r => { count[r.code] = (count[r.code] || 0) + 1; });
const dups = Object.keys(count).filter(k => count[k] > 1);

const foods = rows.filter(r => !dups.includes(r.code)).map(r => {
  const { v } = r;
  const second = r.code[1]; // R raw, P processed
  const f = {
    id: `TKPI_${r.code}`,
    code: r.code,
    source: 'tkpi',
    name_id: r.name,
    name_en: null,
    group: r.code[0],
    groupName: GROUPS[r.code[0]] || null,
    processing: second === 'R' ? 'raw' : second === 'P' ? 'processed' : null,
    kcalPer100: v.kcal,
    protein: v.protein,
    carb: v.carb,
    fat: v.fat,
    fibre: v.fibre ?? null,
    sodium: v.sodium ?? null,
    micro: {},
    bdd: v.bdd ?? null,
    sourceRef: r.src || null,
    page: r.page,
    household: [],
    isEstimate: false,
    halal: null,
    allergens: null,
  };
  // Numbers are copied as printed. These flags only mark rows whose PRINTED numbers disagree with each other
  // (a defect in the source table, or a different energy factor); B-04 may exclude flagged rows from search.
  const atwater = 4 * f.protein + 4 * f.carb + 9 * f.fat;
  f.flags = [];
  if (Math.abs(atwater - f.kcalPer100) > Math.max(0.15 * f.kcalPer100, 10)) f.flags.push('kcalVsMacros');
  const proximate = (v.water) + f.protein + f.fat + f.carb + (v.ash ?? 0);
  if (Math.abs(proximate - 100) > 6) f.flags.push('proximateSum');
  if (f.fibre !== null && f.fibre > f.carb + 0.05) f.flags.push('fibreOverCarb');
  for (const k of ['water', 'ash', 'calcium', 'phosphorus', 'iron', 'potassium', 'copper', 'zinc', 'retinol', 'betaCarotene',
    'totalCarotene', 'thiamin', 'riboflavin', 'niacin', 'vitC']) f.micro[k] = v[k] ?? null;
  if (f.protein + f.carb + f.fat > 100.5) f.flags.push('macrosOver100');
  for (const [k, val] of Object.entries({ kcalPer100: f.kcalPer100, protein: f.protein, carb: f.carb, fat: f.fat, fibre: f.fibre,
    sodium: f.sodium, bdd: f.bdd, ...f.micro })) if (typeof val === 'number' && val > MAX[k]) f.flags.push(`implausible:${k}`);
  return f;
});

const out = {
  schema: 1,
  version: 1,
  source: 'tkpi',
  note: 'TKPI 2020 (Kemenkes), per 100 g BDD. Angka disalin dari PDF; sel kosong = null (tidak dianalisis). halal/allergens null: TKPI tidak memuatnya. Baris di needsHandCheck belum masuk tabel.',
  counts: { rows: foods.length, flaggedSourceInconsistent: foods.filter(f => f.flags.length).length, needsHandCheck: hand.length + dups.length, tablePages },
  foods,
  needsHandCheck: [...hand, ...dups.map(c => ({ page: rows.filter(r => r.code === c).map(r => r.page).join(','), code: c, reason: 'duplicate code' }))],
};
fs.mkdirSync(path.dirname(outFile), { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(out, null, 1) + '\n');
console.log(`table pages: ${tablePages}\nrows: ${foods.length}\nneeds hand check: ${out.needsHandCheck.length}\n-> ${outFile}`);
