#!/usr/bin/env python3
"""Publish the owner-requested signed APK without changing accepted releases."""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = 'RadmirAmir/pocket-llama'
SOURCE = '31a36b00e07bed005f37416244600776a4510cb8'
TAG = 'v0.13.4-refactor.31a36b0'
CERTIFICATE = 'ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0'


def api(method, path, body=None):
    with tempfile.TemporaryDirectory() as directory:
        args = ['gh', 'api', '--method', method, 'repos/' + REPO + path]
        if body is not None:
            payload = Path(directory) / 'body.json'
            payload.write_text(json.dumps(body))
            args += ['--input', str(payload)]
        return json.loads(subprocess.check_output(args, text=True))


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    if os.environ.get('GITHUB_REPOSITORY') != REPO or not os.environ.get('GH_TOKEN'):
        raise ValueError('Delivery is restricted to the project repository')
    directory = ROOT / 'distribution/test-builds/31a36b0'
    plan = json.loads((directory / 'manifest.json').read_text())
    if plan['sourceCommit'] != SOURCE or plan['tag'] != TAG:
        raise ValueError('Unexpected candidate source or tag')
    candidate = ROOT / 'build/delivery' / plan['candidate']['name']
    baseline = ROOT / 'build/delivery/baseline.apk'
    checksum = plan['candidate']['sha256']
    if candidate.stat().st_size != plan['candidate']['bytes'] or digest(candidate) != checksum:
        raise ValueError('Candidate digest or length mismatch')
    if digest(baseline) != plan['baseline']['sha256']:
        raise ValueError('Baseline digest mismatch')
    protected = ['app', 'native', 'distribution/release.json', 'distribution/model-catalog.json',
                 'llama.lock', 'rag-model.lock.json', '*runtime.lock.json', 'scripts/build-apk.py',
                 'scripts/prepare-distribution.py', 'scripts/video_bundle.py', 'scripts/search_bundle.py']
    subprocess.run(['git', 'diff', '--exit-code', SOURCE, 'HEAD', '--', *protected], cwd=ROOT, check=True)
    ci = api('GET', '/actions/runs/40142889366')
    if ci['head_sha'] != SOURCE or ci['conclusion'] != 'success' or ci['status'] != 'completed':
        raise ValueError('Exact application source CI is not successful')
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    for path in (baseline, candidate):
        signature = subprocess.check_output(['java', '-jar', str(sdk / 'lib/apksigner.jar'),
                                             'verify', '--print-certs', str(path)], text=True)
        if 'certificate SHA-256 digest: ' + CERTIFICATE not in signature:
            raise ValueError('Signing certificate mismatch')
        badging = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'badging', str(path)], text=True)
        identity = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
        if not identity or identity.groups() != ('dev.pocketllama', '37', '0.13.4') or 'application-debuggable' in badging:
            raise ValueError('Unexpected package, version or debugging flag')
    subprocess.run([str(sdk / 'zipalign'), '-c', '-P', '16', '4', str(candidate)], check=True)
    with zipfile.ZipFile(candidate) as archive:
        for name in ('native-rpc.js', 'studio.html', 'studio.js', 'notes.html', 'notes.js', 'photos.html', 'photos.js'):
            if archive.read('assets/' + name) != (ROOT / 'app/src/main/assets' / name).read_bytes():
                raise ValueError('Stale asset: ' + name)
        dex = b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
        for name in ('SpeechRecognizer', 'Transcription'):
            if ('Ldev/pocketllama/voice/' + name + ';').encode() not in dex:
                raise ValueError('Missing refactor class: ' + name)
    report = json.loads((directory / 'validation.json').read_text())
    if report['sha256'] != checksum or report['sourceCommit'] != SOURCE:
        raise ValueError('Local validation does not match this APK')
    report_path = candidate.parent / 'PocketLlama-31a36b0-validation.json'
    report_path.write_text(json.dumps(report, indent=2) + '\n')
    sums = candidate.parent / 'SHA256SUMS.txt'
    sums.write_text(checksum + '  ' + candidate.name + '\n')
    existing = next((r for r in api('GET', '/releases?per_page=100') if r['tag_name'] == TAG), None)
    if existing and not existing['draft']:
        asset = next((a for a in existing['assets'] if a['name'] == candidate.name), None)
        if not asset or asset.get('digest') != 'sha256:' + checksum or asset['size'] != candidate.stat().st_size:
            raise ValueError('Refusing to replace a published artifact')
        print(asset['browser_download_url'])
        return
    notes = (
        'Тестовая сборка рефакторинга из коммита `' + SOURCE + '`.\n\n'
        'Полный APK ARM64 с прежним идентификатором dev.pocketllama и подписью принятой версии 0.13.4. '
        'Версия внутри приложения остаётся 0.13.4 (код 37).\n\n'
        'Проверены подпись, структура APK, все 16 встроенных моделей, 14 прежних нативных библиотек '
        'и соответствие ресурсов исходному коду. Исходный коммит прошёл CI. '
        'Установка именно этой сборки и работа на физическом телефоне ещё не проверялись.\n\n'
        'Сборка предназначена для тестирования владельцем. В main не вмерджена. '
        'Принятая версия 0.13.4 и её релиз сохранены.\n\nSHA-256: `' + checksum + '`.\n'
    )
    release = existing or api('POST', '/releases', {
        'tag_name': TAG, 'target_commitish': SOURCE, 'name': 'Pocket Llama — refactor test 31a36b0',
        'body': notes, 'draft': True, 'prerelease': True, 'make_latest': 'false',
    })
    if release['target_commitish'] != SOURCE:
        raise ValueError('Existing draft targets a different source')
    for path in (candidate, sums, report_path):
        asset = next((a for a in release['assets'] if a['name'] == path.name), None)
        if asset:
            if asset.get('digest') != 'sha256:' + digest(path):
                raise ValueError('Refusing to replace a different draft artifact')
        else:
            subprocess.run(['gh', 'release', 'upload', TAG, '--repo', REPO, str(path)], check=True)
    release = api('GET', '/releases/' + str(release['id']))
    asset = next(a for a in release['assets'] if a['name'] == candidate.name)
    if asset.get('digest') != 'sha256:' + checksum or asset['size'] != candidate.stat().st_size:
        raise ValueError('Uploaded APK differs from the verified file')
    api('PATCH', '/releases/' + str(release['id']), {'draft': False, 'prerelease': True, 'make_latest': 'false'})
    print(asset['browser_download_url'])


if __name__ == '__main__':
    main()
