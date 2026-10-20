#!/usr/bin/env python3
"""Verify the actual deliverables, model bytes, channels and legacy update certificate."""
from pathlib import Path
import argparse,hashlib,json,re,struct,subprocess,zipfile,sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
import video_bundle
import search_bundle
ROOT=Path(__file__).resolve().parents[1]
def digest(stream):
 h=hashlib.sha256()
 while b:=stream.read(1024*1024):h.update(b)
 return h.hexdigest()
def apk_resources(archive):
 """Apps targeting Android 11+ need an uncompressed, aligned resource table."""
 info=archive.getinfo('resources.arsc')
 assert info.compress_type==zipfile.ZIP_STORED,'Compressed resources.arsc: Android cannot install this APK'
 with open(archive.filename,'rb') as stream:
  stream.seek(info.header_offset+26)
  name_size,extra_size=struct.unpack('<HH',stream.read(4))
 assert (info.header_offset+30+name_size+extra_size)%4==0,'Unaligned resources.arsc'
def models(archive,prefix,personal,personal_compact=False):
 with zipfile.ZipFile(archive) as z:
  names=z.namelist();assert len(names)==len(set(names)),'Duplicate archive entry'
  if prefix=='apk':apk_resources(z)
  video_count=video_bundle.validate_archive(z,video_bundle.inventory(ROOT),'' if prefix=='apk' else 'base/')
  search_count=search_bundle.validate_archive(z,search_bundle.inventory(ROOT),'' if prefix=='apk' else 'base/')
  meta=json.loads(z.read(('' if prefix=='apk' else 'base/')+'assets/distribution.json'))
  config=json.loads((ROOT/'distribution/release.json').read_text())
  community=config.get('communitySurya',{})
  with_surya=personal or (prefix=='apk' and meta['channel']=='community' and community.get('enabled') is True and community.get('monetized') is False and community.get('distribution')=='free-personal-use')
  assert not any('line-detector' in n or '/notices/Surya-detector-' in n for n in names),'Retired scanner model in artifact'
  if prefix=='apk':
   assert not any(b'Ldev/pocketllama/photo/SuryaDetector;' in z.read(n) for n in names if n.endswith('.dex')),'Retired detector class in DEX'
  expected=[]
  for group in ['speech','moonshine','ocr']:
   module='' if prefix=='apk' else ('speech_pack/' if group=='moonshine' else group+'_pack/')
   for item in json.loads((ROOT/'app/src/main/assets'/group/'manifest.json').read_text())['files']:
    expected.append((module+'assets/'+group+'/'+item['name'],item['bytes'],item['sha256']))
  if with_surya:
   scanner=json.loads((ROOT/'scanner-runtime.lock.json').read_text())
   if personal_compact or not personal:assert not any(n.startswith('assets/surya/bundled/') for n in names),'Unexpected bundled Surya in compact update'
   else:
    for item in scanner['surya']['models']:expected.append(('assets/surya/bundled/'+item['name'],item['bytes'],item['sha256']))
  else:assert not any(n.startswith('assets/surya/') or n.startswith('surya_pack/') or n.startswith('surya_vision_pack/') for n in names),'Surya in a disabled channel'
  for name,size,sha in expected:
   assert names.count(name)==1 and z.getinfo(name).file_size==size,name
   with z.open(name) as source:assert digest(source)==sha,'Model checksum: '+name
  assert meta['suryaEnabled']==with_surya
  for asset in ['notes.html','notes.css','notes.js','theme.js','design.css','studio.html','studio.css','studio.js','photos.js','photos.html','photos.css','chat.html','chat.css','rag/model.json','i18n.js','i18n-en.json','model-catalog/catalog.signed.json','model-catalog/public-key.txt']:
   assert z.read(('' if prefix=='apk' else 'base/')+'assets/'+asset)==(ROOT/'app/src/main/assets'/asset).read_bytes(),'Stale app asset: '+asset
  for name in ['Gemma.txt','Gemma-terms.txt','Gemma-prohibited-uses.txt']:
   assert z.read(('' if prefix=='apk' else 'base/')+'assets/notices/'+name)==(ROOT/'notices'/name).read_bytes(),'Missing embedding terms: '+name
  assert not any(n.startswith(('assets/rag/','base/assets/rag/')) and n.endswith('.gguf') for n in names),'Embedding weights must remain a separate download'
  if with_surya:
   for name in ['Surya-community.txt','Surya-weights.txt']:
    assert z.read(('' if prefix=='apk' else 'base/')+'assets/notices/'+name)==(ROOT/'notices'/name).read_bytes(),'Missing or stale model terms: '+name
  return {'modelsVerified':len(expected),'videoFilesVerified':video_count,'searchFilesVerified':search_count,'channel':meta['channel'],'suryaBundled':bool(personal and not personal_compact),'bytes':Path(archive).stat().st_size}
def certificate(java,signer,path):
 result=subprocess.check_output([java,'-jar',str(signer),'verify','--print-certs',str(path)],text=True)
 return re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)',result).group(1).lower()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--old-apk',required=True);p.add_argument('--sdk',required=True);p.add_argument('--jdk',required=True);p.add_argument('--personal-compact',action='store_true');p.add_argument('--community-only',action='store_true');a=p.parse_args();v=json.loads((ROOT/'distribution/release.json').read_text())['versionName'];out=ROOT/'artifacts'
 personal=out/('PocketLlama-'+v+('-personal-update-arm64.apk' if a.personal_compact else '-personal-arm64.apk'));community=out/('PocketLlama-'+v+'-community-arm64.apk');bundle=out/('PocketLlama-'+v+'-play-unsigned.aab')
 report={'community':models(community,'apk',False)} if a.community_only else {'personal':models(personal,'apk',True,a.personal_compact),'community':models(community,'apk',False),'play':models(bundle,'bundle',False)}
 signer=Path(a.sdk)/'build-tools/36.0.0/lib/apksigner.jar';java=str(Path(a.jdk)/'bin/java');old=certificate(java,signer,Path(a.old_apk));new=certificate(java,signer,community if a.community_only else personal);assert old==new,'Legacy update certificate changed'
 report['legacyCertificateMatches']=True;report['legacyCertificateSha256']=new
 target=ROOT/('docs/artifact-validation-'+v+'.json');target.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
