#!/usr/bin/env bash
set -euo pipefail
# The existing acceptance script refuses physical devices before any uninstall.
bash scripts/test-release-android.sh
work=build/release-candidate
adb uninstall dev.pocketllama
adb install --no-streaming --no-incremental "$work/baseline-source.apk" > "$work/evidence/baseline-0132-install.txt" 2>&1
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > "$work/evidence/baseline-0132-start.txt"
adb install --no-streaming --no-incremental -r "$work/candidate.apk" > "$work/evidence/upgrade-from-0132.txt" 2>&1
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > "$work/evidence/upgrade-from-0132-start.txt"
adb shell pidof dev.pocketllama
echo 'PASS: signed community in-place 0.13.2 upgrade and startup'
