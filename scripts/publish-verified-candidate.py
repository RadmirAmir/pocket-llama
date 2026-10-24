#!/usr/bin/env python3
"""Publish only a byte-identical APK accepted by this run's Android job."""
from pathlib import Path
import importlib.util
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REPO = 'RadmirAmir/pocket-llama'
spec = importlib.util.spec_from_file_location('release_copy', ROOT/'scripts/reconstruct-release-apk.py')
copy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(copy)


def api(method, path, body=None):
    with tempfile.TemporaryDirectory() as temporary:
        args = ['gh', 'api', '--method', method, 'repos/'+REPO+path]
        if body is not None:
            file = Path(temporary)/'body.json'
            file.write_text(json.dumps(body))
            args += ['--input', str(file)]
        return json.loads(subprocess.check_output(args, text=True))


def main():
    if os.environ.get('GITHUB_REPOSITORY') != REPO or not os.environ.get('GH_TOKEN'):
        raise ValueError('Publishing is restricted to this repository')
    plan, _ = copy.load_plan(ROOT/'distribution/release-candidates/0.13.4.json')
    accepted = ROOT/'build/accepted-release'
    report = json.loads((accepted/'accepted.json').read_text())
    if (report.get('accepted') is not True or report.get('runId') != os.environ['GITHUB_RUN_ID']
            or report.get('sha256') != plan['candidate']['sha256']
            or report.get('sourceCommit') != plan['sourceCommit']
            or report.get('workflowCommit') != os.environ['GITHUB_SHA']):
        raise ValueError('Android acceptance does not match this candidate/run')
    protected = ['app','native','distribution/release.json','distribution/model-catalog.json','llama.lock','rag-model.lock.json','*runtime.lock.json','scripts/build-apk.py','scripts/prepare-distribution.py','scripts/video_bundle.py','scripts/search_bundle.py']
    subprocess.run(['git','diff','--exit-code',plan['sourceCommit'],os.environ['GITHUB_SHA'],'--',*protected], cwd=ROOT, check=True)
    output = ROOT/'build/release-publication'/plan['candidate']['name']
    copy.reconstruct(ROOT/'distribution/release-candidates/0.13.4.json',
                     ROOT/'build/release-candidate/baseline-source.apk', output)
    # Read all authenticated release metadata through gh; no credentials reach
    # artifact storage or any external provider.
    releases = api('GET', '/releases?per_page=100')
    existing = next((r for r in releases if r['tag_name'] == plan['tag']), None)
    if existing and not existing['draft']:
        asset = next((a for a in existing['assets'] if a['name'] == output.name), None)
        if not asset or asset.get('digest') != 'sha256:'+plan['candidate']['sha256']:
            raise ValueError('Refusing to replace a different published APK')
        print('Exact accepted release is already published.')
        return
    notes = (ROOT/'docs/RELEASE-NOTES-0.13.4.md').read_text()
    notes = notes.replace('(VALIDATION-0.13.4.md)', '(https://github.com/'+REPO+'/blob/main/docs/VALIDATION-0.13.4.md)')
    notes += '\nAPK SHA-256: `'+plan['candidate']['sha256']+'`.\n\n'
    notes += 'App source: `'+plan['sourceCommit']+'`.\n\n'
    notes += 'Signed installation/upgrade acceptance: '+report['runUrl']+'.\n'
    body = {'tag_name': plan['tag'], 'target_commitish': os.environ['GITHUB_SHA'],
            'name': 'Pocket Llama 0.13.4 RC.1 — source-bound video takeaways',
            'body': notes, 'draft': True, 'prerelease': True}
    release = api('PATCH', '/releases/'+str(existing['id']), body) if existing else api('POST', '/releases', body)
    upload = [str(output)] + [str(accepted/name) for name in ('SHA256SUMS.txt','VALIDATION-0.13.4.md','artifact-validation-0.13.4.json')]
    # --clobber is used only for an unpublished draft owned by this run.
    subprocess.run(['gh', 'release', 'upload', plan['tag'], '--repo', REPO, '--clobber', *upload], check=True)
    release = api('GET', '/releases/'+str(release['id']))
    asset = next(a for a in release['assets'] if a['name'] == output.name)
    if asset.get('digest') != 'sha256:'+plan['candidate']['sha256'] or asset['size'] != plan['candidate']['bytes']:
        raise ValueError('Uploaded release digest/size mismatch')
    release = api('PATCH', '/releases/'+str(release['id']), {'tag_name':plan['tag'],'draft':False,'prerelease':True})
    print(json.dumps({'release':release['html_url'],'sha256':plan['candidate']['sha256']}))


if __name__ == '__main__':
    main()
