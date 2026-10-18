#!/usr/bin/env python3
"""Restore checksum-pinned Moonshine native runtime and bundled English speech assets."""
from pathlib import Path
import hashlib,json,os,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
def digest(path):
 with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def fetch(url,target,expected):
 if target.is_file() and digest(target)==expected:return
 target.parent.mkdir(parents=True,exist_ok=True);partial=target.with_name(target.name+'.part')
 try:
  with urllib.request.urlopen(url,timeout=120) as response,partial.open('wb') as output:
   while block:=response.read(1024*1024):output.write(block)
  if digest(partial)!=expected:raise ValueError('Moonshine checksum mismatch: '+target.name)
  os.replace(partial,target)
 finally:
  if partial.exists():partial.unlink()
def main():
 runtime=json.loads((ROOT/'moonshine-runtime.lock.json').read_text())
 if any(not (ROOT/f['path']).is_file() or digest(ROOT/f['path'])!=f['sha256'] for f in runtime['files']):
  archive=ROOT/'.cache/runtime/moonshine-voice.aar';fetch(runtime['source'],archive,runtime['aar_sha256'])
  with zipfile.ZipFile(archive) as source:
   for entry in runtime['files']:
    target=ROOT/entry['path'];target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(source.read('jni/arm64-v8a/'+target.name))
    if target.stat().st_size!=entry['bytes'] or digest(target)!=entry['sha256']:raise ValueError('Moonshine runtime mismatch')
 models=json.loads((ROOT/'moonshine-models.lock.json').read_text());assets=ROOT/'app/src/main/assets/moonshine';assets.mkdir(parents=True,exist_ok=True)
 for entry in models['files']:
  fetch(entry['url'],assets/entry['name'],entry['sha256'])
  if (assets/entry['name']).stat().st_size!=entry['bytes']:raise ValueError('Moonshine model size mismatch')
 (assets/'manifest.json').write_text(json.dumps(models,indent=2)+'\n')
 print('Pinned Moonshine Streaming Medium runtime and English assets verified',flush=True)
if __name__=='__main__':main()
