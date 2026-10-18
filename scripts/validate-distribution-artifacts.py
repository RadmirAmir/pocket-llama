#!/usr/bin/env python3
"""Verify the actual deliverables, model bytes, channels and legacy update certificate."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
def digest(stream):
 h=hashlib.sha256()
 while b:=stream.read(1024*1024):h.update(b)
 return h.hexdigest()
def models(archive,prefix,personal,personal_compact=False):
 with zipfile.ZipFile(archive) as z:
  names=z.namelist();assert len(names)==len(set(names)),'Duplicate archive entry'
  expected=[]
  for group in ['speech','moonshine','ocr']:
   module='' if prefix=='apk' else ('speech_pack/' if group=='moonshine' else group+'_pack/')
   for item in json.loads((ROOT/'app/src/main/assets'/group/'manifest.json').read_text())['files']:
    expected.append((module+'assets/'+group+'/'+item['name'],item['bytes'],item['sha256']))
  if personal:
   scanner=json.loads((ROOT/'scanner-runtime.lock.json').read_text())
   if personal_compact:assert not any(n.startswith('assets/surya/bundled/') for n in names),'Unexpected bundled Surya in compact update'
   else:
    for item in scanner['surya']['models']:expected.append(('assets/surya/bundled/'+item['name'],item['bytes'],item['sha256']))
   d=scanner['detector'];expected.append(('assets/surya/'+d['file'],d['bytes'],d['sha256']))
  else:assert not any(n.startswith('assets/surya/') or n.startswith('surya_pack/') or n.startswith('surya_vision_pack/') for n in names),'Restricted model in public channel'
  for name,size,sha in expected:
   assert names.count(name)==1 and z.getinfo(name).file_size==size,name
   with z.open(name) as source:assert digest(source)==sha,'Model checksum: '+name
  meta=json.loads(z.read(('' if prefix=='apk' else 'base/')+'assets/distribution.json'))
  assert meta['suryaEnabled']==personal
  for asset in ['studio.js','photos.js','chat.html','i18n.js','i18n-en.json','model-catalog/catalog.signed.json','model-catalog/public-key.txt']:
   assert z.read(('' if prefix=='apk' else 'base/')+'assets/'+asset)==(ROOT/'app/src/main/assets'/asset).read_bytes(),'Stale app asset: '+asset
  return {'modelsVerified':len(expected),'channel':meta['channel'],'suryaBundled':bool(personal and not personal_compact),'bytes':Path(archive).stat().st_size}
def certificate(java,signer,path):
 result=subprocess.check_output([java,'-jar',str(signer),'verify','--print-certs',str(path)],text=True)
 return re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)',result).group(1).lower()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--old-apk',required=True);p.add_argument('--sdk',required=True);p.add_argument('--jdk',required=True);p.add_argument('--personal-compact',action='store_true');a=p.parse_args();v=json.loads((ROOT/'distribution/release.json').read_text())['versionName'];out=ROOT/'artifacts'
 personal=out/('PocketLlama-'+v+('-personal-update-arm64.apk' if a.personal_compact else '-personal-arm64.apk'));community=out/('PocketLlama-'+v+'-community-arm64.apk');bundle=out/('PocketLlama-'+v+'-play-unsigned.aab')
 report={'personal':models(personal,'apk',True,a.personal_compact),'community':models(community,'apk',False),'play':models(bundle,'bundle',False)}
 signer=Path(a.sdk)/'build-tools/36.0.0/lib/apksigner.jar';java=str(Path(a.jdk)/'bin/java');old=certificate(java,signer,Path(a.old_apk));new=certificate(java,signer,personal);assert old==new,'Legacy update certificate changed'
 report['legacyCertificateMatches']=True;report['legacyCertificateSha256']=new
 target=ROOT/('docs/artifact-validation-'+v+'.json');target.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
