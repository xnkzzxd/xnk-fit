# Mockups — XNK Fit (D-05)

| | |
| --- | --- |
| Status | Draft, 2026-11-16 (task D-05). Waiting for owner approval (D-06). |
| Reads | [PRD.md](../PRD.md) §7–§9, [Design.md](../Design.md) §5 and §11, [Rules.md](../Rules.md), [Content.md](../Content.md) |
| Format | Static HTML, no server, no network. Open [index.html](index.html) in a browser (double-click works). |

## How to look at them

- **[index.html](index.html)**: pick a screen on the left; it shows phone (390 dp) and tablet (900 dp), light and dark, side by side. Pick a state ("Keadaan") and an accent above the frames.
- **Buka** opens the screen full size. Resize the window: below 600 px it is the phone layout (bottom bar), from 600 px the tablet layout (side rail, two columns). Check 360 and 430 px for the phone range.
- The **yellow bar** (top right) and the **dashed "Keadaan" box** are mockup controls only, not part of the app: theme, accent (7), big text, and the states of each screen.
- Everything is clickable: tabs, ＋ sheet (Mulai latihan, Catat makan, Catat berat, Catat aktivitas), sheets, set ticks, rest timer, steppers, switches. The browser back button closes an open sheet first (N-3).
- URL parameters for links in review notes: `?theme=dark`, `&accent=hijau`, `&big=1`, `&state=m:nonum` (example: `makan.html?theme=dark&state=m:nonum`).

## Rules checked (Design §11)

| Rule | How |
| --- | --- |
| No red for calories or weight (PR-4, R_TONE) | Red (`--danger`) appears only in one error: "cadangan dari versi lebih baru" (Pengaturan → Data). Over target shows "+120 di atas target" in the accent with a kind line. Weight can be hidden (Saya). |
| 44 dp touch targets (D-2) | Automatic check over 144 page/state/width combinations and every sheet: no control under 44 × 44 px. |
| No sideways scroll (Design §11) | Same check at 360, 430 and 900 px: none, except the allowed swipes (week planner days, Jelajahi shelf, chip rows). |
| Indonesian UI, neutral words (no "kamu" / "Kak") | All screen text. Exercise names and cues in English (owner decision, L-10). |
| Light and dark, 7 accents, big text (D-1) | Tokens in `mock.css`; no colour literals in screens except the accent swatches in Tampilan. |
| Phone 360–430, tablet ≥ 600 with rail (N-4) | One layout per screen, switches at 600 px. |
| XNK look (owner review 1) | Inter font, 12 px rounded buttons and chips (no pills), choice rows and lone buttons fill the width, text wraps inside its box (checked: no text wider than its button, chip, row or card). Inter loads from Google Fonts; offline the page falls back to the system font. |
| Phase X look (owner review 2) | Tokens and components copied from the XNK portal (parts/theme 01-awal, 05-portal-x): black band with contour lines, first card hanging over it, black hero card with rings, bar-marked card titles, to-do rows, tiles, floating black tab dock, black segmented bars and sheet heads. Text cut to one short line per item. Two buttons side by side are always the same width (checked). |

## Screen list for owner approval (D-06)

Tick each row, or write the change wanted in "Catatan". A row is approved when it has ✅ and no open note.

