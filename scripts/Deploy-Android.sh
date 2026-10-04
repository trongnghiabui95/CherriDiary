#!/usr/bin/env bash
set -euo pipefail

# Produces a debug APK for local testing; installation is a separate step.
if [[ "${1:-}" == "--help" ]]; then
  echo 'Usage: bash scripts/Deploy-Android.sh ["/d/Project Cherri/Compose Deploy App"]'
  exit 0
fi
if (( $# > 1 )); then echo 'Expected at most one deployment directory.' >&2; exit 2; fi
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
deploy_dir="${1:-/d/Project Cherri/Compose Deploy App}"
if command -v cygpath >/dev/null 2>&1; then deploy_dir="$(cygpath -u "$deploy_dir")"; fi
cd -- "$project_root"
bash ./gradlew -p android :app:assembleDebug --console=plain
apk="$project_root/android/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$apk" ]] || { echo 'Build did not produce app-debug.apk.' >&2; exit 1; }
mkdir -p -- "$deploy_dir"
staged_apk="$(mktemp "$deploy_dir/.app-debug.apk.XXXXXX")"
trap 'rm -f -- "$staged_apk"' EXIT
cp -- "$apk" "$staged_apk"
mv -f -- "$staged_apk" "$deploy_dir/app-debug.apk"
echo "APK copied: $deploy_dir/app-debug.apk"
echo 'Install this APK on the phone, or use adb install -r with its full path.'
