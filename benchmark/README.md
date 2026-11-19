# benchmark/

Macrobenchmark and baseline profile generator for the app (B-19, Design §10). A `com.android.test` module: it runs
on a phone or emulator only. CI compiles it; it does not run it.

- `StartupBenchmark`: cold start to usable Hari ini (`timeToFullDisplayMs`, reported by `ReportDrawnWhen` in
  `TodayScreen`), with and without the baseline profile.
- `TabSwitchBenchmark`: Latihan → Makan → Saya → Hari ini, frame timing.
- `BaselineProfileGenerator`: the cold-start and tab path, for `./gradlew :app:generateBaselineProfile`.

Budgets are checked by `tools/benchmark-budget.mjs` on the results. How to run on the Redmi 10 and the API 26 / 2 GB
emulator, and the numbers: docs/Speed.md. Precondition: onboarding finished once on the device.
