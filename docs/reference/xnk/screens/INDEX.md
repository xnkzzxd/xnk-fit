# Reference screens (XNK demo, client portal)

Static captures of `../demo/index.html?klien`, made by `tools/compare/capture-reference.js`. Name: `<screen>_<hp|tablet>_<terang|gelap>.png`.
hp = 390×844, tablet = 800×1280 (colour palette reduced to 64 so the folder stays under 2 MB).

| Screen | Demo view | hp | tablet |
| --- | --- | --- | --- |
| `hari-ini` | `public-dashboard` | yes | yes |
| `latihan` | `public-program` (routine list) | yes | yes |
| `logger` | running workout (`clientQuick('workout')`) | yes | yes |
| `makan` | `public-food` | yes | yes |
| `planner` | `public-plan` | yes | yes |
| `progres` | `public-progress` | yes | yes |
| `saya` | `public-more` | yes | yes |
| `kartu` | `public-share` | yes | yes |
| `pengaturan` | settings sheet (`openSettings()`) | yes | yes |
| `paket` | `public-pack` | yes | yes |
| `info` | `public-info` | yes | yes |

Older captures of the live app: `portal-hari-ini.png`, `portal-latihan.png`, `portal-logger.png`.
No help/privacy/data/onboarding screen exists in the demo (see `tools/compare/map.json`).

Redo (only when the reference changes):

    npm i playwright   # or any Playwright with Chromium
    node tools/compare/capture-reference.js
    python3 tools/compare/shrink-reference.py
