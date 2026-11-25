#!/usr/bin/env bash
# Runs app.xnkfit.screenshots.ScreenshotTest once per display variant on the connected emulator and pulls the PNGs.
# Usage: tools/screenshots/run-variants.sh <out-dir>   (debug app and androidTest APK already built)
# A variant is <hp|tablet>_<terang|gelap>_<1.0|1.3>. Window size and density are set with `wm`, so the real MainActivity
# sees a ~411x891 dp phone or a 800x1280 dp tablet; night mode and font scale are system settings.
set -u
OUT="${1:?out dir}"
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$OUT"
adb install -r -t "$APP_APK" >/dev/null && adb install -r -t "$TEST_APK" >/dev/null || { echo "install failed"; exit 1; }
for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $s 0; done
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard

status=0
for device in hp tablet; do
  if [ "$device" = hp ]; then adb shell wm size 1080x2340; adb shell wm density 420      # 411 x 891 dp
  else adb shell wm size 1600x2560; adb shell wm density 320; fi                           # 800 x 1280 dp
  for theme in terang gelap; do
    adb shell cmd uimode night "$([ $theme = gelap ] && echo yes || echo no)"
    for scale in 1.0 1.3; do
      variant="${device}_${theme}_${scale}"
      adb shell settings put system font_scale "$scale"
      adb shell pm clear app.xnkfit >/dev/null
      echo "== $variant"
      adb shell am instrument -w -e class app.xnkfit.screenshots.ScreenshotTest -e variant "$variant" \
        app.xnkfit.test/androidx.test.runner.AndroidJUnitRunner | tail -n 25 || status=1
      for f in $(adb shell run-as app.xnkfit ls files/screenshots | tr -d '\r'); do
        adb exec-out run-as app.xnkfit cat "files/screenshots/$f" > "$OUT/$f"
      done
    done
  done
done
adb shell wm size reset; adb shell wm density reset; adb shell settings put system font_scale 1.0
# The instrumentation can report "OK" while a screen was missed; the test itself fails then, so check the files too.
n=$(ls "$OUT"/*.png 2>/dev/null | wc -l); echo "png files: $n"
[ "$n" -gt 0 ] || status=1
exit $status
