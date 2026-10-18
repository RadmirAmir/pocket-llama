#!/usr/bin/env bash
# Only run against a disposable emulator: the public test package is reset below.
set -euo pipefail
mkdir -p install-evidence
adb shell getprop > install-evidence/device-properties.txt
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.dalvik.vm.native.bridge
adb logcat -c
trap 'adb logcat -d > install-evidence/logcat.txt' EXIT
set +e
adb install --no-streaming --no-incremental -r build/install/community-0.9.0.apk > install-evidence/fresh-0.9.0.txt 2>&1
fresh=$?
set -e
cat install-evidence/fresh-0.9.0.txt
if [ "$fresh" -ne 0 ]; then
    adb install --no-streaming --no-incremental -r build/install/community-0.8.0.apk > install-evidence/baseline-0.8.0.txt 2>&1 || true
    cat install-evidence/baseline-0.8.0.txt
    exit "$fresh"
fi
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup.txt
sleep 3
adb shell pidof dev.pocketllama
adb shell dumpsys package dev.pocketllama > install-evidence/package.txt
adb shell uiautomator dump /sdcard/pocket-install-ui.xml
adb pull /sdcard/pocket-install-ui.xml install-evidence/ui.xml
adb uninstall dev.pocketllama
adb install --no-streaming --no-incremental build/install/community-0.8.0.apk > install-evidence/baseline-0.8.0.txt 2>&1
adb install --no-streaming --no-incremental -r build/install/community-0.9.0.apk > install-evidence/upgrade-0.8-to-0.9.txt 2>&1
cat install-evidence/upgrade-0.8-to-0.9.txt
if [ -f artifacts/Pocket-community-installer-1.0.apk ]; then
    python3 scripts/test-community-installer.py
fi
