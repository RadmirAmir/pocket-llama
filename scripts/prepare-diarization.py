#!/usr/bin/env python3
"""Restore the pinned, bundled Nemotron Q8 model; inference needs no network."""
from pathlib import Path
import hashlib
import json
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def prepare():
    lock = json.loads((ROOT / 'diarization.lock.json').read_text())
    item = lock['model']
    dest = ROOT / 'app/src/main/assets/diarization' / item['name']
    def valid(path):
        if not path.is_file() or path.stat().st_size != item['bytes']:
            return False
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha256').hexdigest() == item['sha256']
    if not valid(dest):
        dest.parent.mkdir(parents=True, exist_ok=True)
        part = dest.with_suffix('.part')
        try:
            with urllib.request.urlopen(item['source'], timeout=180) as source, part.open('wb') as out:
                shutil.copyfileobj(source, out, 1024 * 1024)
            if not valid(part):
                raise ValueError('Nemotron model checksum mismatch')
            part.replace(dest)
        finally:
            part.unlink(missing_ok=True)
    manifest = {'engine': lock['engine'], 'files': [item]}
    (dest.parent / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Pinned Nemotron Q8 model verified', flush=True)


if __name__ == '__main__':
    prepare()
