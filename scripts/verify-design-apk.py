#!/usr/bin/env python3
"""Check a reconstructed design update against its unchanged signed baseline."""
import argparse,hashlib,importlib.util,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('design_build',ROOT/'scripts/build-design-apk.py')
build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)

def digest(stream):
 h=hashlib.sha256()
 while block:=stream.read(1024*1024):h.update(block)
 return h.digest()

def verify(baseline,candidate):
 if build.digest(baseline)!=build.BASE_SHA:raise ValueError('Baseline APK does not match the released version')
 with zipfile.ZipFile(baseline) as before,zipfile.ZipFile(candidate) as after:
  def inventory(z):
   entries=[i.filename for i in z.infolist() if not i.filename.upper().startswith('META-INF/')]
   if len(entries)!=len(set(entries)):raise ValueError('Duplicate APK entry')
   return set(entries)
  names=inventory(before)
  if names!=inventory(after):raise ValueError('APK inventory changed')
  changed={'assets/'+n for n in build.ASSETS}|{'AndroidManifest.xml'}
  if not changed<=names:raise ValueError('Missing release assets')
  for name in sorted(names):
   if name=='AndroidManifest.xml':
    if after.read(name)!=build.patch_manifest(before.read(name)):raise ValueError('Unexpected manifest change')
   elif name in changed:
    if after.read(name)!=(ROOT/'app/src/main'/name).read_bytes():raise ValueError('Asset does not match source: '+name)
   else:
    with before.open(name) as a,after.open(name) as b:
     if digest(a)!=digest(b):raise ValueError('Unrelated model, runtime, or app entry changed: '+name)
 print('PASS: release assets match source; all',len(names)-len(changed),'other APK entries match the previous release')

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline',required=True,type=Path);p.add_argument('--candidate',required=True,type=Path);a=p.parse_args();verify(a.baseline,a.candidate)
