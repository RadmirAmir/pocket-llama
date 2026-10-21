#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/search-android/evidence
trap 'adb logcat -d > build/search-android/evidence/full-logcat.txt' EXIT
adb install -r build/search-android/probe.apk
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
adb shell am start -W -n dev.pocketllama.searchtest/.AndroidSearchProbe
for attempt in $(seq 1 120); do
    adb logcat -d -s PocketSearchTest:I PocketSearchRuntime:I > build/search-android/evidence/logcat.txt
    if grep -q 'PASS:' build/search-android/evidence/logcat.txt; then
        cat build/search-android/evidence/logcat.txt
        exit 0
    fi
    if grep -q 'FAIL' build/search-android/evidence/logcat.txt; then
        cat build/search-android/evidence/logcat.txt
        exit 1
    fi
    sleep 1
done
adb logcat -d > build/search-android/evidence/full-logcat.txt
cat build/search-android/evidence/logcat.txt
exit 1
