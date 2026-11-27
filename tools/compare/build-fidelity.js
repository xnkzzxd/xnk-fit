// Fidelity report: reference spec (docs/reference/xnk/spec) vs app measurements (FidelityTest JSON). Node only, no dependencies.
// Usage: node tools/compare/build-fidelity.js <screenshots dir (has fidelity/*.json)> [outDir=fidelity]
// Writes report.md and report.json. REPORT ONLY: always exits 0. Numbers are measurements, not a verdict; look at the pictures too.
const fs = require('fs');
const path = require('path');
const ROOT = path.resolve(__dirname, '..', '..');
const SPEC = path.join(ROOT, 'docs/reference/xnk/spec');
const IN = path.resolve(process.argv[2] || 'screenshots', 'fidelity');
const OUT = path.resolve(process.argv[3] || 'fidelity');
fs.mkdirSync(OUT, { recursive: true });

// Tolerances (dp/px after normalising the app to 390 dp wide): box x, w, h +-2; radius +-2; text size +-1 sp;
// colour: CIE76 delta E <= 5 for fills (about two just-noticeable steps; antialiasing and the one-pixel sampling stay inside it),
// <= 10 for text/ink colour (sampled from the most extreme pixel of anti-aliased glyphs). y is information only (status bar, scroll).
const TOL = { box: 2, radius: 2, sp: 1, dE: 5, dEInk: 10 };
// app tag -> reference component (spec JSON key) and what is compared.
const MAP = {
  pita_atas: { ref: 'pita_atas', f: ['x', 'w'] , note: 'app band measured below the status bar; height varies with date line' },
  kartu_hero_hitam: { ref: 'kartu_hero_hitam', f: ['x', 'w', 'h', 'bg', 'radius'] },
  px_sec: { ref: 'px_sec', f: ['x', 'w', 'h'] },
  judul_bagian: { ref: 'judul_bagian', f: ['sp', 'weight', 'ink'], text: true },
  judul_bagian_garis: { ref: 'judul_bagian_garis', f: ['w', 'h', 'bg'] },
  baris_tugas: { ref: 'baris_tugas', f: ['x', 'w', 'h'] },
  lingkaran_centang: { ref: 'lingkaran_centang', f: ['w', 'h'] },
  tugas_judul: { ref: 'tugas_judul', f: ['sp', 'weight', 'ink'], text: true },
  tugas_sub: { ref: 'tugas_sub', f: ['sp', 'weight', 'ink'], text: true },
  tombol_utama: { ref: 'tombol_utama', f: ['h', 'radius'], note: 'ref = .btn-primary (page); app first = on-ink variant, so colour is not compared' },
  tombol_sekunder: { ref: 'tombol_sekunder', f: ['h', 'radius'] },
  tombol_garis: { ref: 'tombol_garis', f: ['h', 'radius'] },
  bilah_bawah: { ref: 'bilah_bawah', f: ['x', 'w', 'h', 'bg', 'radius'] },
  tab: { ref: 'tab', f: ['h'] },
  tombol_plus_tengah: { ref: 'tombol_plus_tengah', f: ['w', 'h', 'bg', 'radius'] },
  stat_tile: { ref: 'stat_tile', f: ['h', 'bg', 'radius'] },
  sel_input: { ref: 'sel_input', f: ['h', 'bg', 'radius'] },
  tombol_centang: { ref: 'tombol_centang', f: ['w', 'h', 'bg', 'radius'] },
  chip: { ref: 'chip', f: ['h', 'radius'] },
  kartu_putih: { ref: 'kartu_putih', f: ['x', 'w', 'bg', 'radius'] },
};
const parseColor = (s) => {
  if (!s) return null;
  let m = s.match(/rgba?\(([^)]+)\)/);
  if (m) { const p = m[1].split(/[ ,\/]+/).filter(Boolean).map(Number); return { rgb: p.slice(0, 3), a: p.length > 3 ? p[3] : 1 }; }
  m = s.match(/color\(srgb ([^)]+)\)/);
  if (m) { const p = m[1].split(/[ \/]+/).filter(Boolean).map(Number); return { rgb: p.slice(0, 3).map((v) => v * 255), a: p.length > 3 ? p[3] : 1 }; }
  return null;
};
const lab = ([r, g, b]) => {
  const f = (c) => { c /= 255; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
  const [R, G, B] = [f(r), f(g), f(b)];
  const X = (0.4124 * R + 0.3576 * G + 0.1805 * B) / 0.95047, Y = 0.2126 * R + 0.7152 * G + 0.0722 * B, Z = (0.0193 * R + 0.1192 * G + 0.9505 * B) / 1.08883;
  const g2 = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  return [116 * g2(Y) - 16, 500 * (g2(X) - g2(Y)), 200 * (g2(Y) - g2(Z))];
};
const dE = (a, b) => { const A = lab(a), B = lab(b); return Math.hypot(A[0] - B[0], A[1] - B[1], A[2] - B[2]); };
const radiusOf = (css, w, h) => { const m = (css || '').match(/([\d.]+)(px|%)/); if (!m) return 0; return m[2] === '%' ? Math.min(w, h) * parseFloat(m[1]) / 100 : parseFloat(m[1]); };
const num = (v) => (typeof v === 'number' ? Math.round(v * 10) / 10 : v);

const files = fs.existsSync(IN) ? fs.readdirSync(IN).filter((f) => /^fid_.*\.json$/.test(f)) : [];
const report = { tolerances: TOL, variants: {} };
const md = ['# Gerbang kesetiaan: acuan XNK vs app', '',
  'Acuan = `docs/reference/xnk/spec` (demo hidup, px CSS). App = `FidelityTest` (dp, dinormalkan ke lebar 390). Toleransi: kotak ±2, radius ±2, teks ±1 sp, warna ΔE76 ≤ 5 (isian) / ≤ 10 (warna teks). y hanya info. Hanya laporan; angka bukan lulus/gagal tunggal.', ''];
if (!files.length) md.push('Tidak ada berkas `fidelity/fid_*.json` dari app (tes tidak berjalan atau gagal): tidak ada yang diukur.');
for (const file of files.sort()) {
  const app = JSON.parse(fs.readFileSync(path.join(IN, file), 'utf8'));
  const [dev, theme] = app.variant.split('_');
  if (dev !== 'hp' || !app.variant.endsWith('_1.0')) continue; // tablet is not normalised to 390 and font 1.3 changes sizes on purpose
  const specFile = path.join(SPEC, `${app.screen}_${dev}_${theme}.json`);
  if (!fs.existsSync(specFile)) { md.push(`## ${app.variant}`, `Spesifikasi acuan tidak ada: ${path.basename(specFile)}`, ''); continue; }
  const ref = JSON.parse(fs.readFileSync(specFile, 'utf8')).components;
  const k = 390 / (app.widthPx / app.density) / app.density; // px -> dp normalised to 390 dp wide
  const rows = [];
  for (const [tag, m] of Object.entries(MAP)) {
    const items = app.items.filter((i) => i.tag === tag);
    const refs = ref[m.ref] || [];
    const n = Math.max(1, Math.min(items.length || 1, refs.length || 1, 2));
    for (let i = 0; i < n; i++) {
      const a = items[i], r = refs[i];
      const row = { component: tag + (n > 1 ? `#${i + 1}` : ''), fields: [], status: '' };
      if (!a && !r) { row.status = 'belum ada padanan'; row.why = 'tidak ada di acuan maupun di app (tampilan ini)'; rows.push(row); continue; }
      if (!a) { row.status = 'belum ada padanan'; row.why = 'tidak terlihat di app (di luar layar atau belum dibuat)'; rows.push(row); continue; }
      if (!r) { row.status = 'belum ada padanan'; row.why = 'tidak ada di acuan layar ini'; rows.push(row); continue; }
      const A = { x: a.x * k, y: a.y * k, w: a.w * k, h: a.h * k };
      const R = { x: r.box.x, y: r.box.y, w: r.box.w, h: r.box.h };
      let bad = 0;
      const add = (name, refV, appV, tol, unit) => {
        const d = typeof refV === 'number' ? appV - refV : null; const ok = d === null ? refV === appV : Math.abs(d) <= tol;
        if (!ok) bad++;
        row.fields.push({ name, ref: num(refV), app: num(appV), diff: d === null ? null : num(d), ok, unit });
      };
      for (const f of m.f) {
        if (f === 'x' || f === 'w' || f === 'h') add(f, R[f], A[f], TOL.box, 'dp');
        else if (f === 'radius') {
          const rr = radiusOf(r.style.borderRadius, R.w, R.h);
          if (a.inset >= 0) { const d = 1.5, i = a.inset; const rpx = i < 1 ? 0 : (i + d) + Math.sqrt(2 * i * d); add('radius', rr, rpx * k, TOL.radius, 'dp'); }
        } else if (f === 'sp' && a.fontSp !== undefined) add('sp', parseFloat(r.style.fontSize), a.fontSp, TOL.sp, 'sp');
        else if (f === 'weight' && a.fontWeight !== undefined) add('weight', +r.style.fontWeight, a.fontWeight, 100, '');
        else if (f === 'bg') {
          const c = parseColor(r.style.backgroundColor);
          if (c && c.a > 0.9) { const e = dE(c.rgb, a.bg); const ok = e <= TOL.dE; if (!ok) bad++; row.fields.push({ name: 'bg', ref: `rgb(${c.rgb.map(Math.round)})`, app: `rgb(${a.bg})`, diff: num(e), ok, unit: 'ΔE' }); }
        } else if (f === 'ink') {
          const c = parseColor(r.style.color);
          if (c && c.a > 0.9) { const e = dE(c.rgb, a.ink); const ok = e <= TOL.dEInk; if (!ok) bad++; row.fields.push({ name: 'warna teks', ref: `rgb(${c.rgb.map(Math.round)})`, app: `rgb(${a.ink})`, diff: num(e), ok, unit: 'ΔE' }); }
        }
      }
      row.dy = num(A.y - R.y); row.status = bad ? 'berbeda' : 'sama'; if (m.note) row.note = m.note;
      rows.push(row);
    }
  }
  const cnt = (s) => rows.filter((r) => r.status === s).length;
  report.variants[app.variant] = { sama: cnt('sama'), berbeda: cnt('berbeda'), belum: cnt('belum ada padanan'), rows };
  md.push(`## ${app.screen} · ${app.variant}`, '', `**sama ${cnt('sama')} · berbeda ${cnt('berbeda')} · belum ada padanan ${cnt('belum ada padanan')}** (${rows.length} komponen)`, '',
    '| komponen | nilai acuan | nilai app | selisih | status |', '| --- | --- | --- | --- | --- |');
  for (const r of rows) {
    if (!r.fields.length) { md.push(`| ${r.component} | - | - | - | ${r.status}: ${r.why || ''} |`); continue; }
    r.fields.forEach((f, idx) => md.push(`| ${idx ? '' : r.component} | ${f.name} ${f.ref}${f.unit && f.unit !== 'ΔE' ? f.unit : ''} | ${f.app} | ${f.diff === null ? '-' : (f.diff > 0 && f.unit !== 'ΔE' ? '+' : '') + f.diff + (f.unit === 'ΔE' ? ' ΔE' : '')} ${f.ok ? '✓' : '✗'} | ${idx ? '' : r.status + (r.note ? ' (' + r.note + ')' : '') + ` · dy ${r.dy}`} |`));
  }
  md.push('');
}
fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify(report, null, 1));
fs.writeFileSync(path.join(OUT, 'report.md'), md.join('\n'));
console.log('fidelity report:', Object.entries(report.variants).map(([v, x]) => `${v} sama ${x.sama} berbeda ${x.berbeda} belum ${x.belum}`).join('; ') || 'no data');
