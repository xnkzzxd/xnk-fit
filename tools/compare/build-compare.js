// Side-by-side "reference (XNK) vs app" images. Usage: node tools/compare/build-compare.js <appScreenshotsDir> [outDir=compare]
// Left = reference, right = app. Needs only `playwright` (the pairs are laid out as HTML and photographed). Never exits non-zero for missing pairs.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const APP = path.resolve(process.argv[2] || 'screenshots');
const OUT = path.resolve(process.argv[3] || 'compare');
const REF = path.join(ROOT, 'docs/reference/xnk/screens');
const map = JSON.parse(fs.readFileSync(path.join(__dirname, 'map.json'), 'utf8'));
const URI = (f) => 'data:image/png;base64,' + fs.readFileSync(f).toString('base64');

// Rough hints only, computed in the page on a downscaled copy: share of dark pixels in the top 12% (the black band),
// mean luminance, luminance spread (contrast) and colour-histogram overlap (1 = same palette). Not a pass/fail measure.
const METRICS = `(async (a, b) => {
  const load = (u) => new Promise((r) => { const i = new Image(); i.onload = () => r(i); i.src = u; });
  const stat = async (u) => {
    const i = await load(u), w = 120, h = Math.round(120 * i.height / i.width);
    const c = document.createElement('canvas'); c.width = w; c.height = h;
    const x = c.getContext('2d'); x.drawImage(i, 0, 0, w, h);
    const d = x.getImageData(0, 0, w, h).data; let sum = 0, sq = 0, dark = 0, n = 0; const hist = {};
    const top = Math.max(1, Math.round(h * 0.12));
    for (let y = 0; y < h; y++) for (let k = 0; k < w; k++) {
      const p = (y * w + k) * 4, l = 0.2126 * d[p] + 0.7152 * d[p + 1] + 0.0722 * d[p + 2];
      sum += l; sq += l * l;
      if (y < top) { n++; if (l < 60) dark++; }
      const key = (d[p] >> 6) + ',' + (d[p + 1] >> 6) + ',' + (d[p + 2] >> 6); hist[key] = (hist[key] || 0) + 1;
    }
    const t = w * h, m = sum / t;
    for (const k in hist) hist[k] /= t;
    return { topDark: dark / n, mean: m, spread: Math.sqrt(sq / t - m * m), hist };
  };
  const A = await stat(a), B = await stat(b); let o = 0;
  for (const k in A.hist) if (B.hist[k]) o += Math.min(A.hist[k], B.hist[k]);
  return { topDark: [A.topDark, B.topDark], mean: [A.mean, B.mean], spread: [A.spread, B.spread], palette: o };
})`;

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const appFiles = fs.existsSync(APP) ? fs.readdirSync(APP) : [];
  const appFor = (screen, dev, th) => {
    const base = `${screen}_${dev}_${th}`;
    const hit = appFiles.find((f) => f === `${base}_1.0.png`) || appFiles.find((f) => f.startsWith(base + '_') && f.endsWith('.png'));
    return hit ? path.join(APP, hit) : null;
  };
  const b = await chromium.launch();
  const rows = [], withRef = [], without = [], missing = [];
  for (const [screen, m] of Object.entries(map.screens)) {
    if (!m.ref) { without.push(`${screen}: ${m.reason}`); continue; }
    withRef.push(screen);
    for (const dev of ['hp', 'tablet']) {
      if (dev === 'tablet' && !map.tabletRefs.includes(m.ref)) continue;
      for (const th of ['terang', 'gelap']) {
        const ref = path.join(REF, `${m.ref}_${dev}_${th}.png`), app = appFor(screen, dev, th);
        const name = `${screen}_${dev}_${th}`;
        if (!fs.existsSync(ref)) { missing.push(`${name}: reference file missing`); continue; }
        if (!app) { missing.push(`${name}: no app screenshot`); continue; }
        const ctx = await b.newContext({ viewport: { width: 400, height: 400 } });
        const p = await ctx.newPage();
        const [ru, au] = [URI(ref), URI(app)];
        const H = dev === 'hp' ? 1000 : 1100;
        await p.setContent(`<body style="margin:0;background:#888;font:600 20px sans-serif;color:#fff"><div style="display:flex;gap:16px;padding:12px;align-items:flex-start;width:max-content">
          <div><div style="padding:4px 0">ACUAN XNK · ${m.ref}</div><img src="${ru}" style="height:${H}px;display:block"></div>
          <div><div style="padding:4px 0">APP · ${screen}</div><img src="${au}" style="height:${H}px;display:block"></div></div></body>`);
        await p.waitForFunction(() => [...document.images].every((i) => i.complete && i.naturalWidth));
        const met = await p.evaluate(`${METRICS}(${JSON.stringify(ru)}, ${JSON.stringify(au)})`);
        await p.screenshot({ path: path.join(OUT, name + '.png'), fullPage: true });
        await ctx.close();
        rows.push({ name, screen, dev, th, met });
      }
    }
  }
  await b.close();
  const pct = (x) => Math.round(x * 100) + '%', n0 = (x) => Math.round(x);
  const line = (r) => `| ${r.name} | ${pct(r.met.topDark[0])} / ${pct(r.met.topDark[1])} | ${n0(r.met.mean[0])} / ${n0(r.met.mean[1])} | ${n0(r.met.spread[0])} / ${n0(r.met.spread[1])} | ${pct(r.met.palette)} |`;
  const md = ['# Acuan XNK vs app', '',
    `Pairs: ${rows.length}. Left of each image = reference (XNK demo), right = app. Numbers are hints only; look at the pictures.`, '',
    '## Screens with a reference', withRef.map((s) => `- ${s}`).join('\n'), '',
    '## Screens without a reference', without.map((s) => `- ${s}`).join('\n') || '- (none)', '',
    '## Pairs not built', missing.map((s) => `- ${s}`).join('\n') || '- (none)', '',
    '## Rough metrics (reference / app)', '', '| pair | dark top band | mean luma | contrast | palette overlap |', '| --- | --- | --- | --- | --- |',
    ...rows.map(line), ''].join('\n');
  fs.writeFileSync(path.join(OUT, 'summary.md'), md);
  const html = `<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width"><title>Acuan XNK vs app</title><body style="font-family:sans-serif;margin:12px"><h1>Acuan XNK vs app</h1>`
    + ['hp', 'tablet'].map((dev) => `<h2>${dev}</h2>` + rows.filter((r) => r.dev === dev).map((r) => `<h3>${r.name}</h3><a href="${r.name}.png"><img src="${r.name}.png" style="max-width:100%;height:auto"></a>`).join('')).join('')
    + `<p><a href="summary.md">summary.md</a></p>`;
  fs.writeFileSync(path.join(OUT, 'index.html'), html);
  console.log(`pairs ${rows.length}, not built ${missing.length}, without reference ${without.length}`);
})().catch((e) => { console.log('build-compare failed:', e.message); process.exit(0); });
