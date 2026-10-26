#!/usr/bin/env python3
"""Restore reviewed runtime dependencies from lock files. Native llama binaries are built separately."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
def digest(p):
 with p.open('rb') as r:return hashlib.file_digest(r,'sha256').hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--personal',action='store_true');p.add_argument('--channel',choices=['community','play','personal'],default='community');a=p.parse_args()
 lock=json.loads((ROOT/'speech-runtime.lock.json').read_text());cache=ROOT/'.cache/runtime';cache.mkdir(parents=True,exist_ok=True)
 if any(not (ROOT/f['path']).is_file() or digest(ROOT/f['path'])!=f['sha256'] for f in lock['files']):
  archive=cache/'onnxruntime.aar'
  if not archive.is_file() or digest(archive)!=lock['aar_sha256']:
   with urllib.request.urlopen(lock['source'],timeout=90) as r,archive.open('wb') as out:
    while b:=r.read(1024*1024):out.write(b)
   if digest(archive)!=lock['aar_sha256']:raise ValueError('ONNX Runtime checksum mismatch')
  with zipfile.ZipFile(archive) as z:
   for f in lock['files']:
    dest=ROOT/f['path'];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read('classes.jar' if dest.suffix=='.jar' else 'jni/arm64-v8a/'+dest.name))
    if dest.stat().st_size!=f['bytes'] or digest(dest)!=f['sha256']:raise ValueError('ONNX Runtime extracted file mismatch')
 for script,args in [('prepare-security.py',[]),('prepare-ocr.py',[]),('prepare-speech.py',[]),('prepare-moonshine.py',[]),('prepare-diarization.py',[]),('prepare-scanner.py',[]),('prepare-video.py',[]),('prepare-search.py',[])]:
  subprocess.run([sys.executable,str(ROOT/'scripts'/script),*args],cwd=ROOT,check=True)
 print('Runtime restored. Build llama with scripts/build-native.sh cpu before Gradle.')
if __name__=='__main__':main()
