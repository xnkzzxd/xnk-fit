# Manual tests (owner, on a device)

These need a real phone or emulator, so no automatic test fakes them (B-18, Design §13). Run them on the **Redmi 10** (MIUI/HyperOS, the slow case) and on an **emulator, API 26, 2 GB RAM**. Mark each with date, device and result. A failure goes to TODO as a bug, never as a loosened test.

What the JVM tests already cover (so these steps do not repeat it): every tap is on disk (`LoggerRepositoryTest`), a transaction cut off before commit leaves the old data (`KillMidTransactionTest`: workout sets, restore, content update, meal log), restore and content write failures roll back (`BackupArchiveTest`, `BundledContentLoaderTest`), every main screen draws at 360/430/600/840 dp, light and dark, font 1.0/1.3/2.0 with every clickable element at 48 dp or more (`ScreenWalkTest`, `LoggerScreenTest`). What they cannot see is below.

## Kill and crash (NF-2)

| ID | Steps | Expected |
| --- | --- | --- |
| K-1 | Start a workout (Dipandu), tick 3 sets with weights. Swipe the app away from Recents (or `adb shell am force-stop app.xnkfit`). Open the app. | Opens on Latihan with "Lanjutkan"; the 3 sets, weights and times are there; nothing lost. |
| K-2 | Same as K-1 but kill the process during the rest timer (`adb shell am kill app.xnkfit` after backgrounding). | After opening, the rest bar shows the remaining time from the stored end time, or is gone if the time has passed. |
| K-3 | Start a workout, leave the phone untouched for 2 days (or set the clock forward 2 days), open the app. | The old workout is closed at its last set; no "Lanjutkan" for it; its sets are in history. |
| K-4 | Start a restore from a backup file. When the progress shows, force-stop the app. Open the app. | Old data is complete and unchanged (no half restore). Run restore again: it works. |
| K-5 | Turn on airplane mode in the middle of a content update check (Pengaturan, "Periksa pembaruan") and force-stop. Open the app. | Foods and exercises are all present; content version is the old one. |
| K-6 | Add a meal in Makan, force-stop right after pressing save. Open the app. | The meal is there (or never shown as saved). No duplicate. |
| K-7 | Fill phone storage until about 50 MB is free (`adb` large file), tick 5 sets. | No crash; either the sets are saved or a clear message shows; after freeing space, no corrupt data. |
| K-8 | Turn the phone off by holding power in the middle of a set tick (5 tries). Boot and open. | Workout opens with every tick that showed as done. |

## Alarm, notification, Doze, camera (not testable on the JVM)

| ID | Steps | Expected |
| --- | --- | --- |
| A-1 | Rest timer 90 s, press power to lock the screen. | Alarm and notification at about 90 s (within the exact-alarm tolerance), sound or vibration per channel. |
| A-2 | Same on Android 12+ with "Alarms & reminders" permission off. | App shows the in-app fallback; no crash; asks once. |
| A-3 | Same while the phone has been idle 30 min (Doze: `adb shell dumpsys deviceidle force-idle`). | Rest alarm still fires; reminders may be late but arrive. |
| N-1 | Turn on a reminder switch on Android 13+. | The notification permission is asked at that moment, not before. Deny: the switch goes back off with a note. |
| N-2 | Turn on 4 reminder kinds, wait through a day. | At most 3 notifications that day; text rotates; none when the user already logged. |
| N-3 | MIUI: battery saver "Strict", app in background overnight. | Report whether reminders arrive; note the settings that fix it (for the Bantuan text). |
| C-1 | Saya, "Ambil foto", allow camera. | Camera opens, picture saved on the phone only; no gallery copy; deleting the photo removes the file. |
| C-2 | Same, deny camera twice. | App shows a clear message; no crash. |

## Screens (pixels, which the semantic tests cannot judge)

| ID | Steps | Expected |
| --- | --- | --- |
| S-1 | Phone 360 and 430 dp, light and dark, font 1.3 and 2.0: open Hari ini, Latihan, logger, Makan, Saya, Pengaturan, Data. | No clipped or overlapping text; every button reachable by scrolling; dark mode text readable. |
| S-2 | Tablet or emulator 600 and 840 dp, portrait and landscape. | Side rail, two columns where designed; no stretched forms; rotation keeps the open workout. |
| S-3 | Font 2.0 on a 600 dp tablet. | Falls back to one column, no clipped text. |
| S-4 | Back gesture on every screen and overlay, phone and tablet. | Goes back one level; from a tab root leaves the app. |
| S-5 | TalkBack on: Hari ini, a logger set, Makan, Saya. | Every control is read with a name; the two "Buka" buttons on Saya (Kartu saya, Pengaturan) are told apart by their card. |
| S-6 | Cold start timing on the Redmi 10 (stopwatch or `adb shell am start -W`). | Under the NF-1 budget in Design §10 (B-19 measures this properly). |
