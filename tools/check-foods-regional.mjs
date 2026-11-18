// Checks content/foods-regional.json (C-07). Usage: node tools/check-foods-regional.mjs
import fs from 'fs';
const dir = new URL('../content/', import.meta.url).pathname;
const data = JSON.parse(fs.readFileSync(dir + 'foods-regional.json', 'utf8'));
const rows = data.rows;
const errors = [], warns = [];
const req = ['id','source','name_id','name_en','kcalPer100','protein','carb','fat','fibre','household','isEstimate','halal','allergens','recipe','basis'];
const banned = /\b(babi|pork|bacon|ham|lard|saksang|b2|rica-rica babi|bir|beer|wine|tuak|arak|brem|sake|alkohol|alcohol|ballo|cap tikus)\b/i;
const ids = new Set(), names = new Set();
// other food files (other tasks): duplicate check on id and name_id
const others = [];
for (const f of fs.readdirSync(dir)) {
  if (!/^foods-.*\.json$/.test(f) || f === 'foods-regional.json') continue;
  try {
    const j = JSON.parse(fs.readFileSync(dir + f, 'utf8'));
    const arr = Array.isArray(j) ? j : (j.rows || j.foods || []);
    for (const r of arr) others.push({ f, id: r.id ?? r.code, n: (r.name_id ?? r.name ?? '').toLowerCase().trim() });
  } catch (e) { warns.push(`cannot read ${f}: ${e.message}`); }
}
const otherIds = new Map(others.map(o => [o.id, o.f])), otherNames = new Map(others.map(o => [o.n, o.f]));
for (const r of rows) {
  for (const k of req) if (r[k] === undefined || r[k] === null || r[k] === '') errors.push(`${r.id}: missing ${k}`);
  if (!['warung-est','bought'].includes(r.source)) errors.push(`${r.id}: source ${r.source}`);
  if (r.source === 'warung-est' && r.isEstimate !== true) errors.push(`${r.id}: warung-est needs isEstimate=true`);
  if (r.source === 'bought' && !(r.sourceUrl && r.sourceDate)) errors.push(`${r.id}: bought needs sourceUrl and sourceDate`);
  if (r.halal !== true) errors.push(`${r.id}: not halal`);
  if (banned.test(r.name_id + ' ' + r.name_en)) errors.push(`${r.id}: pork/alcohol term in name`);
  for (const i of r.recipe || []) if (banned.test(i.ingredient) || !i.grams || !i.basis) errors.push(`${r.id}: bad ingredient ${i.ingredient}`);
  const k = 4 * r.protein + 4 * r.carb + 9 * r.fat;
  if (Math.abs(k - r.kcalPer100) > 0.1 * r.kcalPer100) errors.push(`${r.id}: macros ${k.toFixed(0)} vs kcal ${r.kcalPer100}`);
  if (ids.has(r.id)) errors.push(`dup id ${r.id}`); ids.add(r.id);
  const n = r.name_id.toLowerCase().trim();
  if (names.has(n)) errors.push(`dup name ${r.name_id}`); names.add(n);
  if (otherIds.has(r.id)) errors.push(`${r.id}: id also in ${otherIds.get(r.id)}`);
  if (otherNames.has(n)) errors.push(`${r.id}: name also in ${otherNames.get(n)}`);
  if (r.sodium === null) warns.push(`${r.id}: sodium null (no basis)`);
}
const warnRows = rows.filter(r => r.warn).length;
console.log(`rows ${rows.length}; ⚠ rows ${warnRows}; other files compared: ${[...new Set(others.map(o => o.f))].join(', ') || 'none present'}`);
console.log(`sodium null: ${rows.filter(r => r.sodium === null).length}`);
for (const e of errors) console.log('ERROR', e);
console.log(errors.length ? `FAIL (${errors.length} errors)` : 'OK');
process.exit(errors.length ? 1 : 0);