| # | Screen (file) | What it shows | PRD | States to look at | OK | Catatan |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Onboarding ([onboarding.html](onboarding.html)) | Welcome with the promise, goal, sex / birth year / height / weight, interest, nickname, one health question, permissions; restore link | O-1…O-6 | ob:1…ob:7, ob:u13 (under 13 stops) | [ ] | |
| 2 | Formulir kesehatan ([screening.html](screening.html)) | Full form; results: safe, lighter program + doctor text, eating-disorder history → no-numbers mode, under 18 | S-1, S-2, S-6, O-2 | sc:form, sc:ok, sc:risk, sc:ed, sc:teen | [ ] | |
| 3 | Hari ini ([hari-ini.html](hari-ini.html)) | Next workout, calorie ring, week strip, level and streak (PRD order; tablet: workout + ring left, week + level right); rest day; non-lifter activity target 150 min + steps; empty states; "Lanjutkan latihan?"; recalc prompt; welcome card | H-1…H-3, M-2, N-1, N-2, P-8 | h:lift, h:rest, h:act, h:empty + two sheets | [ ] | |
| 4 | ＋ sheet (all tab screens) | Mulai latihan, Catat makan, Catat berat, Catat aktivitas (MET kcal shown, not added) | N-2, L-14 | Tap ＋ | [ ] | |
| 5 | Latihan ([latihan.html](latihan.html)) | Training profile on first open, auto program days, review proposals, own routines, empty workout, Jelajahi shelf, weekly muscle map, activity and steps, equipment sheet, exercise detail with figure, cues and "Kenapa gerakan ini"; home bodyweight with the missing-pull message | L-1…L-5, L-9…L-11, L-14, L-15, O-4 | l:prog, l:setup, l:home + sheets | [ ] | |
| 6 | Logger ([logger.html](logger.html)) | Mode choice at first workout; Dipandu (one set, big steppers, suggestion, Rapi/Ragu); Bebas (table, superset, drop set, band level); rest timer; swap; set kinds; finish | L-2, L-6…L-8, L-12 | lg:mode, lg:dipandu, lg:bebas + sheets | [ ] | |
| 7 | Ringkasan latihan ([ringkasan.html](ringkasan.html)) | Duration, volume, sets, records, badge, session muscle map, feeling 1–5, note, share | L-13 | — | [ ] | |
| 8 | Makan ([makan.html](makan.html)) | Ring and macros, activity kcal shown not added, recalc prompt, 3 options per meal with santan note, food log per slot, copy yesterday, quick kcal; over target (kind); no-numbers mode; food preferences on first open | M-1…M-5, M-9, S-6, O-4 | m:normal, m:over, m:nonum, m:setup | [ ] | |
| 9 | Pilih makanan ([pilih-makanan.html](pilih-makanan.html)) | Search, recent / favourites / TKPI / packaged / warung / own, household measures first with ±, estimate flags, portion sheet, own food | M-6, M-7 | f:recent, f:search, f:own + portion sheet | [ ] | |
| 10 | Rencana minggu ([planner.html](planner.html)) | Phone: one day per screen with swipe; tablet: 7-column table; swap, lock, masak sekali, makan di luar; kind limit note | M-3, M-4, M-10 | phone and tablet | [ ] | |
| 11 | Daftar belanja ([belanja.html](belanja.html)) | Grouped checklist from the week plan | M-8 | — | [ ] | |
| 12 | Saya ([saya.html](saya.html)) | Athlete card, level, weight trend (hideable), waist, photos on phone only, journey, streak rule, badges | P-1…P-5 | s:show, s:hide | [ ] | |
| 13 | Kartu saya ([kartu.html](kartu.html)) | Card gallery, simple editor (card, style, share), app name + QR on, no-kg and face-mosaic switches | P-2, P-6 | k:gal, k:edit | [ ] | |
| 14 | Pengaturan ([pengaturan.html](pengaturan.html)) | Profil, Latihan, Makan, Pengingat, Tampilan, Data, Bantuan in that order; backup, restore warning with typed word, other-phone ask, newer-version refusal, export / import, delete all | P-7, B-1…B-7, R-1, R-2, D-1 | p:home…p:data + sheets | [ ] | |
| 15 | Bantuan ([bantuan.html](bantuan.html)) | How targets are calculated (with sources and ⚠), short guide, glossary, welcome cards reset, privacy, feedback link | P-8, PV-1…PV-3 | b:home, b:target, b:istilah, b:privasi | [ ] | |

## Questions for the owner (decide in D-06)

1. **English exercise cues** (PRD §14 item 3): shown in Latihan → Detail gerakan and the Dipandu hint. Keep English, or Indonesian cues with English names?
2. **Figure drawing** (Design §16 item 3): the figure and muscle map here are placeholders. Static SVG per pose (default) is assumed.
3. **Logger mode**: chosen at the first workout (L-6). Is Dipandu the right preselected choice?
4. **Screening questions**: the wording in the mockup is an example; the final list and its source go into Rules (S-07).

## Not in these mockups

- Muscle maps use the XNK app's own figure (`figure-data.js`, shapes from react-muscle-highlighter, MIT, credits in `FIGURE-CREDITS.md`). The animated exercise figure is still a stick placeholder (C-08). Food numbers (C-03…C-07) are examples.
- Android system dialogs (permissions, share sheet, file picker) are shown as a short toast.
- Notifications are shown only as sample text (Pengaturan → Pengingat, rest timer toast).

## Files

`mock.css` (tokens and components), `figure-data.js` + `FIGURE-CREDITS.md` (muscle map shapes), `mock.js` (shell: tabs / rail, ＋ sheet, sheets, states, steppers, rest timer, icons, muscle map placeholder), one HTML file per screen, `index.html` (review page). Mockup code is thrown away when B-01 starts the Compose app.
