# Speed budget: how it is checked and the numbers (B-19)

Budgets are in Design §10 (PRD NF-1, G6). This file says which ones are checked by CI, which need a device, how to
run the device checks on the Redmi 10 and on the API 26 / 2 GB emulator, and the numbers measured so far.

## 1. What is checked where

| Measure | Budget | Checked automatically (CI, every push) | Needs a device |
| --- | --- | --- | --- |
| Cold start to usable Hari ini | 2.0 s, CPU 4× slower | No (benchmark module only compiled) | `StartupBenchmark`, `timeToFullDisplayMs` median |
| Tab switch | 100 ms | No | `TabSwitchBenchmark`, `frameDurationCpuMs` P99 |
| Set tick saved to disk | 50 ms | Yes: `SpeedBudgetTest.setTickSavedToDiskWithinBudget` (median ≤ 50 ms, p95 ≤ 100 ms, JVM) | Optional |
| Food search per keystroke | 100 ms | Yes: `SpeedBudgetTest.foodSearchPerKeystrokeOnTheFullTableWithinBudget` (all 1,143 TKPI + 174 estimate rows) | Optional |
| Installed size | 20 MB | Yes: release APK file size (`tools/check_apk_size.sh`) | Installed size on the phone, §3 |

JVM numbers come from Robolectric's SQLite on the CI box. They prove the path is one small transaction and an FTS4
index hit; they are not phone numbers. CI writes them to `app/build/speed/jvm-budgets.txt` (artifact `reports`).

## 2. Running the device checks

Once per device:

1. Install the benchmark build and finish onboarding by hand, so the app opens on Hari ini:
   `./gradlew :app:installBenchmarkRelease`, open XNK Fit, go through onboarding. Macrobenchmark keeps app data
   between runs; the benchmarks stop with a clear message if onboarding shows.
2. Turn off animations is **not** needed (Macrobenchmark measures real frames); keep the screen on and the phone
   charged above 50 %, not hot.

Run:

```
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
node tools/benchmark-budget.mjs benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/*/*benchmarkData.json
```

(add `--unthrottled` on the Redmi 10, see below). The second command exits 1 when a measure is over budget.

### Redmi 10 (owner's reference phone, S-05)

- Developer options on, USB debugging on, "Install via USB" on (MIUI asks for it), plug in, `adb devices`.
- The phone is not rooted, so its CPU clocks cannot be lowered. The run is unthrottled and is checked with
  `--unthrottled`: every budget divided by 4 (cold start 500 ms, tab switch 25 ms) ⚠ stand-in (Decisions.md).
- Baseline profile generation needs root on API 28–32; the Redmi 10 on Android 13 (API 33) can generate without root.

### Emulator API 26, 2 GB RAM, CPU 4× slower (Design §10)

```
sdkmanager "system-images;android-26;google_apis;x86_64" "emulator"
avdmanager create avd -n api26_2gb -k "system-images;android-26;google_apis;x86_64" -d pixel_4
# 2 cores, 2 GB; cgroup quota 25 % of the 2 cores = the CPU 4x slower than the host gives it
systemd-run --user --scope -p CPUQuota=50% emulator -avd api26_2gb -cores 2 -memory 2048 -no-snapshot -no-audio
```

On macOS or Windows use `cpulimit -l 50 -p <emulator pid>` instead of `systemd-run`. Needs hardware acceleration
(KVM / HVF / WHPX); a software emulator is far slower than 4× and its numbers mean nothing.
API 26 cannot generate a baseline profile (needs API 28 rooted or 33+); it only runs the benchmarks.

## 3. Installed size on a device

`adb shell pm path app.xnkfit` gives the APK; `adb shell dumpsys package app.xnkfit | grep -i codePath`, then
Settings → Apps → XNK Fit → Storage "App size" is the installed size (APK + compiled code). Write it in §4.

## 4. Numbers

| Date | Device | Build | Cold start (TTFD median) | Tab switch (frame P99) | Set tick | Search keystroke | Size | By |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-11-19 | CI-like sandbox JVM (Robolectric) | debug unit test | n/a | n/a | median 2.1 ms, p95 3.2 ms | median 1.6 ms, p95 3.4 ms | release APK file 2.9 MB | B-19 |
| — | Redmi 10 | benchmarkRelease | not run yet (owner's phone) | | | | | |
| — | Emulator API 26, 2 GB, CPU ¼ | benchmarkRelease | not run yet (no KVM in the cloud sandbox) | | | | | |

First start only, JVM: loading the bundled content into Room took 222 ms (report only, on `Dispatchers.IO`, before
onboarding; not on the Hari ini path).

## 5. Baseline profile

Generator: `benchmark/src/main/java/app/xnkfit/benchmark/BaselineProfileGenerator.kt` (cold start + four tab switches).
Not committed yet: it can only be made on an API 33+ emulator or phone (or rooted API 28+), and the cloud sandbox has
no KVM. No profile file is written by hand. To make it:

```
./gradlew :app:installBenchmarkRelease   # then finish onboarding once on the device
./gradlew :app:generateBaselineProfile
git add app/src/main/generated/baselineProfiles/ && git commit -m "Add baseline profile (B-19)"
```

`profileinstaller` is in the app (Design §12) so the profile is applied on sideloaded installs and by Macrobenchmark.

## Emulator CI run (2026-11-25)

These are the times of the smoke suite in `emulator.yml`, **not** the speed of the app and **not** Redmi 10 numbers (a shared CI runner with software GPU). Per job, cache miss on the first run: AVD creation about 1.3 min; smoke step about 6.3 min on API 26 phone 2 GB (Gradle build and 4 UI tests 311 s, kill test with install 45 s) and about 5 min on API 34 tablet (238 s and 44 s); whole job about 8 min and 6.5 min. No cold-start or frame number was taken; `:benchmark` still needs a device.
