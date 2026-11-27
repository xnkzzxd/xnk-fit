const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const out = process.argv[2];
  const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
  const p = await ctx.newPage();
  await p.goto('file:///home/user/XNK/demo/index.html?klien');
  await p.waitForTimeout(6000);
  for (let i=0;i<3;i++){
    const t = p.locator('button:visible', {hasText:/^\s*Tutup\s*$/}).first();
    if (await t.count()) { await t.click({force:true, timeout:3000}).catch(()=>{}); await p.waitForTimeout(1200); }
    await p.keyboard.press('Escape'); await p.waitForTimeout(600);
  }
  await p.screenshot({ path: out + '/portal-hari-ini.png' });
  const tabs = await p.evaluate(() => Array.from(document.querySelectorAll('#tabbar > *')).map(e => (e.innerText||'').trim().replace(/\n/g,' ')));
  console.log('tabs', JSON.stringify(tabs));
  const n = tabs.length;
  const names = {};
  for (let i=0;i<n;i++){
    await p.locator('#tabbar > *').nth(i).click({force:true, timeout:4000}).catch(e=>console.log('click fail',i));
    await p.waitForTimeout(2500);
    await p.screenshot({ path: out + '/portal-tab' + i + '.png' });
  }
  await b.close();
})();
