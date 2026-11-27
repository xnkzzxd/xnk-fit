# XNK Fit visual system (Phase T)

The look of XNK Fit is the look of the owner's live XNK apps (xnk.my.id, xnk.web.id), chosen by the owner on 2026-11-27.
The reference files are in `docs/reference/xnk/` (README there). Code: `app/src/main/java/app/xnkfit/ui/design/`.

## 1. Tokens (`ui/design/Tokens.kt`)

Values are copied from `docs/reference/xnk/theme/01-awal.part` (`:root` and the dark block) and `05-portal-x.part` (accents).
`Tokens.kt` is the only Kotlin file with colour literals. Screens read `Xnk.colors.*`, `XnkType.*`, `XnkRadius.*`, `XnkSpace.*`.

### Colours

| Token | CSS | Light | Dark | Used for |
| --- | --- | --- | --- | --- |
| `bg` | `--bg` | `#FAFAF9` | `#0A0A0A` | page |
| `surface` | `--surface` | `#FFFFFF` | `#141414` | cards |
| `surface2` | `--surface-2` | `#F5F5F4` | `#1A1A1A` | flat cards |
| `surface3` | `--surface-3` | `#ECEBE9` | `#242424` | grey buttons, KG/REP cells, bar tracks |
| `border` / `borderStrong` | `--border` / `--border-strong` | `#E7E5E4` / `#CFCFCB` | `#272727` / `#3A3A3A` | lines, outline buttons |
| `fg` / `fg2` / `fgMuted` | `--fg` / `--fg-2` / `--fg-muted` | `#0B0B0B` / `#3B3B39` / `#676764` | `#F4F4F2` / `#CFCFCC` / `#9B9B97` | text |
| `ink` / `onInk` / `onInkMuted` | `--ink` … | `#0B0B0B` / `#FFFFFF` / `#A7A7A4` | `#F4F4F2` / `#0B0B0B` / `#5C5C59` | bottom bar, ink fills |
| `band` / `bandCard` | `--band` / `--band-card` | `#0B0B0B` / `#1A1A19` | `#161616` / `#2A2A2A` | top band, hero card |
| `onBand` / `onBandMuted` | `--on-band` … | `#FFFFFF` / `#A7A7A4` | same | text on band and hero |
| `danger` / `dangerSoft` | `--danger` … | `#C4261B` / `#FDECEA` | `#FF6B5E` / `#2A1412` | errors only (PR-4) |
| `accent` / `onAccent` / `accentOnInk` | `--accent` … | see below | see below | primary button, ring, bars, done checks |

Accents (`Accent` enum, 7 choices, default `INK`): INK = `--ink` (black in light, `#F4F4F2` in dark); BIRU `#1D4ED8`/`#60A5FA`,
HIJAU `#15803D`/`#4ADE80`, TOSKA `#0F766E`/`#2DD4BF`, UNGU `#6D28D9`/`#A78BFA`, PINK `#BE185D`/`#F472B6`,
ORANYE `#C2410C`/`#FB923C` (light accent / accent on ink; in dark the accent is the second value with black text on it).
No setting chooses the accent yet; the app uses INK (as the XNK portal without `data-accent`).

Note, faithful to the reference: in dark mode `--ink` is light, so the bottom bar pill is light grey with dark icons, while the band
and hero stay dark.

### Radius, spacing, type

| Token | Value | CSS |
| --- | --- | --- |
| `XnkRadius.xs / sm / md / lg / xl` | 8 / 12 / 16 / 22 / 28 dp | `--r-*` |
| `XnkRadius.bar / fab` | 24 / 18 dp | `.tabbar`, `.tab-fab` |
| `XnkSpace.gap / page / pageWide` | 16 / 16 / 28 dp | `--gap`, `--page-pad` |
| `XnkSpace.card / inkCard / hang` | 18 / 22 / 52 dp | `.card`, `.card-ink`, `.px-hang` |

Type: rem × 16 = sp, so all text follows the system font size. Font Inter (OFL, `assets/licenses/Inter-OFL.txt`), three static
weights 400/700/800 cut from Inter 4.1 to Latin + punctuation (52 KB each, 156 KB total); other glyphs fall back to the system font.

| Style | sp | Weight | Tracking | CSS |
| --- | --- | --- | --- | --- |
| `xs2`, `xs`, `sm`, `md`, `lg` | 11, 12, 13, 14, 16 | 400 | 0 | `--fs-2xs` … `--fs-lg` |
| `eyebrow` | 11 | 700 | 0.08 em, upper case | `.eyebrow` |
| `section` | 16 | 800 | −0.01 em | `.section-title`, `.px-sec h3` |
| `rowTitle`, `button` | 14 | 700 | 0 | `.todo-title`, `.btn` |
| `tab` | 10 | 700 | 0 | `.tab` |
| `heroTitle`, `kpi` | 22 | 800 | −0.02 em | `.px-hero-t`, `.kpi-value` |
| `pageTitle` | 28 | 800 | −0.03 em | `.page-title` |
| `bigNumber` | 36 | 800 | −0.03 em | `.nx-count` |

Material 3 stays under the screens not yet redrawn; `XnkColors.toMaterial()` maps every Material role onto the tokens
(primary = accent, surface = page, outline = border), so old screens already lose the purple.

## 2. Components (`ui/design/`)

All have previews in `Previews.kt` (light, dark, font 1.3).

