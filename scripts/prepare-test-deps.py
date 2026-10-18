#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,urllib.request
ROOT=Path(__file__).resolve().parents[1]
entry=json.loads((ROOT/'distribution/test-deps.lock.json').read_text())['json']
target=ROOT/'build/test-deps/json.jar';target.parent.mkdir(parents=True,exist_ok=True)
with urllib.request.urlopen(entry['url'],timeout=30) as r:raw=r.read()
if hashlib.sha256(raw).hexdigest()!=entry['sha256']:raise SystemExit('Host test dependency checksum mismatch')
target.write_bytes(raw);print('Pinned host JSON dependency ready')
