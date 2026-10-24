#!/usr/bin/env python3
"""Audit the frozen signed APK; record acceptance only after emulator success."""
from pathlib import Path
import argparse
import importlib.util
import json
import os
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('release_copy', ROOT/'scripts/reconstruct-release-apk.py')
copy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(copy)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accepted', action='store_true')
    args = parser.parse_args()
    plan, _ = copy.load_plan(ROOT/'distribution/release-candidates/0.13.4.json')
    apk = ROOT/'build/release-candidate/candidate.apk'
    if apk.stat().st_size != plan['candidate']['bytes'] or copy.digest(apk) != plan['candidate']['sha256']:
        raise ValueError('Candidate digest/length mismatch')
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        assert sorted(n for n in names if n.startswith('assets/search/')) == [
            'assets/search/python.zip', 'assets/search/runtime-id.txt', 'assets/search/server.py']
        dex = b''.join(archive.read(n) for n in names if n.startswith('classes') and n.endswith('.dex'))
        for removed in (b'BrowserSearch', b'browserSearch', b'BrowserFixture', b'AndroidSearchProbe'):
            assert removed not in dex, removed
        assert 'Поиск выполняется в фоне.'.encode() in dex
        assert b'summary-v5-video-evidence' in dex
        for name in ['studio.js','studio.css','i18n-en.json']:
            assert archive.read('assets/'+name) == (ROOT/'app/src/main/assets'/name).read_bytes(), name
        assert archive.read('assets/search/server.py') == (ROOT/'app/src/main/assets/search/server.py').read_bytes()
    print('PASS: frozen signed APK hash, background-search assets and absence of browser/diagnostic classes')
    if not args.accepted:
        return
    assert os.environ['GITHUB_REPOSITORY'] == 'RadmirAmir/pocket-llama'
    run = os.environ['GITHUB_RUN_ID']
    run_url = 'https://github.com/RadmirAmir/pocket-llama/actions/runs/'+run
    report = {'accepted': True, 'runId': run, 'runUrl': run_url, 'workflowCommit':os.environ['GITHUB_SHA'],
              'sourceCommit': plan['sourceCommit'], 'sha256': plan['candidate']['sha256'],
              'freshInstall': True, 'startup': True, 'upgradeFrom0110RC3': True,
              'upgradeFrom0133': True, 'notesRagSqlcipherVideoRegression': True,
              'device': 'Android 36 x86_64 emulator; ARM64 LLM performance not measured'}
    evidence = ROOT/'build/release-candidate/evidence'
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence/'accepted.json').write_text(json.dumps(report, indent=2)+'\n')
    (evidence/'SHA256SUMS.txt').write_text(plan['candidate']['sha256']+'  '+plan['candidate']['name']+'\n')
    validation = (ROOT/'docs/VALIDATION-0.13.4.md').read_text()
    validation += '\nExact signed APK acceptance passed: '+run_url+'\n'
    validation += 'Fresh installation/startup, in-place upgrades from 0.11.0 RC.3 and 0.13.3, notes/RAG/SQLCipher/video regression passed on Android 36 emulator.\n'
    (evidence/'VALIDATION-0.13.4.md').write_text(validation)
    metadata = json.loads((ROOT/'docs/artifact-validation-0.13.4.json').read_text())
    assert metadata['sha256'] == report['sha256']
    metadata['signedApkAcceptance'] = report
    (evidence/'artifact-validation-0.13.4.json').write_text(json.dumps(metadata, indent=2)+'\n')


if __name__ == '__main__':
    main()
