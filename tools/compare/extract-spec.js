// Extracts a measurable spec from the live reference (XNK demo, ?klien): per screen x device x theme, the box (CSS px) and
// computed style of each important component, plus the :root tokens. Static output in docs/reference/xnk/spec/ (redo only when the reference changes).
// Usage: NODE_PATH=$(npm root -g) node tools/compare/extract-spec.js [outDir]
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const ROOT = path.resolve(__dirname, '..', '..');
const DEMO = 'file://' + path.join(ROOT, 'docs/reference/xnk/demo/index.html') + '?klien';
const OUT = process.argv[2] || path.join(ROOT, 'docs/reference/xnk/spec');
const DEVICES = { hp: { width: 390, height: 844, mobile: true }, tablet: { width: 1024, height: 1366, mobile: false } };
const SCREENS = {
  'hari-ini': "window.navigate('public-dashboard')", latihan: "window.navigate('public-program')", logger: "window.clientQuick('workout')",
  makan: "window.navigate('public-food')", planner: "window.navigate('public-plan')", saya: "window.navigate('public-more')", pengaturan: "window.openSettings()",
};
// component -> CSS selector (first visible match; "all" lists up to 3 matches). Keep in sync with spec/INDEX.md.
const COMPONENTS = {
  pita_atas: 'header.topbar', judul_pita: 'h1.topbar-title', band: '.px-band', kartu_hero_hitam: 'section.card-ink', hero_eyebrow: '.ih-eyebrow', hero_judul: '.px-hero-t, .nx-when',
  judul_bagian: 'h3.section-title', judul_bagian_garis: '.section-title::before', px_sec: '.px-sec', baris_tugas: 'button.todo-row', lingkaran_centang: '.todo-check', tugas_judul: '.todo-title', tugas_sub: '.todo-sub',
  tombol_utama: '.btn-primary', tombol_sekunder: '.btn-secondary', tombol_garis: '.btn-outline', tombol_ghost: '.btn-ghost',
  bilah_bawah: 'nav.tabbar', tab: 'button.tab', tombol_plus_tengah: 'button.tab-fab', stat_tile: '.px-tile, .mtile, .wl-st, .ih-stat', sel_input: 'input.wl-in', tombol_centang: 'button.wl-ck',
  nomor_set: 'button.wl-setno', chip: '.pill', kartu_putih: 'div.card', daftar_item: 'button.list-item', ikon_chip: '.icon-chip', ath_nama: '.ath-name', ring: '.ring-wrap',
};
const PROPS = ['backgroundColor', 'color', 'fontFamily', 'fontSize', 'fontWeight', 'lineHeight', 'letterSpacing', 'borderRadius', 'padding', 'gap', 'boxShadow', 'border', 'opacity'];
const PAGE_FN = `(args) => {
  const [COMPONENTS, PROPS] = args, out = {};
  const vis = (e) => { const r = e.getBoundingClientRect(), s = getComputedStyle(e); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && !e.closest('.splash,.detail,.wl,.layer') ; };
  for (const [name, sel] of Object.entries(COMPONENTS)) {
    const pseudo = sel.endsWith('::before') ? '::before' : null;
    const base = pseudo ? sel.slice(0, -8) : sel;
    const els = [...document.querySelectorAll(base)].filter((e) => (name === 'tombol_centang' || name === 'sel_input' || name === 'nomor_set') ? (e.getBoundingClientRect().width > 0 && !e.closest('.splash')) : vis(e)).slice(0, 3);
    if (!els.length) continue;
    out[name] = els.map((e) => {
      const r = e.getBoundingClientRect(), s = getComputedStyle(e, pseudo), o = { selector: sel, text: pseudo ? '' : (e.textContent || '').trim().slice(0, 40), box: { x: +r.x.toFixed(1), y: +(r.y + scrollY).toFixed(1), w: +r.width.toFixed(1), h: +r.height.toFixed(1) }, style: {} };
      if (pseudo) { o.box = { x: +r.x.toFixed(1), y: +(r.y + scrollY).toFixed(1), w: parseFloat(s.width) || 0, h: parseFloat(s.height) || 0 }; }
      for (const p of PROPS) o.style[p] = s[p];
      return o;
    });
  }
  return out;
}`;
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await chromium.launch();
  let tokens = {}, n = 0;
  for (const [dev, d] of Object.entries(DEVICES)) for (const theme of ['terang', 'gelap']) {
    const ctx = await b.newContext({ viewport: { width: d.width, height: d.height }, deviceScaleFactor: 1, isMobile: d.mobile, hasTouch: d.mobile, colorScheme: theme === 'gelap' ? 'dark' : 'light', reducedMotion: 'reduce' });
    const p = await ctx.newPage();
    await p.goto(DEMO);
    await p.waitForFunction(() => typeof window.navigate === 'function' && document.querySelector('#tabbar > *'), null, { timeout: 30000 });
    await p.waitForTimeout(1500);
    for (const [name, js] of Object.entries(SCREENS)) {
      await p.evaluate((t) => { try { window.closeModal && window.closeModal(); } catch (e) {} window.setTheme(t); window.navigate('public-dashboard'); }, theme === 'gelap' ? 'dark' : 'light');
      await p.keyboard.press('Escape'); await p.waitForTimeout(400);
      await p.evaluate(js).catch((e) => console.log('FAIL', name, e.message));
      await p.waitForTimeout(1200);
      const comps = await p.evaluate(eval(PAGE_FN), [COMPONENTS, PROPS]);
      const doc = { screen: name, device: dev, theme, viewport: { w: d.width, h: d.height }, components: comps };
      fs.writeFileSync(path.join(OUT, `${name}_${dev}_${theme}.json`), JSON.stringify(doc, null, 1));
      n++;
    }
    if (dev === 'hp') tokens[theme] = await p.evaluate(() => {
      const cs = getComputedStyle(document.documentElement), t = {};
      for (const sheet of document.styleSheets) { let rules; try { rules = sheet.cssRules; } catch (e) { continue; } for (const r of rules) if (r.style) for (const pr of r.style) if (pr.startsWith('--') && /^:root/.test(r.selectorText || '')) t[pr] = cs.getPropertyValue(pr).trim(); }
      const fonts = [...document.fonts].map((f) => f.family + ' ' + f.weight + ' ' + f.status);
      return { vars: t, bodyFont: getComputedStyle(document.body).fontFamily, htmlFontSize: cs.fontSize, fonts };
    });
    await ctx.close();
  }
  fs.writeFileSync(path.join(OUT, 'tokens.json'), JSON.stringify(tokens, null, 1));
  await b.close();
  console.log('wrote', n, 'specs + tokens.json to', OUT);
})();
