#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,urllib.request
ROOT=Path(__file__).resolve().parents[1]
entry=json.loads((ROOT/'distribution/build-tools.lock.json').read_text())['bundletool']
target=ROOT/'build/tools/bundletool.jar';target.parent.mkdir(parents=True,exist_ok=True)
if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest()!=entry['sha256']:
 with urllib.request.urlopen(entry['url'],timeout=90) as source,target.open('wb') as out:
  while b:=source.read(1024*1024):out.write(b)
 if hashlib.sha256(target.read_bytes()).hexdigest()!=entry['sha256']:raise SystemExit('Bundletool checksum mismatch')
print('Pinned official bundletool ready')
