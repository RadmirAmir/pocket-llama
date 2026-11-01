#!/usr/bin/env bash
# Exercise installation and update on a disposable Android emulator only.
set -euo pipefail
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] && [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != 1 ]; then
    echo 'Refusing installation tests on a physical device' >&2
    exit 1
fi
work=build/release-candidate
mkdir -p "$work/evidence"
adb logcat -c
trap 'adb logcat -d > "$work/evidence/logcat.txt"' EXIT
adb install --no-streaming --no-incremental "$work/baseline.apk" | tee "$work/evidence/old-install.txt"
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > "$work/evidence/old-start.txt"
adb install --no-streaming --no-incremental -r "$work/candidate.apk" | tee "$work/evidence/upgrade.txt"
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > "$work/evidence/upgrade-start.txt"
adb shell dumpsys package dev.pocketllama > "$work/evidence/installed-package.txt"
grep -q 'versionCode=39' "$work/evidence/installed-package.txt"
adb shell pidof dev.pocketllama
echo 'PASS: previous Nemotron APK updated in place and started on the Android emulator'
