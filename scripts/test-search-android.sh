#!/usr/bin/env bash
set -euo pipefail
adb install -r build/search-android/probe.apk
adb logcat -c
adb shell am start -n dev.pocketllama.searchtest/.AndroidSearchProbe
mkdir -p build/search-android/evidence
for attempt in $(seq 1 60); do
    adb logcat -d -s PocketSearchTest:I > build/search-android/evidence/logcat.txt
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
