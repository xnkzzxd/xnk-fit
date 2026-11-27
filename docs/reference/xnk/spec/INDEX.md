# Spesifikasi terukur acuan XNK

Diekstrak dari demo hidup (`../demo/index.html?klien`, Chromium via Playwright) oleh `tools/compare/extract-spec.js`. Statis: ulangi hanya bila acuan berubah:
`NODE_PATH=$(npm root -g) node tools/compare/extract-spec.js`

Berkas: `<layar>_<hp|tablet>_<terang|gelap>.json` (hp 390×844, tablet 1024×1366, DSF 1; kotak dalam px CSS; y = posisi dokumen) dan `tokens.json` (variabel `:root` terang/gelap, font). Layar: hari-ini, latihan, logger, makan, planner, saya, pengaturan. Tiap komponen memuat hingga 3 kemunculan pertama yang terlihat. Catatan: DOM demo menyimpan tampilan lain di belakang lapisan logger/pengaturan, sehingga komponen tak terkait dapat ikut tercatat di layar tersebut; gunakan hanya komponen yang memang ada di layarnya.

| komponen | pemilih CSS |
| --- | --- |
| pita_atas / judul_pita / band | `header.topbar` / `h1.topbar-title` / `.px-band` |
| kartu_hero_hitam, hero_eyebrow, hero_judul | `section.card-ink`, `.ih-eyebrow`, `.px-hero-t, .nx-when` |
| judul_bagian, judul_bagian_garis | `h3.section-title`, `.section-title::before` (garis vertikal) |
| px_sec | `.px-sec` |
| baris_tugas, lingkaran_centang, tugas_judul, tugas_sub | `button.todo-row`, `.todo-check`, `.todo-title`, `.todo-sub` |
| tombol_utama / sekunder / garis / ghost | `.btn-primary` / `.btn-secondary` / `.btn-outline` / `.btn-ghost` |
| bilah_bawah, tab, tombol_plus_tengah | `nav.tabbar`, `button.tab`, `button.tab-fab` |
| stat_tile | `.px-tile, .mtile, .wl-st, .ih-stat` |
| sel_input (KG/REP), tombol_centang, nomor_set | `input.wl-in`, `button.wl-ck`, `button.wl-setno` |
| chip, kartu_putih, daftar_item, ikon_chip, ring | `.pill`, `div.card`, `button.list-item`, `.icon-chip`, `.ring-wrap` |

Font: lihat `docs/Decisions.md` (Inter 400–900, dimuat dari Google Fonts, tidak disertakan di repo).
