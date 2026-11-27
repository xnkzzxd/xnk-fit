# Reference vs app comparison

Judges how close the app looks to the XNK portal (`docs/reference/xnk/`), screen by screen.

- `capture-reference.js`: photographs the demo (`demo/index.html?klien`) into `docs/reference/xnk/screens/` (hp 390×844, tablet 800×1280, light and dark). Static; redo only when the reference changes: `node tools/compare/capture-reference.js && python3 tools/compare/shrink-reference.py` (Playwright + Chromium; Pillow for the shrink).
- `map.json`: app screen name (ScreenshotTest) → reference screen, or `null` with a reason.
- `build-compare.js <screenshots dir> [out dir]`: for each pair writes `<screen>_<hp|tablet>_<terang|gelap>.png` (left = ACUAN, right = APP, app at font scale 1.0), `index.html` and `summary.md` (screens with and without a counterpart, plus rough hints: dark top band, mean luminance, contrast, palette overlap). The numbers are hints, never pass/fail.

Local: `NODE_PATH=$(npm root -g) node tools/compare/build-compare.js screenshots compare`.
CI: workflow Screenshots, step "Build comparison", after the emulator run; artifact `compare` (14 days). The step is `continue-on-error`, so a missing pair is only logged in `summary.md`.
To add a screen: add it to `SCREENS` in the capture script, rerun it, and add the row to `map.json`.

## Fidelity gate (T-07)

- `extract-spec.js`: reference spec (box + computed style per component, `:root` tokens) → `docs/reference/xnk/spec/` (see its `INDEX.md`). Static. `NODE_PATH=$(npm root -g) node tools/compare/extract-spec.js`.
- `FidelityTest` (androidTest, only run by `screenshots.yml` after `ScreenshotTest` of each variant) writes `fidelity/fid_<screen>_<variant>.json` from `fid:*` test tags.
- `build-fidelity.js <screenshots dir> [out]` → `report.md` / `report.json` (component | reference | app | diff | status; tolerances in the file header). Local: `node tools/compare/build-fidelity.js screenshots compare/fidelity` with the JSONs pulled from a device run (`adb exec-out run-as app.xnkfit cat files/fidelity/...`).
- `build-compare.js` now also writes `<screen>_<dev>_<theme>_diff.png` (blurred difference heat map) and a metrics table in `summary.md`.
- CI: artifact `compare` (14 days) holds all of it (`compare/fidelity/report.md`). Report only; a missing counterpart never fails the run.
- Reading: ✓/✗ per field; "belum ada padanan" = not visible in the app screen or absent in the reference; dy (vertical offset) is information only.
