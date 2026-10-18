#!/usr/bin/env python3
"""Restore the pinned official SQLCipher/AndroidX/Kotlin runtime; verify before replacing."""
from pathlib import Path
import hashlib,io,json,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
for artifact in json.loads((ROOT/'security-runtime.lock.json').read_text())['artifacts']:
    files=artifact['files']
    if all((ROOT/f['path']).is_file() and hashlib.sha256((ROOT/f['path']).read_bytes()).hexdigest()==f['sha256'] for f in files):continue
    with urllib.request.urlopen(artifact['url'],timeout=120) as response:raw=response.read()
    if hashlib.sha256(raw).hexdigest()!=artifact['sha256']:raise ValueError('Downloaded artifact checksum mismatch')
    for f in files:
        content=zipfile.ZipFile(io.BytesIO(raw)).read(f['entry']) if f['entry'] else raw
        if len(content)!=f['bytes'] or hashlib.sha256(content).hexdigest()!=f['sha256']:raise ValueError('Runtime component checksum mismatch')
        path=ROOT/f['path'];path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    print('Restored',artifact['url'])
print('Security runtime verified')
