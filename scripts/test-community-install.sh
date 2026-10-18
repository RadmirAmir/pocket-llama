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

# Verify the exact published Surya update as well as the original diagnostic fixture.
curl --fail --location --retry 3 --output build/install/community-0.9.1.apk https://github.com/RadmirAmir/pocket-llama/releases/download/v0.9.1-rc.1/PocketLlama-0.9.1-community-arm64.apk
echo '91e424025d3d0594e47fd277bea241a4dd876b1a4daddba1d8b6fa8945cb5b93  build/install/community-0.9.1.apk' | sha256sum --check
python3 - <<'PY'
import hashlib,json,zipfile
with zipfile.ZipFile('build/install/community-0.9.1.apk') as apk:
    profile=json.loads(apk.read('assets/distribution.json'))
    assert profile['channel']=='community' and profile['suryaEnabled'] is True
    with apk.open('assets/surya/line-detector.onnx') as model:
        assert hashlib.file_digest(model,'sha256').hexdigest()=='b5f51dcfd8df8ae55bbc7938d8226f20699eef6e9d9577ee59b456cd6c169a78'
    assert not any(n.startswith('assets/surya/bundled/') for n in apk.namelist())
    for name in ['Surya-community.txt','Surya-weights.txt','Surya-detector-source.txt','Surya-detector-CC-BY-NC-SA-4.0.txt']:
        assert len(apk.read('assets/notices/'+name))>500
print('Published community Surya capability, detector and full terms verified')
PY
adb install --no-streaming --no-incremental -r build/install/community-0.9.1.apk > install-evidence/upgrade-0.9.0-to-0.9.1.txt 2>&1
cat install-evidence/upgrade-0.9.0-to-0.9.1.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-upgrade-0.9.1.txt
adb shell dumpsys package dev.pocketllama > install-evidence/package-0.9.1.txt
python3 - <<'PY'
from pathlib import Path
import re
package=Path('install-evidence/package-0.9.1.txt').read_text()
assert re.search(r'versionCode=24(?:\s|$)',package), 'Unexpected installed version code'
assert re.search(r'versionName=0\.9\.1(?:\s|$)',package), 'Unexpected installed version name'
print('Installed community version 0.9.1 / code 24 verified')
PY
adb uninstall dev.pocketllama
adb install --no-streaming --no-incremental build/install/community-0.9.1.apk > install-evidence/fresh-0.9.1.txt 2>&1
cat install-evidence/fresh-0.9.1.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-fresh-0.9.1.txt
sleep 3
adb shell pidof dev.pocketllama
