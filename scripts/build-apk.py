#!/usr/bin/env python3
"""Build this Android-framework + ONNX Runtime app with official SDK tools, without Maven or Gradle.

Prerequisites: JDK 17, Android SDK platform 36 and build-tools 36.0.0;
build native servers first. Produces a debug-signed personal-use APK.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def source_files(directory, pattern):
    return sorted(p for p in directory.rglob(pattern) if p.is_file() and not any(part.startswith(".") for part in p.relative_to(directory).parts))

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

def package_assets(archive, assets):
    # Check each actual ZIP input, including files that appear after staging.
    for asset in source_files(assets, '*'):
        relative = asset.relative_to(assets)
        if 'line-detector' in relative.name or relative.name.startswith('Surya-detector-'):
            raise SystemExit('Retired scanner asset in APK input: ' + str(relative))
        archive.write(asset, 'assets/' + relative.as_posix())

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
    parser.add_argument('--jdk', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output')
    parser.add_argument('--channel',choices=['personal','community'],default='personal')
    parser.add_argument('--no-bundled-surya',action='store_true',help='Compact personal update: retain Surya support and existing weights, bundle GigaAM and PaddleOCR.')
    parser.add_argument('--keystore',default=os.environ.get('POCKET_KEYSTORE'))
    parser.add_argument('--key-alias',default=os.environ.get('POCKET_KEY_ALIAS','androiddebugkey'))
    parser.add_argument('--debug-key',action='store_true',help='Explicitly allow the legacy/test key. Not a production signature.')
    args = parser.parse_args()
    if not args.sdk or not args.jdk:
        parser.error('Set ANDROID_HOME and JAVA_HOME, or provide --sdk and --jdk')
    sdk, jdk = Path(args.sdk).resolve(), Path(args.jdk).resolve()
    info=json.loads((ROOT/'distribution/release.json').read_text())
    spec=importlib.util.spec_from_file_location('distribution_prepare',ROOT/'scripts/prepare-distribution.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    stage=module.prepare(args.channel,bundle_surya=False if args.no_bundled_surya else None)
    package=info['legacyApplicationId'] if args.channel=='personal' else info['applicationId']
    artifact_channel='personal-update' if args.channel=='personal' and args.no_bundled_surya else args.channel
    if not args.output:args.output=str(ROOT/'artifacts'/('PocketLlama-'+info['versionName']+'-'+artifact_channel+'-arm64.apk'))
    if not args.keystore:parser.error('Provide --keystore; signing keys never belong in the source tree')
    if not args.debug_key and not (os.environ.get('POCKET_STORE_PASSWORD') and os.environ.get('POCKET_KEY_PASSWORD')):
        parser.error('Set POCKET_STORE_PASSWORD and POCKET_KEY_PASSWORD, or explicitly use --debug-key for a personal preview')
    suffix = '.exe' if os.name == 'nt' else ''
    tools = sdk / 'build-tools' / '36.0.0'
    android = sdk / 'platforms' / 'android-36' / 'android.jar'
    for path in [tools / ('aapt2'+suffix), tools / ('zipalign'+suffix), tools / 'lib/d8.jar', tools / 'lib/apksigner.jar', android, jdk / 'bin' / ('javac'+suffix)]:
        if not path.is_file(): parser.error('Missing SDK / JDK component: ' + str(path))
    libs = ROOT / 'app/src/main/jniLibs/arm64-v8a'
    if not (libs / 'libllama_cpu.so').is_file(): parser.error('Run scripts/build-native.sh cpu first')
    speech = ROOT / 'app/src/main/assets/speech'
    manifest_path = speech / 'manifest.json'
    if not manifest_path.is_file(): parser.error('Missing speech manifest; restore sources')
    for item in json.loads(manifest_path.read_text())['files']:
        asset = speech / item['name']
        if not asset.is_file() or asset.stat().st_size != item['bytes']:
            parser.error('Run python3 scripts/prepare-speech.py first: missing ' + item['name'])
        with asset.open('rb') as f:
            if hashlib.file_digest(f, 'sha256').hexdigest() != item['sha256']:
                parser.error('Speech model checksum mismatch: ' + item['name'])
    ocr = ROOT / 'app/src/main/assets/ocr'
    for item in json.loads((ocr / 'manifest.json').read_text())['files']:
        asset = ocr / item['name']
        if not asset.is_file() or asset.stat().st_size != item['bytes'] or hashlib.sha256(asset.read_bytes()).hexdigest() != item['sha256']:
            parser.error('OCR model mismatch: run python3 scripts/prepare-ocr.py')
    for artifact in json.loads((ROOT / 'security-runtime.lock.json').read_text())['artifacts']:
        for item in artifact['files']:
            path = ROOT / item['path']
            if not path.is_file() or path.stat().st_size != item['bytes'] or hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
                parser.error('Security runtime mismatch: run python3 scripts/prepare-security.py: ' + item['path'])
    for runtime_lock in ('speech-runtime.lock.json', 'moonshine-runtime.lock.json'):
        for item in json.loads((ROOT / runtime_lock).read_text())['files']:
            path = ROOT / item['path']
            if not path.is_file() or path.stat().st_size != item['bytes'] or hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
                parser.error('Speech runtime checksum mismatch: run python3 scripts/prepare-runtime.py: ' + item['path'])
    video = module.video_bundle.inventory(ROOT)
    scanner = json.loads((ROOT / 'scanner-runtime.lock.json').read_text())
    for item in scanner['files'] + [f for a in scanner['artifacts'] for f in a['files']]:
        if not json.loads((stage/'manifest.json').read_text())['surya'] and item['path'].startswith('app/src/main/assets/surya/'):continue
        path = ROOT / item['path']
        if not path.is_file() or path.stat().st_size != item['bytes'] or hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
            parser.error('Scanner runtime mismatch: run python3 scripts/prepare-scanner.py: ' + item['path'])
    build = ROOT / 'build' / ('sdk-apk-'+args.channel) 
    build.mkdir(parents=True, exist_ok=True)
    # Regenerate staging directories to avoid silently including stale classes or dex files.
    for subdir in ('generated', 'classes', 'dex'):
        target = build / subdir
        if target.exists(): shutil.rmtree(target)
        target.mkdir()
    manifest = build / 'AndroidManifest.xml'
    manifest_text = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    manifest_text = manifest_text.replace('<manifest ', '<manifest package="'+package+'" ', 1)
    manifest.write_text(manifest_text)
    aapt = tools / ('aapt2'+suffix)
    run(aapt, 'compile', '--dir', ROOT / 'app/src/main/res', '-o', build / 'resources.zip')
    run(aapt, 'link', '-o', build / 'resources.ap_', '-I', android, '--manifest', manifest,
        '--java', build / 'generated', '--custom-package', 'dev.pocketllama', '--min-sdk-version', '29', '--target-sdk-version', '36',
        '--version-code', str(info['versionCode']), '--version-name', info['versionName'], build / 'resources.zip')
    jars = [ROOT / 'app/libs/onnxruntime-android-1.23.2.jar', ROOT / 'app/libs/opencv-4.12.0.jar', ROOT / 'app/libs/jsoup-1.21.2.jar'] + [ROOT / item['path'] for artifact in json.loads((ROOT / 'security-runtime.lock.json').read_text())['artifacts'] for item in artifact['files'] if item['path'].endswith('.jar')]
    classpath = os.pathsep.join([str(android), *map(str, jars)])
    java, javac = jdk / 'bin' / ('java'+suffix), jdk / 'bin' / ('javac'+suffix)
    sources = source_files(ROOT / 'app/src/main/java', '*.java') + source_files(build / 'generated', '*.java')
    run(javac, '--release', '11', '-encoding', 'UTF-8', '-cp', classpath, '-d', build / 'classes', *sources)
    classes = source_files(build / 'classes', '*.class')
    run(java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '29',
        '--lib', android, '--output', build / 'dex', *classes, *jars)
    unsigned = build / 'unsigned.apk'
    shutil.copyfile(build / 'resources.ap_', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as z:
        for dex in sorted((build / 'dex').glob('*.dex')): z.write(dex, dex.name)
        for library in sorted(libs.glob('*.so')):
            info = zipfile.ZipInfo('lib/arm64-v8a/' + library.name)
            info.external_attr = 0o100755 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, library.read_bytes())
        # The staged assets include notices and channel-specific model capabilities.
        assets = stage / 'assets'
        if assets.is_dir():
            module.video_bundle.validate_assets(assets, video)
            package_assets(z, assets)
        module.video_bundle.validate_archive(z, video)
    aligned = build / 'aligned.apk'
    run(tools / ('zipalign'+suffix), '-P', '16', '-f', '4', unsigned, aligned)
    keystore=Path(args.keystore).resolve()
    if not keystore.is_file():parser.error('Signing key not found')
    output = Path(args.output).resolve(); output.parent.mkdir(parents=True, exist_ok=True)
    staged_output = build / 'signed-final.part'
    run(java, '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', keystore, '--ks-key-alias', args.key_alias,
        '--ks-pass', 'pass:android' if args.debug_key else 'env:POCKET_STORE_PASSWORD', '--key-pass', 'pass:android' if args.debug_key else 'env:POCKET_KEY_PASSWORD', '--out', staged_output, aligned)
    run(java, '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', staged_output)
    os.replace(staged_output, output)
    run(aapt, 'dump', 'badging', output)
    print('Built:', output)
    return 0

if __name__ == '__main__':
    sys.exit(main())
