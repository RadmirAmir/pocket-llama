#!/usr/bin/env python3
"""Prepare the pinned optional built-in Surya package; never downloads private documents."""
import argparse,hashlib,json,os,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--accept-model-license',action='store_true');a=p.parse_args()
 if not a.accept_model_license:p.error('Read notices/Surya-weights.txt. Pass --accept-model-license only for a permitted use; this does not grant redistribution rights.')
 lock=json.loads((ROOT/'scanner-runtime.lock.json').read_text())['surya'];dest=ROOT/'.cache/surya';dest.mkdir(parents=True,exist_ok=True)
 for f in lock['models']:
  target=dest/f['name'];part=target.with_suffix('.part')
  def valid(x):
   if not x.is_file() or x.stat().st_size!=f['bytes']:return False
   with x.open('rb') as r:return hashlib.file_digest(r,'sha256').hexdigest()==f['sha256']
  if valid(target):print('Verified',f['name'],flush=True);continue
  url='https://huggingface.co/'+lock['repository']+'/resolve/'+lock['revision']+'/'+f['name']
  print('Preparing',f['name'],flush=True)
  with urllib.request.urlopen(url,timeout=120) as r,part.open('wb') as o:
   total=0
   while b:=r.read(1024*1024):
    total+=len(b)
    if total>f['bytes']:raise ValueError('Oversized model')
    o.write(b)
  if not valid(part):raise ValueError('Model size or SHA-256 mismatch: '+f['name'])
  os.replace(part,target);print('Verified',f['name'],flush=True)
if __name__=='__main__':main()
