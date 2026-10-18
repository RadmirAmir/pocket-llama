#!/usr/bin/env python3
"""Verify/restore pinned scanner runtime.

The GGUF/mmproj are intentionally downloaded by the Android app, after an explicit
setup action. No document, camera image, or user data is sent to a model service.
"""
from pathlib import Path
import argparse,hashlib,json,os,shutil,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
def digest(p):
 with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def download(url,path,size,sha):
 path.parent.mkdir(parents=True,exist_ok=True)
 if path.is_file() and path.stat().st_size==size and digest(path)==sha:return
 part=path.with_suffix(path.suffix+'.part');total=0
 with urllib.request.urlopen(url,timeout=60) as source,part.open('wb') as out:
  while block:=source.read(1024*1024):
   total+=len(block)
   if total>size:raise ValueError('Oversized download: '+path.name)
   out.write(block)
 if total!=size or digest(part)!=sha:raise ValueError('Incomplete download or checksum mismatch: '+path.name+'; retry')
 os.replace(part,path)
def main():
 argparse.ArgumentParser(description=__doc__).parse_args();lock=json.loads((ROOT/'scanner-runtime.lock.json').read_text());cache=ROOT/'build/scanner-download'
 for artifact in lock['artifacts']:
  missing=any(not (ROOT/f['path']).is_file() or digest(ROOT/f['path'])!=f['sha256'] for f in artifact['files'])
  if not missing:continue
  url=artifact['source'];path=cache/url.rsplit('/',1)[-1];size=artifact.get('bytes',sum(f['bytes'] for f in artifact['files']));download(url,path,size,artifact['sha256'])
  if path.suffix=='.aar':
   with zipfile.ZipFile(path) as z:
    for f in artifact['files']:
     dest=ROOT/f['path'];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read('classes.jar' if dest.suffix=='.jar' else 'jni/arm64-v8a/'+dest.name))
  else:shutil.copyfile(path,ROOT/artifact['files'][0]['path'])
 for f in lock['files']+[f for a in lock['artifacts'] for f in a['files']]:
  path=ROOT/f['path']
  if not path.is_file() or path.stat().st_size!=f['bytes'] or digest(path)!=f['sha256']:raise SystemExit('Scanner runtime mismatch: '+f['path']+'; restore the pinned dependency')
 print('Pinned scanner assets and runtime verified')
if __name__=='__main__':main()
