#!/usr/bin/env python3
"""Restore pinned CPython/QuickJS and official yt-dlp. Build-time downloads only.

The Python archive uses Unix symlinks. Resolve them at build time so Android's
ZipInputStream can install ordinary files without a shell or symlink APIs.
"""
from pathlib import Path, PurePosixPath
import hashlib,io,json,posixpath,shutil,stat,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
lock=json.loads((ROOT/'video-runtime.lock.json').read_text())
cache=ROOT/'.cache/video';cache.mkdir(parents=True,exist_ok=True)
assets=ROOT/'app/src/main/assets/video';assets.mkdir(parents=True,exist_ok=True)
libs=ROOT/'app/src/main/jniLibs/arm64-v8a'
for item in lock['artifacts']:
    target=cache/item['name']
    if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest()!=item['sha256']:
        temp=target.with_suffix('.part')
        with urllib.request.urlopen(item['url'],timeout=120) as response,temp.open('wb') as out:shutil.copyfileobj(response,out)
        if temp.stat().st_size!=item['bytes'] or hashlib.sha256(temp.read_bytes()).hexdigest()!=item['sha256']:raise ValueError('Runtime download checksum mismatch')
        temp.replace(target)
with zipfile.ZipFile(cache/'ytdl.aar') as aar:
    for name in ['libpython.so','libqjs.so']:(libs/name).write_bytes(aar.read('jni/arm64-v8a/'+name))
    with zipfile.ZipFile(io.BytesIO(aar.read('jni/arm64-v8a/libpython.zip.so'))) as source,zipfile.ZipFile(assets/'python.zip','w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as target:
        def content(name,seen=()):
            if name in seen or len(seen)>40:raise ValueError('Cyclic runtime symlink')
            info=source.getinfo(name);raw=source.read(name)
            if not stat.S_ISLNK(info.external_attr>>16):return raw
            resolved=posixpath.normpath(posixpath.join(posixpath.dirname(name),raw.decode()))
            if resolved.startswith('../') or resolved.startswith('/'):raise ValueError('Runtime symlink escapes archive')
            return content(resolved,seen+(name,))
        for info in source.infolist():
            if info.is_dir():continue
            if info.filename.startswith('/') or '..' in PurePosixPath(info.filename).parts:raise ValueError('Unsafe archive entry')
            entry=zipfile.ZipInfo(info.filename,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100600<<16
            target.writestr(entry,content(info.filename))
shutil.copyfile(cache/'yt-dlp',assets/'yt-dlp')
stamp=hashlib.sha256((ROOT/'video-runtime.lock.json').read_bytes()).hexdigest()
(assets/'runtime-id.txt').write_text(stamp+'\n')
files=[]
for path in [assets/'python.zip',assets/'yt-dlp',libs/'libpython.so',libs/'libqjs.so']:
    files.append({'path':str(path.relative_to(ROOT)),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
manifest={'yt_dlp':'2026.08.19','android_runtime':'youtubedl-android 0.18.1','files':files}
existing=ROOT/'video-runtime.manifest.json'
if existing.is_file() and json.loads(existing.read_text())!=manifest:raise ValueError('Derived video runtime differs from lock')
existing.write_text(json.dumps(manifest,indent=2)+'\n')
print('Video runtime ready:',sum(f['bytes'] for f in files),'bytes')
