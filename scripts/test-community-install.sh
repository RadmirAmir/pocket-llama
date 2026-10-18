#!/usr/bin/env bash
# Only run against a disposable emulator: the public test package is reset below.
set -euo pipefail
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] && [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != 1 ]; then
    echo 'Refusing destructive installation tests on a physical device' >&2
    exit 1
fi
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

# Install the previous community version before testing the exact new release.
curl --fail --location --retry 3 --output build/install/community-0.9.1.apk https://github.com/RadmirAmir/pocket-llama/releases/download/v0.9.1-rc.1/PocketLlama-0.9.1-community-arm64.apk
echo '91e424025d3d0594e47fd277bea241a4dd876b1a4daddba1d8b6fa8945cb5b93  build/install/community-0.9.1.apk' | sha256sum --check
adb install --no-streaming --no-incremental -r build/install/community-0.9.1.apk > install-evidence/baseline-0.9.1.txt 2>&1
cat install-evidence/baseline-0.9.1.txt

curl --fail --location --retry 3 --output build/install/community-0.9.2.apk https://github.com/RadmirAmir/pocket-llama/releases/download/v0.9.2-rc.1/PocketLlama-0.9.2-community-arm64.apk
echo 'd888310192043510c8309edbb969fa512aec26c965e63bffd66a9a6607ac2991  build/install/community-0.9.2.apk' | sha256sum --check
python3 - <<'PY'
import json,zipfile
with zipfile.ZipFile('build/install/community-0.9.2.apk') as apk:
    names=apk.namelist()
    profile=json.loads(apk.read('assets/distribution.json'))
    assert profile['channel']=='community' and profile['suryaEnabled'] is True
    assert not any('line-detector' in n or '/notices/Surya-detector-' in n for n in names)
    assert not any(b'Ldev/pocketllama/photo/SuryaDetector;' in apk.read(n) for n in names if n.endswith('.dex'))
    assert not any(n.startswith('assets/surya/bundled/') for n in names)
    for name in ['Surya-community.txt','Surya-weights.txt']:
        assert len(apk.read('assets/notices/'+name))>500
    assert b'AI PUBS OPEN RAIL-M' in apk.read('assets/notices/Surya-weights.txt')
print('Published community keeps Surya/full model terms and excludes the retired detector')
PY
adb install --no-streaming --no-incremental -r build/install/community-0.9.2.apk > install-evidence/upgrade-0.9.1-to-0.9.2.txt 2>&1
cat install-evidence/upgrade-0.9.1-to-0.9.2.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-upgrade-0.9.2.txt
sleep 3
adb shell pidof dev.pocketllama
adb shell dumpsys package dev.pocketllama > install-evidence/package-0.9.2.txt
python3 - <<'PY'
from pathlib import Path
import re
package=Path('install-evidence/package-0.9.2.txt').read_text()
assert re.search(r'versionCode=25(?:\s|$)',package), 'Unexpected installed version code'
assert re.search(r'versionName=0\.9\.2(?:\s|$)',package), 'Unexpected installed version name'
print('Installed community version 0.9.2 / code 25 verified')
PY
adb uninstall dev.pocketllama
adb install --no-streaming --no-incremental build/install/community-0.9.2.apk > install-evidence/fresh-0.9.2.txt 2>&1
cat install-evidence/fresh-0.9.2.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-fresh-0.9.2.txt
sleep 3
adb shell pidof dev.pocketllama
