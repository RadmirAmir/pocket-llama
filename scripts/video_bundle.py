"""Required video program files shared by staging, APK and AAB verification."""
import hashlib
import json
from pathlib import Path

ASSETS = ('runtime-id.txt', 'launcher.py', 'python.zip', 'yt-dlp')
LIBRARIES = ('libpython.so', 'libqjs.so')
REQUIRED = frozenset(['app/src/main/assets/video/' + n for n in ASSETS] +
                     ['app/src/main/jniLibs/arm64-v8a/' + n for n in LIBRARIES])


def runtime_id(root):
    return (hashlib.sha256((Path(root) / 'video-runtime.lock.json').read_bytes()).hexdigest() + '\n').encode('ascii')


def check_file(path, item):
    if not path.is_file() or path.stat().st_size != item['bytes']:
        raise ValueError('Missing or incomplete video runtime file: ' + str(path))
    with path.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != item['sha256']:
            raise ValueError('Video runtime checksum mismatch: ' + str(path))


def inventory(root):
    root = Path(root)
    manifest = json.loads((root / 'video-runtime.manifest.json').read_text())
    files = manifest['files']
    paths = [item['path'] for item in files]
    if len(paths) != len(set(paths)) or set(paths) != REQUIRED:
        raise ValueError('Incomplete video runtime inventory; run scripts/prepare-video.py. Required: ' + ', '.join(sorted(REQUIRED)))
    for item in files:
        check_file(root / item['path'], item)
    if (root / 'app/src/main/assets/video/runtime-id.txt').read_bytes() != runtime_id(root):
        raise ValueError('Video runtime ID does not match video-runtime.lock.json')
    return files


def validate_assets(directory, files):
    directory = Path(directory)
    for item in files:
        if item['path'].startswith('app/src/main/assets/'):
            check_file(directory / item['path'].removeprefix('app/src/main/assets/'), item)


def validate_archive(archive, files, base=''):
    names = archive.namelist()
    for item in files:
        path = item['path']
        if path.startswith('app/src/main/assets/'):
            name = base + 'assets/' + path.removeprefix('app/src/main/assets/')
        else:
            name = base + 'lib/arm64-v8a/' + Path(path).name
        if names.count(name) != 1 or archive.getinfo(name).file_size != item['bytes']:
            raise ValueError('Missing or incomplete video runtime in artifact: ' + name)
        with archive.open(name) as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != item['sha256']:
                raise ValueError('Video runtime checksum mismatch in artifact: ' + name)
    return len(files)
