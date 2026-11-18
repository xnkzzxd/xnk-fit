#!/usr/bin/env bash
# Permission gate (Design §14): the built APK may declare only the allowlist below.
# Usage: tools/check_permissions.sh <apk>   (needs aapt2 from Android build-tools on PATH or ANDROID_HOME)
set -euo pipefail
apk="${1:?usage: check_permissions.sh <apk>}"
aapt2="$(command -v aapt2 || ls "${ANDROID_HOME:-/nonexistent}"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
[ -n "$aapt2" ] || { echo "aapt2 not found" >&2; exit 2; }
allowed='
android.permission.POST_NOTIFICATIONS
android.permission.CAMERA
android.permission.health.READ_STEPS
android.permission.INTERNET
android.permission.SCHEDULE_EXACT_ALARM
android.permission.WAKE_LOCK
android.permission.ACCESS_NETWORK_STATE
app.xnkfit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
'
found="$("$aapt2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\(.*\)'.*/\1/p")"
bad=0
for p in $found; do
  if ! grep -qxF "$p" <<<"$allowed"; then echo "FAIL: permission not in Design §14: $p" >&2; bad=1; fi
done
[ "$bad" -eq 0 ] && echo "OK: permissions within allowlist ($(wc -w <<<"$found") declared)"
exit "$bad"
