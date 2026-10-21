#!/usr/bin/env python3
"""Build a small x86_64 diagnostic APK using the production native bridge.

No model weights or inference server are needed. Only test APK responses are
fixtures; shipped production assets never contain this fixture entrypoint.
"""
from pathlib import Path
import argparse
import hashlib
import io
import os
import posixpath
import shutil
import stat
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run(list(map(str, args)), check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    args = parser.parse_args()
    sdk = Path(args.sdk)
    tools, android = sdk / 'build-tools/36.0.0', sdk / 'platforms/android-36/android.jar'
    work = ROOT / 'build/search-android'
    work.mkdir(parents=True, exist_ok=True)
    tls = work / 'tls'
    tls.mkdir(exist_ok=True)
    if not (tls / 'fixture-ca.pem').is_file():
        run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-sha256', '-nodes',
            '-keyout', tls / 'fixture-key.pem', '-out', tls / 'fixture-ca.pem',
            '-days', '30', '-subj', '/CN=Pocket disposable TLS fixture',
            '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1')
    classes, dex = work / 'classes', work / 'dex'
    for folder in (classes, dex):
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir()
    manifest = work / 'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.pocketllama.searchtest">
<uses-permission android:name="android.permission.INTERNET"/>
<application android:label="Pocket Search Test" android:debuggable="true" android:allowBackup="false" android:extractNativeLibs="true" android:usesCleartextTraffic="true">
<activity android:name=".AndroidSearchProbe" android:exported="true"/></application></manifest>''')
    run(tools / 'aapt2', 'link', '-o', work / 'base.apk', '-I', android, '--manifest', manifest, '--min-sdk-version', '29', '--target-sdk-version', '36')
    java = ROOT / 'app/src/main/java/dev/pocketllama'
    # Synthetic fixture queries only: retain child stderr in the diagnostic APK.
    # The shipped bridge continues to discard provider messages containing URLs.
    diagnostic_bridge = work / 'diagnostic/WebSearch.java'
    diagnostic_bridge.parent.mkdir(exist_ok=True)
    bridge = (java / 'search/WebSearch.java').read_text()
    quiet = 'while(in.read(b)!=-1){}'
    assert bridge.count(quiet) == 1
    diagnostic_bridge.write_text(bridge.replace(quiet, 'int n;while((n=in.read(b))!=-1){android.util.Log.e("PocketSearchRuntime",new String(b,0,n,StandardCharsets.UTF_8));}').replace('new String[]{"server.py"}', 'new String[]{"server.py","fixture-ca.pem","fixture-key.pem"}'))
    sources = [*sorted((java / 'core').glob('*.java')), java / 'ChatApi.java',
               java / 'video/VideoRuntime.java', *sorted(p for p in (java / 'search').glob('*.java') if p.name != 'WebSearch.java'), diagnostic_bridge,
               ROOT / 'tests/android-search/AndroidSearchProbe.java']
    run('javac', '--release', '11', '-encoding', 'UTF-8', '-cp', android, '-d', classes, *sources)
    run('java', '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '29', '--lib', android, '--output', dex, *sorted(classes.rglob('*.class')))
    python_zip = io.BytesIO()
    with zipfile.ZipFile(ROOT / '.cache/video/ytdl.aar') as aar:
        with zipfile.ZipFile(io.BytesIO(aar.read('jni/x86_64/libpython.zip.so'))) as original, zipfile.ZipFile(python_zip, 'w', compression=zipfile.ZIP_DEFLATED) as target:
            def content(name, seen=()):
                if name in seen:
                    raise ValueError('Cyclic runtime symlink')
                info = original.getinfo(name)
                raw = original.read(name)
                if stat.S_ISLNK(info.external_attr >> 16):
                    return content(posixpath.normpath(posixpath.join(posixpath.dirname(name), raw.decode())), seen + (name,))
                return raw
            for entry in original.infolist():
                if not entry.is_dir():
                    target.writestr(entry.filename, content(entry.filename))
        search_zip = io.BytesIO((ROOT / 'build/search-test-assets/python.zip').read_bytes())
        with zipfile.ZipFile(search_zip, 'a', compression=zipfile.ZIP_DEFLATED) as bundle:
            bundle.writestr('pocket_server.py', (ROOT / 'app/src/main/assets/search/server.py').read_bytes())
        with zipfile.ZipFile(work / 'base.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
            for file in sorted(dex.glob('*.dex')):
                apk.write(file, file.name)
            info = zipfile.ZipInfo('lib/x86_64/libpython.so')
            info.external_attr = 0o100755 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            apk.writestr(info, aar.read('jni/x86_64/libpython.so'))
            apk.writestr('assets/video/python.zip', python_zip.getvalue())
            apk.writestr('assets/video/runtime-id.txt', hashlib.sha256(python_zip.getvalue()).hexdigest() + '\n')
            apk.writestr('assets/video/yt-dlp', 'unused by search test')
            apk.write(ROOT / 'app/src/main/assets/video/launcher.py', 'assets/video/launcher.py')
            apk.writestr('assets/search/python.zip', search_zip.getvalue())
            apk.writestr('assets/search/runtime-id.txt', hashlib.sha256(search_zip.getvalue()).hexdigest() + '\n')
            apk.write(ROOT / 'tests/android-search/fixture-server.py', 'assets/search/server.py')
            for name in ['fixture-ca.pem', 'fixture-key.pem']:
                apk.write(tls / name, 'assets/search/' + name)
    run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
    key = work / 'debug.keystore'
    if not key.exists():
        run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey', '-dname', 'CN=Pocket Search Test', '-keyalg', 'RSA', '-validity', '365')
    run('java', '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--out', work / 'probe.apk', work / 'aligned.apk')
    print('Built native search probe:', work / 'probe.apk')


if __name__ == '__main__':
    main()
