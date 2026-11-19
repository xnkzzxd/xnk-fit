// C-07: map generic ingredient keys to TKPI codes and recompute regional rows. Usage: node tools/apply-tkpi-regional.mjs [--dry]
// Only keys with a clear TKPI equivalent (same food, same state as eaten) are mapped; all others stay generic ⚠.
import fs from 'fs';
const dir = new URL('../content/', import.meta.url).pathname;
const P = dir + 'foods-regional.json';
const data = JSON.parse(fs.readFileSync(P, 'utf8'));
const tkpi = new Map(JSON.parse(fs.readFileSync(dir + 'foods-tkpi.json', 'utf8')).foods.map(f => [f.code, f]));
const MAP = { nasi: 'AP001', ketan: 'AP017', telurdadar: 'HP001', singkong: 'BP008', ubi: 'BP011', kacanghijau: 'CP005', buncis: 'DP003', kecap: 'NP005', roti: 'AP024', susu: 'JR006', gulamerah: 'MP005' };
const dry = process.argv.includes('--dry');
const ref = data.ingredientReference;
for (const [k, code] of Object.entries(MAP)) {
  const t = tkpi.get(code);
  if (!t) throw new Error('no TKPI ' + code);
  ref[k] = { code, kcal: t.kcalPer100, protein: t.protein, carb: t.carb, fat: t.fat, fibre: t.fibre ?? 0, sodium: t.sodium ?? null, basis: `TKPI 2020 ${code} ${t.name_id}` };
}
const r1 = x => Math.round(x * 10) / 10;
let changed = 0; const notes = [];
for (const row of data.rows) {
  const tot = row.recipe.reduce((s, i) => s + i.grams, 0);
  const sum = f => row.recipe.reduce((s, i) => s + i.grams * (ref[i.ingredient][f] ?? 0), 0) / tot;
  const old = row.kcalPer100;
  row.kcalPer100 = r1(sum('kcal')); row.protein = r1(sum('protein')); row.carb = r1(sum('carb')); row.fat = r1(sum('fat')); row.fibre = r1(sum('fibre'));
  row.servingGrams = tot; row.servingKcal = Math.round(row.kcalPer100 * tot / 100);
  if (row.household?.[0]) row.household[0].grams = tot;
  for (const i of row.recipe) i.basis = ref[i.ingredient].code ? `TKPI ${ref[i.ingredient].code}` : 'generic-ref ⚠';
  const allCode = row.recipe.every(i => ref[i.ingredient].code);
  const allNa = row.recipe.every(i => ref[i.ingredient].sodium != null);
  if (allNa) row.sodium = Math.round(sum('sodium') * 10) / 10 * 1; // mg per 100 g, TKPI/label basis only
  else row.sodium = null;
  row.warn = !allCode;
  const n = row.recipe.filter(i => ref[i.ingredient].code).length;
  row.basis = `Resep perkiraan (bahan + gram); ${n}/${row.recipe.length} bahan memakai kode TKPI 2020 (lihat recipe.basis), sisanya acuan umum ⚠. Bobot porsi diasumsikan, BUKAN dari situs gerai.`;
  if (Math.abs(old - row.kcalPer100) / old > 0.1) { changed++; notes.push(`${row.id} ${old}→${row.kcalPer100}`); }
}
console.log(`rows ${data.rows.length}; kcal change >10%: ${changed}`);
console.log(notes.slice(0, 40).join('; '));
if (!dry) fs.writeFileSync(P, JSON.stringify(data, null, 1) + '\n');
