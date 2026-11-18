#!/usr/bin/env bash
# APK size gate (Design §10: installed size 20 MB, PRD NF-1). Fails when over budget.
# Usage: tools/check_apk_size.sh <apk> [budget_bytes]   (default budget: 20 MB = 20971520 bytes)
# The APK file size stands in for installed size; the real installed size is checked on a device later (B-19).
set -euo pipefail
apk="${1:?usage: check_apk_size.sh <apk> [budget_bytes]}"
budget="${2:-20971520}"
[ -f "$apk" ] || { echo "APK not found: $apk" >&2; exit 2; }
size=$(stat -c %s "$apk")
echo "APK: $apk"
echo "Size: $size bytes ($((size / 1024)) KiB), budget: $budget bytes ($((budget / 1024)) KiB)"
if [ "$size" -gt "$budget" ]; then
  echo "FAIL: APK is over the size budget" >&2
  exit 1
fi
echo "OK: within budget"
