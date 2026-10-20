#!/usr/bin/env bash
# This test resets only the public package in a disposable emulator.
# Published 0.11.0 is checked as an in-place update from 0.10.2.
set -euo pipefail
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] && [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != 1 ]; then
    echo 'Refusing destructive installation tests on a physical device' >&2
    exit 1
fi
mkdir -p build/install install-evidence
adb shell getprop > install-evidence/device-properties.txt
adb logcat -c
trap 'adb logcat -d > install-evidence/logcat.txt' EXIT
curl --fail --location --retry 3 --output build/install/community-0.10.2.apk https://github.com/RadmirAmir/pocket-llama/releases/download/v0.10.2-rc.1/PocketLlama-0.10.2-community-arm64.apk
curl --fail --location --retry 3 --output build/install/community-0.11.0.apk https://github.com/RadmirAmir/pocket-llama/releases/download/v0.11.0-rc.2/PocketLlama-0.11.0-community-arm64.apk
echo '8a070a2f260c220fbe78892f59e7054ad97bff8406b53907ca51bfee99f5e23c  build/install/community-0.10.2.apk' | sha256sum --check
echo '5154a6f203b8d1ba264966049b08e5ea3f304e4b2ba3a44e28a06f8697a99d07  build/install/community-0.11.0.apk' | sha256sum --check
python3 - <<'PY'
from pathlib import Path
import hashlib,json,zipfile,importlib.util
with zipfile.ZipFile('build/install/community-0.11.0.apk') as apk:
    spec=importlib.util.spec_from_file_location('artifact_check','scripts/validate-distribution-artifacts.py')
    check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)
    check.apk_resources(apk)
    names=apk.namelist()
    for name in ['notes.html','notes.js','notes.css','studio.html','studio.js','studio.css','chat.html','chat.css','photos.html','photos.js','rag/model.json','i18n.js','i18n-en.json','theme.js']:
        assert apk.read('assets/'+name)==Path('app/src/main/assets',name).read_bytes(), 'Stale asset: '+name
    dex=b''.join(apk.read(n) for n in names if n.endswith('.dex'))
    assert b'copyTranscript' in dex, 'Transcript copy method missing from native executable'
    for cls in ['NotesActivity','NoteStore','NoteText','NoteDraft']:
        assert ('Ldev/pocketllama/notes/'+cls+';').encode() in dex, cls
    for cls in ['RagMcp','RagStore','RagSources','RagManager','RagRuntime','RagPrompt']:
        assert ('Ldev/pocketllama/rag/'+cls+';').encode() in dex, cls
    assert json.loads(apk.read('assets/rag/model.json'))==json.loads(Path('rag-model.lock.json').read_text())
    for name in ['Gemma.txt','Gemma-terms.txt','Gemma-prohibited-uses.txt']:
        assert apk.read('assets/notices/'+name)==Path('notices',name).read_bytes(), name
    assert not any(n.startswith('assets/rag/') and n.endswith('.gguf') for n in names)
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
# Reset only after the emulator guard, including reruns of the native fixture tests.
adb uninstall dev.pocketllama >/dev/null 2>&1 || true
adb install --no-streaming --no-incremental build/install/community-0.10.2.apk > install-evidence/baseline-0.10.2.txt 2>&1
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-baseline-0.10.2.txt
adb install --no-streaming --no-incremental -r build/install/community-0.11.0.apk > install-evidence/upgrade-0.10.2-to-0.11.0.txt 2>&1
cat install-evidence/upgrade-0.10.2-to-0.11.0.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-upgrade-0.11.0.txt
sleep 3
adb shell pidof dev.pocketllama
adb shell dumpsys package dev.pocketllama > install-evidence/package-0.11.0.txt
python3 - <<'PY'
from pathlib import Path
import re
package=Path('install-evidence/package-0.11.0.txt').read_text()
assert re.search(r'versionCode=30(?:\s|$)',package)
assert re.search(r'versionName=0\.11\.0(?:\s|$)',package)
print('Installed community version 0.11.0 / code 30 verified')
PY
adb uninstall dev.pocketllama
adb install --no-streaming --no-incremental build/install/community-0.11.0.apk > install-evidence/fresh-0.11.0.txt 2>&1
cat install-evidence/fresh-0.11.0.txt
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > install-evidence/startup-fresh-0.11.0.txt
sleep 3
adb shell pidof dev.pocketllama

# Exercise programs from the published APK, not missing-file-safe developer assets.
# Source-level knowledge regression also covers notes containing only a title.
python3 scripts/prepare-test-deps.py
python3 scripts/prepare-security.py
python3 scripts/prepare-video.py
CHAT_JSON_JAR="$PWD/build/test-deps/json.jar" python3 scripts/test-video-runtime.py --apk build/install/community-0.11.0.apk --fixture tests/fixtures/video-tone.m4a | tee install-evidence/video-host.txt
python3 scripts/test-video-android.py --sdk "$ANDROID_HOME" --jdk "$JAVA_HOME" --apk build/install/community-0.11.0.apk --fixture tests/fixtures/video-tone.m4a | tee install-evidence/video-android.txt
