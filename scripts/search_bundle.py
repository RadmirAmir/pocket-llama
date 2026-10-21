"""Required embedded SearXNG assets, shared by all distribution paths."""
from pathlib import Path
import hashlib
import json
import video_bundle

ASSETS = ('server.py', 'python.zip', 'runtime-id.txt')
REQUIRED = {'app/src/main/assets/search/' + n for n in ASSETS}


def inventory(root):
    root = Path(root)
    manifest = json.loads((root / 'search-runtime.manifest.json').read_text())
    if manifest.get('revision') != json.loads((root / 'search-runtime.lock.json').read_text())['searxngRevision']:
        raise ValueError('SearXNG source pin mismatch')
    files = manifest['files']
    if len(files) != len(REQUIRED) or {f['path'] for f in files} != REQUIRED:
        raise ValueError('Incomplete search runtime; run scripts/prepare-search.py')
    for item in files:
        video_bundle.check_file(root / item['path'], item)
    archive = root / 'app/src/main/assets/search/python.zip'
    with archive.open('rb') as stream:
        expected = hashlib.file_digest(stream, 'sha256').hexdigest() + '\n'
    if (root / 'app/src/main/assets/search/runtime-id.txt').read_text() != expected:
        raise ValueError('SearXNG runtime ID mismatch')
    return files


validate_assets = video_bundle.validate_assets
validate_archive = video_bundle.validate_archive