| Component | XNK source | Use |
| --- | --- | --- |
| `TopBand(brand, title?, subtitle?)` | `.topbar` + `.px-band`, `.gfx-kontur` | the band at the top of a page, part of its scroll as on the portal: brand (or page name), date, contour lines drawn on Canvas. Hari ini uses it; the other tabs get it in T-02…T-05 |
| `BandHang(bandBelow)` | `.px-band` + `.px-hang` | first list item: the band continues 40 dp behind the hero so it hangs over the edge |
| `HeroCard(eyebrow)` | `.card-ink.gfx-geo` | band-card fill, 1 dp light border, radius 28, rings + accent dot on Canvas; buttons inside use `onInk = true` |
| `XnkCard`, `CardFlat` | `.card`, `.card-flat` | white card radius 22 padding 18; grey flat card radius 16 |
| `SectionHeader(title, trailing?, action?)` | `.px-sec` with bar | 4 dp bar + 16 sp extra-bold title + muted count or a text action |
| `PrimaryButton`, `SecondaryButton`, `TonalButton` | `.btn-primary`, `.btn-outline`, `.btn-secondary` | 48 dp min, radius 12; on ink: white / outline / translucent |
| `RowAction` | `.todo-act` | small action at the end of a `CheckRow`, 48 dp touch area |
| `CheckRow(title, done, stateText)` | `.todo-row` | round check (accent when done), title, sub, action; `stateText` read by TalkBack |
| `MeterBar` | `.px-bar` | 6 dp bar, accent fill |
| `ProgressRing` | `.ring-wrap` | ring with centre slot; size grows with font scale up to 1.5× on Hari ini |
| `StatTile` | `.kpi` | big value, muted label |
| `Chip` | `.chip` | pill, accent when selected |
| `KgRepCell`, `CheckButton` | logger table | big grey KG/REP cell, square set-done button (for T-02) |
| `LineChart` | — | accent polyline over light grid; needs a text description |
| `DayDot` | `.week-x` | week strip |
| `PillBottomBar`, `PillRail` | `.tabbar`, `.tab`, `.tab-fab` | floating pill (max 420 dp, 14 dp margin, 68 dp tall), four labelled tabs + 54 dp ＋; the rail for tablets (B-17) in band colour |

Test contract kept: each tab is one clickable node carrying its visible label (smoke `clickNav`, `ScreenshotTest.tab`,
`ScreenWalkTest.openTab`); ＋ carries the `nav_add` label as its content description.

## 3. Rules

1. No colour literal outside `Tokens.kt` (and the two window themes in `res/values*/themes.xml`). Screens use `Xnk.colors`.
2. No fixed height on anything holding text: `heightIn(min = …)`, never `height(…)`. Only icons, dots and the ring have fixed sizes.
3. Every tap target ≥ 48 dp (`MinTouch`); `ScreenWalkTest` checks it at font 1.0, 1.3 and 2.0.
4. Text in `strings.xml`; tone neutral (R_TONE); red only for errors.
5. No bitmaps for decoration: line art is drawn on Canvas.
6. A new screen group is done only when compared side by side with the reference (below) and approved by the owner.

## 4. Taking and comparing screenshots

1. Push: the `Screenshots` workflow builds the app and runs `ScreenshotTest` on an API 34 emulator in 8 variants
   (`hp|tablet` × `terang|gelap` × `1.0|1.3`), artifact `screenshots`.
2. Download the artifact (GitHub MCP `actions_list` → `list_workflow_run_artifacts`, `actions_get` → `download_workflow_run_artifact`),
   unzip into a new folder outside the repo, open `hari-ini_hp_terang_1.0.png`.
3. Open `docs/reference/xnk/screens/portal-hari-ini.png` (390×844 CSS px) next to it and list what matches and what does not:
   band, hero size and shape, ring art, section bars, card radius, bottom pill, type weight. For more reference screens, run
   `docs/reference/xnk/demo/index.html?klien` in Playwright at 390×844 (`capture-demo.js`).
4. Check dark and font 1.3 variants for clipped text.

The T-01 comparison is in docs/TODO.md (T-01) and the changes log.

## Gerbang kesetiaan (T-07)

Acuan tampilan = app XNK yang hidup. Compose tidak akan pernah sama piksel; yang dijanjikan adalah kemiripan yang **terukur dan jujur**.

1. **Spesifikasi acuan** (`docs/reference/xnk/spec/`): kotak, gaya terhitung, dan token tiap komponen penting, diekstrak dari demo oleh `tools/compare/extract-spec.js` (statis).
2. **Ukuran sisi app** (`app/src/androidTest/.../fidelity/FidelityTest`): komponen diberi `testTag("fid:<komponen>")` di `ui/design`; tes membaca kotak (dp, dinormalkan ke lebar 390), warna dari tangkapan, radius dari tepi sudut, dan ukuran/bobot teks. Hanya berjalan di workflow Screenshots.
3. **Laporan** `compare/fidelity/report.md|json` (`tools/compare/build-fidelity.js`): per komponen nilai acuan | nilai app | selisih | status (sama / berbeda / belum ada padanan). Toleransi: kotak ±2, radius ±2, teks ±1 sp, warna ΔE76 ≤ 5 (isian) / ≤ 10 (warna teks); y hanya info.
4. **Peta selisih struktur** `compare/<layar>_<hp|tablet>_<terang|gelap>_diff.png` + metrik di `summary.md` (kedua gambar dikaburkan 8 px, lebar 390): angka petunjuk, bukan lulus/gagal.

Tahap ini hanya melapor (tidak menggagalkan build). Keputusan akhir tetap melihat gambar berdampingan. Batasan: baru Hari ini (hp, font 1.0) dibandingkan per komponen; tablet menunggu T-06; komponen di luar layar tidak terukur; sampel warna satu piksel; bobot font 500/600/900 acuan tidak ada di app.
