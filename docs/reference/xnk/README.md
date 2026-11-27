# XNK visual reference (owner's live apps)

The owner (2026-11-27) chose the **look of the live XNK apps** (xnk.my.id, xnk.web.id) as the visual target for XNK Fit. Copied unedited from the XNK repo `xnkzzxd/xnk` (commit `8001bd3`, 2026-11-18) so cloud sessions need no access to that repo. These files are references only: they do not ship in the APK.

- `demo/index.html`: the XNK demo as one runnable file (panel and client portal, sample data, no network). Open `index.html?klien` for the phone client portal (the look to match). Screenshot it with Playwright (see `capture-demo.js`, 390×844, mobile, close the badge sheet first).
- `screens/`: three captures of the live client portal: Hari ini, Latihan (routine cards), logger (table `SET | SEBELUMNYA | KG | REP | ✓`).
- `theme/01-awal.part`, `theme/05-portal-x.part`: the CSS tokens and components of the portal (colours light and dark, `--band`, `--accent`, radii, type scale in rem, black hero card with line art, bottom bar pill with the centre ＋ button, KPI tiles, buttons).
- `portal2-Design.md`, `ui-Design.md`: XNK design notes for Phase X (client portal) and Phase R (big text, black bands, left-right data).

What the look is, in words: black top band and black hero card with concentric line art; large bold numbers; white rounded cards; section headings with a short vertical bar; black primary buttons and a white "+" button in a black pill bottom bar; big grey KG and REP cells with square check buttons in the logger; one accent colour from seven choices through tokens, never hard-coded; light and dark themes; text sizes that scale (no fixed heights on text).
