#!/usr/bin/env bash
# Test that the size gate fails over budget and passes under it.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
head -c 1000 /dev/zero > "$tmp/a.apk"
"$here/check_apk_size.sh" "$tmp/a.apk" 2000 >/dev/null
if "$here/check_apk_size.sh" "$tmp/a.apk" 500 >/dev/null 2>&1; then
  echo "FAIL: gate did not fail over budget" >&2; exit 1
fi
echo "apk size gate test: ok"
