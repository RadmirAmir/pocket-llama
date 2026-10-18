#!/usr/bin/env bash
# This test resets only the public package in a disposable emulator.
set -euo pipefail
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] && [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != 1 ]; then
    echo 'Refusing destructive installation tests on a physical device' >&2
    exit 1
fi
mkdir -p build/install install-evidence
adb shell getprop > install-evidence/device-properties.txt
adb logcat -c
trap 'adb logcat -d > install-evidence/logcat.txt' EXIT
curl --fail --location --retry 3 --output build/install/community-0.9.2.apk https://github.com/RadmirAmir/pocket-llama/releases/download/v0.9.2-rc.1/PocketLlama-0.9.2-community-arm64.apk
curl --fail --location --retry 3 --output build/install/community-0.9.3.apk https://github.com/RadmirAmir/pocket-llama/releases/download/v0.9.3-rc.1/PocketLlama-0.9.3-community-arm64.apk
echo 'd888310192043510c8309edbb969fa512aec26c965e63bffd66a9a6607ac2991  build/install/community-0.9.2.apk' | sha256sum --check
echo 'bf064dac3023c27e3d831d70fe2d6c850be9b8bf697ba5aae56759a92b51a1ed  build/install/community-0.9.3.apk' | sha256sum --check
python3 - <<'PY'
from pathlib import Path
import hashlib,json,zipfile
with zipfile.ZipFile('build/install/community-0.9.3.apk') as apk:
    names=apk.namelist()
    profile=json.loads(apk.read('assets/distribution.json'))
    assert profile['channel']=='community' and profile['suryaEnabled'] is True
    assert not any('line-detector' in n or '/notices/Surya-detector-' in n for n in names)
    for name in ['runtime-id.txt','launcher.py','python.zip','yt-dlp']:
        assert names.count('assets/video/'+name)==1
    stamp=(hashlib.sha256(Path('video-runtime.lock.json').read_bytes()).hexdigest()+'\n').encode()
    assert apk.read('assets/video/runtime-id.txt')==stamp
    assert apk.read('assets/video/launcher.py')==Path('app/src/main/assets/video/launcher.py').read_bytes()
    lock=json.loads(Path('video-runtime.lock.json').read_text())
    tool=next(a for a in lock['artifacts'] if a['name']=='yt-dlp')
    assert hashlib.sha256(apk.read('assets/video/yt-dlp')).hexdigest()==tool['sha256']
    for name in ['libpython.so','libqjs.so']:
        assert apk.getinfo('lib/arm64-v8a/'+name).file_size>0
print('Published community contains the complete pinned video program bundle')
PY
adb install --no-streaming --no-incremental build/install/community-0.9.2.apk > install-evidence/baseline-0.9.2.txt 2>&1
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-baseline-0.9.2.txt
adb install --no-streaming --no-incremental -r build/install/community-0.9.3.apk > install-evidence/upgrade-0.9.2-to-0.9.3.txt 2>&1
cat install-evidence/upgrade-0.9.2-to-0.9.3.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-upgrade-0.9.3.txt
sleep 3
adb shell pidof dev.pocketllama
adb shell dumpsys package dev.pocketllama > install-evidence/package-0.9.3.txt
python3 - <<'PY'
from pathlib import Path
import re
package=Path('install-evidence/package-0.9.3.txt').read_text()
assert re.search(r'versionCode=26(?:\s|$)',package)
assert re.search(r'versionName=0\.9\.3(?:\s|$)',package)
print('Installed community version 0.9.3 / code 26 verified')
PY
adb uninstall dev.pocketllama
adb install --no-streaming --no-incremental build/install/community-0.9.3.apk > install-evidence/fresh-0.9.3.txt 2>&1
cat install-evidence/fresh-0.9.3.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-fresh-0.9.3.txt
sleep 3
adb shell pidof dev.pocketllama

# Exercise programs from the published APK, not missing-file-safe developer assets.
python3 scripts/prepare-test-deps.py
python3 scripts/prepare-security.py
python3 scripts/prepare-video.py
CHAT_JSON_JAR="$PWD/build/test-deps/json.jar" python3 scripts/test-video-runtime.py --apk build/install/community-0.9.3.apk --fixture tests/fixtures/video-tone.m4a | tee install-evidence/video-host.txt
python3 scripts/test-video-android.py --sdk "$ANDROID_HOME" --jdk "$JAVA_HOME" --apk build/install/community-0.9.3.apk --fixture tests/fixtures/video-tone.m4a | tee install-evidence/video-android.txt
