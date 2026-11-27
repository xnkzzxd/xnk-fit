// Captures the XNK demo client portal (docs/reference/xnk/demo/index.html?klien) into docs/reference/xnk/screens/.
// Usage: node tools/compare/capture-reference.js [outDir]   (needs `playwright` + Chromium)
// Names: <screen>_<hp|tablet>_<terang|gelap>.png. hp = 390x844, tablet = 800x1280 (same dp as the app's tablet variant).
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const ROOT = path.resolve(__dirname, '..', '..');
const DEMO = 'file://' + path.join(ROOT, 'docs/reference/xnk/demo/index.html') + '?klien';
const OUT = process.argv[2] || path.join(ROOT, 'docs/reference/xnk/screens');

const DEVICES = { hp: { width: 390, height: 844, mobile: true }, tablet: { width: 800, height: 1280, mobile: false } };
const THEMES = ['terang', 'gelap'];
// name -> JS run in the page (the demo's own navigation functions; clicking the bottom bar is unreliable: index 2 is the + button).
const SCREENS = {
  'hari-ini': "window.navigate('public-dashboard')",
  'latihan': "window.navigate('public-program')",
  'logger': "window.clientQuick('workout')",
  'makan': "window.navigate('public-food')",
  'planner': "window.navigate('public-plan')",
  'progres': "window.navigate('public-progress')",
  'saya': "window.navigate('public-more')",
  'kartu': "window.navigate('public-share')",
  'pengaturan': "window.openSettings()",
  'paket': "window.navigate('public-pack')",
  'info': "window.navigate('public-info')",
};

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await chromium.launch();
  for (const [dev, d] of Object.entries(DEVICES)) {
    for (const theme of THEMES) {
      const ctx = await b.newContext({ viewport: { width: d.width, height: d.height }, deviceScaleFactor: 1, isMobile: d.mobile, hasTouch: d.mobile, colorScheme: theme === 'gelap' ? 'dark' : 'light', reducedMotion: 'reduce' });
      const p = await ctx.newPage();
      await p.goto(DEMO);
      await p.waitForFunction(() => typeof window.navigate === 'function' && document.querySelector('#tabbar > *'), null, { timeout: 30000 });
      await p.addStyleTag({ content: "body > div[style*='z-index:99999']{display:none !important}" }); // the red DEMO pill
      await p.waitForTimeout(1500);
      for (const [name, js] of Object.entries(SCREENS)) {
        // fresh state per screen: close sheets, set theme, go home first so a screen never inherits the last one's sheet
        await p.evaluate((t) => { try { window.closeModal && window.closeModal(); } catch (e) {} window.setTheme(t); window.navigate('public-dashboard'); }, theme === 'gelap' ? 'dark' : 'light');
        await p.keyboard.press('Escape');
        await p.waitForTimeout(500);
        await p.evaluate(js).catch((e) => console.log('FAIL', name, e.message));
        await p.waitForTimeout(1200);
        if (name === 'logger') await p.waitForTimeout(800);
        const file = path.join(OUT, `${name}_${dev}_${theme}.png`);
        await p.screenshot({ path: file });
        console.log('ok', path.basename(file));
      }
      await ctx.close();
    }
  }
  await b.close();
})();
