# Reference vs app comparison

Judges how close the app looks to the XNK portal (`docs/reference/xnk/`), screen by screen.

- `capture-reference.js`: photographs the demo (`demo/index.html?klien`) into `docs/reference/xnk/screens/` (hp 390×844, tablet 800×1280, light and dark). Static; redo only when the reference changes: `node tools/compare/capture-reference.js && python3 tools/compare/shrink-reference.py` (Playwright + Chromium; Pillow for the shrink).
- `map.json`: app screen name (ScreenshotTest) → reference screen, or `null` with a reason.
- `build-compare.js <screenshots dir> [out dir]`: for each pair writes `<screen>_<hp|tablet>_<terang|gelap>.png` (left = ACUAN, right = APP, app at font scale 1.0), `index.html` and `summary.md` (screens with and without a counterpart, plus rough hints: dark top band, mean luminance, contrast, palette overlap). The numbers are hints, never pass/fail.

Local: `NODE_PATH=$(npm root -g) node tools/compare/build-compare.js screenshots compare`.
CI: workflow Screenshots, step "Build comparison", after the emulator run; artifact `compare` (14 days). The step is `continue-on-error`, so a missing pair is only logged in `summary.md`.
To add a screen: add it to `SCREENS` in the capture script, rerun it, and add the row to `map.json`.
