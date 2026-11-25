#!/usr/bin/env bash
# Runs inside android-emulator-runner (B-18, docs/Test-Manual.md). Order matters:
# 1. the main suite through Gradle (it uninstalls the app at the end),
# 2. install again, run phase 1 of the kill test, `am force-stop` (the real process kill), run phase 2 in a new process.
set -u
OUT=app/build/emulator-smoke
mkdir -p "$OUT"
START=$(date +%s)
fail=0
trap 'adb logcat -d > "$OUT/logcat.txt" 2>&1 || true' EXIT

./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notPackage=app.xnkfit.smoke.kill || fail=1
MAIN=$(date +%s)

RUNNER=app.xnkfit.test/androidx.test.runner.AndroidJUnitRunner
./gradlew --no-daemon installDebug installDebugAndroidTest || fail=1
adb shell am instrument -w -e class app.xnkfit.smoke.kill.LoggerKillTest#phase1Writes $RUNNER | tee "$OUT/kill1.txt"
grep -q "^OK (1 test)" "$OUT/kill1.txt" || fail=1
adb shell am force-stop app.xnkfit
adb shell am instrument -w -e class app.xnkfit.smoke.kill.LoggerKillTest#phase2SetSurvives $RUNNER | tee "$OUT/kill2.txt"
grep -q "^OK (1 test)" "$OUT/kill2.txt" || fail=1
END=$(date +%s)

{
  echo "main suite (Gradle, incl. build): $((MAIN - START)) s"
  echo "kill test (install + 2 runs): $((END - MAIN)) s"
  echo "script total: $((END - START)) s"
} | tee "$OUT/timing.txt"
exit $fail
