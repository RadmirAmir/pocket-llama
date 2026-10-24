#!/usr/bin/env bash
# Only disposable emulators; verify the signed draft before publishing it.
set -euo pipefail
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] && [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != 1 ]; then
    echo 'Refusing installation tests on a physical device' >&2
    exit 1
fi
work=build/release-candidate
mkdir -p "$work/evidence"
adb logcat -c
trap 'adb logcat -d > "$work/evidence/logcat.txt"' EXIT
python3 - <<'PY'
from pathlib import Path
import importlib.util,json,re,subprocess,os,zipfile
root=Path.cwd(); tools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
config=json.loads((root/'distribution/release.json').read_text())
candidate=root/'build/release-candidate/candidate.apk'; baseline=candidate.with_name('baseline.apk')
def cert(path):
    text=subprocess.check_output(['java','-jar',str(tools/'lib/apksigner.jar'),'verify','--print-certs',str(path)],text=True)
    return re.search(r'certificate SHA-256 digest: ([a-f0-9]+)',text).group(1)
assert cert(candidate)==cert(baseline)=='ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0'
badging=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(candidate)],text=True)
assert "name='dev.pocketllama'" in badging
assert "versionCode='"+str(config['versionCode'])+"'" in badging
assert "versionName='"+config['versionName']+"'" in badging
subprocess.run([str(tools/'zipalign'),'-c','-P','16','4',str(candidate)],check=True)
with zipfile.ZipFile(candidate) as z:
    for group in ['speech','moonshine','ocr']:
        for item in json.loads((root/'app/src/main/assets'/group/'manifest.json').read_text())['files']:
            assert z.getinfo('assets/'+group+'/'+item['name']).file_size==item['bytes']
    for name in ['chat.html','chat.css','studio.js','studio.css','i18n-en.json','search/server.py','rag/model.json']:
        assert z.read('assets/'+name)==(root/'app/src/main/assets'/name).read_bytes(),name
    assert z.getinfo('assets/search/python.zip').file_size>1000000
    assert json.loads(z.read('assets/distribution.json'))['channel']=='community'
print('PASS: exact community identity, previous certificate, version, alignment, full model set and current search/RAG assets')
PY
adb uninstall dev.pocketllama >/dev/null 2>&1 || true
adb install --no-streaming --no-incremental "$work/baseline.apk" > "$work/evidence/baseline-install.txt" 2>&1
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > "$work/evidence/baseline-start.txt"
adb install --no-streaming --no-incremental -r "$work/candidate.apk" > "$work/evidence/upgrade-from-rc3.txt" 2>&1
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > "$work/evidence/upgrade-start.txt"
sleep 3
adb shell pidof dev.pocketllama
adb shell dumpsys package dev.pocketllama > "$work/evidence/installed-package.txt"
adb uninstall dev.pocketllama
adb install --no-streaming --no-incremental "$work/candidate.apk" > "$work/evidence/fresh-install.txt" 2>&1
adb shell am start -W -n dev.pocketllama/dev.pocketllama.MainActivity > "$work/evidence/fresh-start.txt"
sleep 3
adb shell pidof dev.pocketllama
python3 scripts/test-video-android.py --sdk "$ANDROID_HOME" --jdk "$JAVA_HOME" --apk "$work/candidate.apk" --fixture tests/fixtures/video-tone.m4a | tee "$work/evidence/video-notes-knowledge.txt"
echo 'PASS: signed community fresh install, startup and in-place RC3 upgrade'
