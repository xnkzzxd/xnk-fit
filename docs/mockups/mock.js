/* XNK Fit mockups (D-05): shared shell, theme, sheets and small interactions. No server, no network.
   URL params: ?theme=light|dark  &accent=biru|hijau|toska|ungu|pink|oranye  &big=1  &embed=1  &state=<group:name>
   ponytail: mockup-only code, thrown away when B-01 starts the Compose app. */
(function () {
  var P = new URLSearchParams(location.search);
  var root = document.documentElement;
  var theme = P.get('theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  root.dataset.theme = theme;
  if (P.get('accent')) root.dataset.accent = P.get('accent');
  if (P.get('big')) root.classList.add('big');
  if (P.get('embed')) root.classList.add('embed');

  // Icons: simple outlined strokes in a 24 box (stand-ins for Material Symbols outlined, Design §11).
  var I = {
    home: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
    dumbbell: 'M6 7v10M3 9v6M18 7v10M21 9v6M6 12h12',
    plus: 'M12 5v14M5 12h14',
    food: 'M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M16 3c-2 1-3 4-3 7h3v11',
    user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c1-4 4-6 8-6s7 2 8 6',
    back: 'M15 5l-7 7 7 7',
    chev: 'M9 5l7 7-7 7',
    down: 'M6 9l6 6 6-6',
    close: 'M6 6l12 12M18 6L6 18',
    check: 'M5 12l5 5 9-10',
    timer: 'M12 8v5l3 2M9 2h6M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16z',
    swap: 'M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7',
    gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19 12l2-1-1-3-2 .3-1.3-1.3L17 5l-3-1-1 2h-2L10 4 7 5l.3 2L6 8.3 4 8l-1 3 2 1v0l-2 1 1 3 2-.3L7.3 17 7 19l3 1 1-2h2l1 2 3-1-.3-2 1.3-1.3 2 .3 1-3z',
    info: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 11v6M12 7h.01',
    scale: 'M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM8 9a4 4 0 0 1 8 0zM12 9l1.5-2',
    run: 'M13 4a1.5 1.5 0 1 0 0-.01M8 21l3-6 3 2v5M6 12l3-3 4 1 2 3 3 1M11 15l-1-4',
    camera: 'M4 8h3l2-3h6l2 3h3v11H4zM12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
    bell: 'M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 21h4',
    share: 'M12 3v12M7 8l5-5 5 5M5 14v6h14v-6',
    search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5',
    star: 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z',
    lock: 'M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4',
    cloud: 'M7 18a5 5 0 0 1-.6-10A6 6 0 0 1 18 9a4.5 4.5 0 0 1-.5 9z',
    download: 'M12 4v11M7 10l5 5 5-5M5 20h14',
    trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13',
    edit: 'M4 20h4L19 9l-4-4L4 16zM14 6l4 4',
    more: 'M5 12h.01M12 12h.01M19 12h.01',
    play: 'M7 4l13 8-13 8z',
    flame: 'M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-3 2-4 2-7 1 1 2 2 3 2 0-2 0-3 0-5z',
    cal: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
    list: 'M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01',
    trophy: 'M8 4h8v5a4 4 0 0 1-8 0zM8 6H4a3 3 0 0 0 4 4M16 6h4a3 3 0 0 1-4 4M12 13v4M8 21h8M9 17h6',
    heart: 'M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.5A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z',
    leaf: 'M5 19c0-9 6-14 15-15-1 9-6 15-15 15zM5 19l7-7',
    copy: 'M9 9h11v11H9zM5 15H4V4h11v1',
    eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
    eyeoff: 'M3 3l18 18M10.6 6.1A10 10 0 0 1 12 6c6 0 10 6 10 6a17 17 0 0 1-3 3.4M6.6 6.6C3.9 8.3 2 12 2 12s4 6 10 6a9 9 0 0 0 4.4-1.1',
    phone: 'M7 2h10v20H7zM11 18h2',
    help: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 1-1 1.7M12 17h.01',
    sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 1v2M12 21v2M4 4l1.4 1.4M18.6 18.6L20 20M1 12h2M21 12h2M4 20l1.4-1.4M18.6 5.4L20 4',
    cart: 'M3 4h2l2 11h11l2-8H6M9 20h.01M17 20h.01',
    pin: 'M12 22s7-6 7-12a7 7 0 0 0-14 0c0 6 7 12 7 12zM12 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
    steps: 'M8 4c2 0 2 4 2 6s-1 3-2 3-2-1-2-3 0-6 2-6zM16 10c2 0 2 4 2 6s-1 3-2 3-2-1-2-3 0-6 2-6zM6 16h4M14 22h4',
    chart: 'M4 20V4M4 20h16M8 16l4-5 3 3 5-7',
    ruler: 'M3 17L17 3l4 4L7 21zM7 13l2 2M10 10l2 2M13 7l2 2',
    medal: 'M8 3l4 6 4-6M12 21a6 6 0 1 0 0-12 6 6 0 0 0 0 12zM12 13v4'
  };
  function icon(n) { return '<svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="' + (I[n] || I.info) + '"/></svg>'; }
  window.icon = icon;

  // Muscle map placeholder (Content §5: 16 groups, front/back; own drawing comes in C-08).
  var MM = {
    f: { FRONT_DELT: [['e',30,40,7,6],['e',70,40,7,6]], SIDE_DELT: [['e',23,43,3,6],['e',77,43,3,6]], CHEST: [['e',41,49,9,7],['e',59,49,9,7]],
         BICEPS: [['e',25,63,5,9],['e',75,63,5,9]], FOREARM: [['e',21,86,4,11],['e',79,86,4,11]], ABS: [['r',43,58,14,30]],
         OBLIQUES: [['e',37,74,4,10],['e',63,74,4,10]], QUADS: [['e',41,120,7,20],['e',59,120,7,20]] },
    b: { TRAPS: [['e',50,37,14,7]], REAR_DELT: [['e',30,42,6,5],['e',70,42,6,5]], SIDE_DELT: [['e',23,43,3,6],['e',77,43,3,6]],
         LATS: [['e',40,60,8,13],['e',60,60,8,13]], TRICEPS: [['e',25,62,5,9],['e',75,62,5,9]], FOREARM: [['e',21,86,4,11],['e',79,86,4,11]],
         LOWER_BACK: [['r',44,74,12,14]], GLUTES: [['e',42,99,9,9],['e',58,99,9,9]], HAMS: [['e',41,126,7,16],['e',59,126,7,16]], CALVES: [['e',41,159,6,12],['e',59,159,6,12]] }
  };
  var BODY = '<circle class="base" cx="50" cy="14" r="10"/><rect class="base" x="30" y="30" width="40" height="64" rx="12"/>' +
    '<rect class="base" x="17" y="36" width="12" height="62" rx="6"/><rect class="base" x="71" y="36" width="12" height="62" rx="6"/>' +
    '<rect class="base" x="32" y="92" width="17" height="86" rx="8"/><rect class="base" x="51" y="92" width="17" height="86" rx="8"/>';
  function mmSvg(side, lv) {
    var s = '<svg viewBox="0 0 100 186" role="img" aria-label="' + (side === 'f' ? 'Depan' : 'Belakang') + '">' + BODY;
    Object.keys(MM[side]).forEach(function (k) {
      if (!lv[k]) return;
      MM[side][k].forEach(function (g) {
        s += g[0] === 'e' ? '<ellipse class="m' + lv[k] + '" cx="' + g[1] + '" cy="' + g[2] + '" rx="' + g[3] + '" ry="' + g[4] + '"/>'
                          : '<rect class="m' + lv[k] + '" x="' + g[1] + '" y="' + g[2] + '" width="' + g[3] + '" height="' + g[4] + '" rx="3"/>';
      });
    });
    return s + '</svg>';
  }
  function fig() {
    return '<svg viewBox="0 0 200 150" aria-label="Figur gerakan (sementara)"><circle cx="100" cy="28" r="14"/>' +
      '<path d="M100 42v50M100 92l-22 44M100 92l22 44"/><path class="acc arm" d="M100 70l-34 4"/><path class="acc arm" d="M100 70l34 4" style="transform-origin:100px 70px"/></svg>';
  }

  var TABS = [['hari-ini', 'Hari ini', 'home'], ['latihan', 'Latihan', 'dumbbell'], ['+', 'Tambah', 'plus'], ['makan', 'Makan', 'food'], ['saya', 'Saya', 'user']];
  function keep(href) {
    if (!href || /^(#|https?:|mailto:)/.test(href)) return href;
    var q = new URLSearchParams(); ['theme', 'accent', 'big', 'embed'].forEach(function (k) { if (P.get(k)) q.set(k, P.get(k)); });
    var parts = href.split('#'), base = parts[0], hash = parts[1] ? '#' + parts[1] : '';
    var qs = q.toString(); if (!qs) return href;
    return base + (base.indexOf('?') >= 0 ? '&' : '?') + qs + hash;
  }
  window.go = function (href) { location.href = keep(href); };

  function shell() {
    var cur = document.body.dataset.tab;
    var tabHtml = TABS.map(function (t) {
      if (t[0] === '+') return '<button class="tab" data-open="sheet-plus" aria-label="Tambah"><span class="tab-plus row-flex" style="justify-content:center">' + icon('plus') + '</span></button>';
      return '<a class="tab' + (cur === t[0] ? ' on' : '') + '" href="' + t[0] + '.html">' + icon(t[2]) + '<span>' + t[1] + '</span></a>';
    }).join('');
    var nav = document.createElement('nav'); nav.className = 'tabbar'; nav.innerHTML = tabHtml; nav.setAttribute('aria-label', 'Menu utama');
    var rail = document.createElement('nav'); rail.className = 'rail'; rail.innerHTML = '<div class="brand">XNK FIT</div>' + tabHtml; rail.setAttribute('aria-label', 'Menu utama');
    document.body.appendChild(nav); document.body.appendChild(rail);
    document.body.insertAdjacentHTML('beforeend', SHARED_SHEETS);
  }

  var SHARED_SHEETS =
  '<div class="sheet" id="sheet-plus" role="dialog" aria-label="Tambah"><div class="grab"></div>' +
  '<div class="sheet-head"><h2>Tambah</h2><button class="icon-btn" data-close aria-label="Tutup">' + icon('close') + '</button></div>' +
  '<div class="list">' +
  '<a class="li" href="logger.html"><span class="ic">' + icon('play') + '</span><span class="grow"><b>Mulai latihan</b><br><span class="small muted">Full Body B · sekitar 45 menit</span></span>' + icon('chev') + '</a>' +
  '<a class="li" href="pilih-makanan.html"><span class="ic">' + icon('food') + '</span><span class="grow"><b>Catat makan</b><br><span class="small muted">Cari makanan atau tambah kalori cepat</span></span>' + icon('chev') + '</a>' +
  '<button class="li" data-open="sheet-berat"><span class="ic">' + icon('scale') + '</span><span class="grow"><b>Catat berat</b><br><span class="small muted">Berat dan lingkar pinggang</span></span>' + icon('chev') + '</button>' +
  '<button class="li" data-open="sheet-aktivitas"><span class="ic">' + icon('run') + '</span><span class="grow"><b>Catat aktivitas</b><br><span class="small muted">Jalan, sepeda, renang, olahraga lain</span></span>' + icon('chev') + '</button>' +
  '</div></div>' +

  '<div class="sheet" id="sheet-berat" role="dialog" aria-label="Catat berat"><div class="grab"></div>' +
  '<div class="sheet-head"><button class="icon-btn" data-open="sheet-plus" aria-label="Kembali">' + icon('back') + '</button><h2>Catat berat</h2><button class="icon-btn" data-close aria-label="Tutup">' + icon('close') + '</button></div>' +
  '<div class="stack"><label class="field"><span>Berat (kg)</span><div class="stepper big"><button data-step="-0.1" aria-label="Kurangi">−</button><input inputmode="decimal" value="68,4" aria-label="Berat"><button data-step="0.1" aria-label="Tambah">+</button></div></label>' +
  '<label class="field"><span>Lingkar pinggang (cm) · opsional</span><div class="stepper"><button data-step="-0.5">−</button><input inputmode="decimal" value="82" aria-label="Pinggang"><button data-step="0.5">+</button></div></label>' +
  '<p class="small muted">Tren lebih berarti dari satu angka. Timbang di jam yang sama, misalnya Minggu pagi.</p>' +
  '<button class="btn pri block lg" data-close data-toast="Berat tersimpan">Simpan</button></div></div>' +

  '<div class="sheet" id="sheet-aktivitas" role="dialog" aria-label="Catat aktivitas"><div class="grab"></div>' +
  '<div class="sheet-head"><button class="icon-btn" data-open="sheet-plus" aria-label="Kembali">' + icon('back') + '</button><h2>Catat aktivitas</h2><button class="icon-btn" data-close aria-label="Tutup">' + icon('close') + '</button></div>' +
  '<div class="stack"><div class="chips" data-radio>' +
  ['Jalan cepat', 'Lari', 'Sepeda', 'Renang', 'Senam', 'Bulu tangkis', 'Sepak bola', 'Lainnya'].map(function (a, i) { return '<button class="chip' + (i ? '' : ' on') + '">' + a + '</button>'; }).join('') +
  '</div><label class="field"><span>Lama (menit)</span><div class="stepper"><button data-step="-5">−</button><input inputmode="numeric" value="30" aria-label="Menit"><button data-step="5">+</button></div></label>' +
  '<div><div class="field"><span>Seberapa berat?</span></div><div class="seg" data-radio><button>Ringan</button><button class="on">Sedang</button><button>Berat</button></div>' +
  '<p class="xs muted" style="margin-top:6px">Berat = napas terengah, sulit bicara. Menit "berat" dihitung dua kali untuk target mingguan.</p></div>' +
  '<div class="note">' + icon('flame') + '<span>Sekitar <b>150 kkal</b> (MET × berat × jam). Ditampilkan saja, tidak ditambahkan ke target makan.</span></div>' +
  '<button class="btn pri block lg" data-close data-toast="Aktivitas tersimpan · +3 poin">Simpan</button></div></div>' +

  '<div class="scrim" data-close></div>' +
  '<div id="toast" role="status" style="position:fixed;z-index:70;left:50%;bottom:calc(var(--tab-h) + 24px);transform:translateX(-50%);background:var(--fg);color:var(--bg);padding:12px 18px;border-radius:14px;font-weight:700;display:none;max-width:90vw"></div>';

  function openSheet(id) {
    document.querySelectorAll('.sheet.open').forEach(function (s) { s.classList.remove('open'); });
    var s = document.getElementById(id); if (!s) return;
    s.classList.add('open'); document.body.classList.add('sheet-on');
    if (history.state !== 'sheet') history.pushState('sheet', '');
  }
  function closeSheets(fromPop) {
    var any = document.querySelector('.sheet.open');
    document.querySelectorAll('.sheet.open').forEach(function (s) { s.classList.remove('open'); });
    document.body.classList.remove('sheet-on');
    if (any && !fromPop && history.state === 'sheet') history.back();
  }
  window.openSheet = openSheet; window.closeSheets = closeSheets;
  addEventListener('popstate', function () { closeSheets(true); });  // phone back closes the sheet first (N-3)
  addEventListener('keydown', function (e) { if (e.key === 'Escape') closeSheets(); });

  function toast(t) { var el = document.getElementById('toast'); if (!el) return; el.textContent = t; el.style.display = 'block'; clearTimeout(toast.t); toast.t = setTimeout(function () { el.style.display = 'none'; }, 1800); }
  window.toast = toast;

  function setState(key) {
    var g = key.split(':')[0];
    document.querySelectorAll('[data-st]').forEach(function (el) {
      var ks = el.dataset.st.split(' ');
      if (ks.some(function (k) { return k.split(':')[0] === g; })) el.toggleAttribute('hidden', ks.indexOf(key) < 0);
    });
    document.querySelectorAll('[data-sw]').forEach(function (b) { if (b.dataset.sw.split(':')[0] === g) b.classList.toggle('on', b.dataset.sw === key); });
    scrollTo(0, 0);
  }
  window.setState = setState;

  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-open],[data-close],[data-sw],[data-step],[data-toggle],[data-back],[data-toast],[data-go],.chips[data-radio] > *,.seg[data-radio] > *,.chips[data-multi] > *,.check');
    if (!t) return;
    if (t.dataset.step) { var inp = t.parentNode.querySelector('input'); var v = parseFloat(inp.value.replace(',', '.')) || 0; var st = parseFloat(t.dataset.step); var nv = Math.max(0, Math.round((v + st) * 10) / 10); inp.value = String(nv).replace('.', ','); return; }
    if (t.dataset.sw) { setState(t.dataset.sw); }
    if (t.dataset.go) { go(t.dataset.go); return; }
    if (t.dataset.back !== undefined) { if (history.length > 1) history.back(); else go(t.dataset.back || 'hari-ini.html'); return; }
    if (t.parentNode.matches && t.parentNode.matches('[data-radio]')) { Array.prototype.forEach.call(t.parentNode.children, function (c) { c.classList.toggle('on', c === t); }); }
    if (t.parentNode.matches && t.parentNode.matches('[data-multi]')) t.classList.toggle('on');
    if (t.classList.contains('check')) { t.classList.toggle('on'); var tr = t.closest('tr,.li,[data-row]'); if (tr) tr.classList.toggle('done', t.classList.contains('on')); if (t.classList.contains('on') && t.dataset.rest) startRest(); }
    if (t.dataset.toggle) { var el = document.getElementById(t.dataset.toggle); if (el) el.hidden = !el.hidden; }
    if (t.dataset.close !== undefined) closeSheets();
    if (t.dataset.open) openSheet(t.dataset.open);
    if (t.dataset.toast) toast(t.dataset.toast);
  });

  // Rest timer (Logger): stores the end time, as the app will (Design §4).
  var restEnd = 0, restT;
  function startRest(sec) {
    var bar = document.getElementById('rest'); if (!bar) return;
    restEnd = Date.now() + (sec || 90) * 1000; bar.hidden = false; tick();
    clearInterval(restT); restT = setInterval(tick, 250);
  }
  function tick() {
    var left = Math.max(0, Math.round((restEnd - Date.now()) / 1000));
    document.querySelectorAll('[data-rest-left]').forEach(function (el) { el.textContent = Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0'); });
    if (!left) { clearInterval(restT); var b = document.getElementById('rest'); if (b) b.hidden = true; toast('Istirahat selesai. Siap set berikutnya?'); }
  }
  window.startRest = startRest;
  window.restAdd = function (s) { restEnd += s * 1000; tick(); };
  window.restSkip = function () { restEnd = Date.now(); tick(); };

  function mockBar() {
    var b = document.createElement('div'); b.className = 'mock-bar'; b.setAttribute('aria-label', 'Kontrol mockup (bukan bagian aplikasi)');
    b.innerHTML = '<a href="index.html" title="Daftar layar">Daftar</a><button data-mb="theme">' + (theme === 'dark' ? 'Terang' : 'Gelap') + '</button>' +
      '<select data-mb="accent" aria-label="Aksen"><option value="">Hitam</option><option>biru</option><option>hijau</option><option>toska</option><option>ungu</option><option>pink</option><option>oranye</option></select>' +
      '<button data-mb="big">Teks besar</button><button class="mb-toggle" data-mb="min" title="Kecilkan">✕</button>';
    b.querySelector('select').value = P.get('accent') || '';
    b.addEventListener('click', function (e) {
      var k = e.target.dataset.mb; if (!k) return;
      if (k === 'min') { b.classList.toggle('min'); e.target.textContent = b.classList.contains('min') ? 'Mockup' : '✕'; return; }
      if (k === 'theme') P.set('theme', theme === 'dark' ? 'light' : 'dark');
      if (k === 'big') { if (P.get('big')) P.delete('big'); else P.set('big', '1'); }
      location.search = P.toString();
    });
    b.querySelector('select').addEventListener('change', function (e) { if (e.target.value) P.set('accent', e.target.value); else P.delete('accent'); location.search = P.toString(); });
    document.body.appendChild(b);
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!document.body.classList.contains('full')) shell();
    else document.body.insertAdjacentHTML('beforeend', SHARED_SHEETS);
    document.querySelectorAll('[data-i]').forEach(function (el) { el.outerHTML = icon(el.dataset.i); });
    document.querySelectorAll('[data-mm]').forEach(function (el) {
      var lv = {}; el.dataset.mm.split(',').forEach(function (p) { var kv = p.split(':'); if (kv[0]) lv[kv[0]] = +kv[1] || 1; });
      el.classList.add('mm'); el.innerHTML = mmSvg('f', lv) + mmSvg('b', lv);
    });
    document.querySelectorAll('[data-fig]').forEach(function (el) { el.classList.add('fig'); el.innerHTML = fig(); });
    document.querySelectorAll('a[href]').forEach(function (a) { a.setAttribute('href', keep(a.getAttribute('href'))); });
    var st = P.get('state'); if (st) setState(st);
    else document.querySelectorAll('[data-sw].on').forEach(function (b) { setState(b.dataset.sw); });
    if (!P.get('embed')) mockBar();
  });
})();
