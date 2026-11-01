#!/usr/bin/env python3
"""Prepare a signed, resource-only update to the verified Nemotron APK.

The exact installed runtime, models and application identity are retained.
The signing key remains local and is never put in the APK or repository.
"""
import argparse,hashlib,json,os,re,shutil,struct,subprocess,sys,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE_SHA='7d608ce19432febbd4e60f4a5596f6a1aa65710f81b60951bcab65356cc0e13e'
CERT_SHA='ef3cea40110c13d179cc7e1f1e570aeb2f870cb7d46f16273eb5d733cf9b2ed0'
ASSETS=['design.css','studio.css','studio.html','chat.css','photos.css','notes.css']
OLD_NAME='0.14.0-nemotron.1'
NEW_NAME='0.14.1-nemotron.1'

def digest(path):
 with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def stream_digest(stream):
 h=hashlib.sha256()
 while data:=stream.read(1024*1024):h.update(data)
 return h.hexdigest()

def patch_manifest(raw):
 data=bytearray(raw);pool=8;kind,header,size=struct.unpack_from('<HHI',data,pool)
 if kind!=1:raise ValueError('Invalid Android string pool')
 count,styles,flags,strings_start,styles_start=struct.unpack_from('<IIIII',data,pool+8)
 if flags&0x100:raise ValueError('Expected UTF-16 string pool')
 offsets=struct.unpack_from('<'+'I'*count,data,pool+header)
 def get(index):
  if index>=count:raise ValueError('String pool index outside bounds')
  pos=pool+strings_start+offsets[index];length=struct.unpack_from('<H',data,pos)[0]
  if length&0x8000:raise ValueError('Long string not supported')
  return bytes(data[pos+2:pos+2+2*length]).decode('utf-16le')
 strings=[get(i) for i in range(count)]
 indexes=[i for i,s in enumerate(strings) if s==OLD_NAME]
 if len(indexes)!=1 or len(OLD_NAME)!=len(NEW_NAME):raise ValueError('Unexpected version-name string')
 i=indexes[0];offset=pool+strings_start+offsets[i]+2
 data[offset:offset+2*len(OLD_NAME)]=NEW_NAME.encode('utf-16le')
 position=pool+size;changed=0;version_name_ok=False;package_ok=False
 while position<len(data):
  typ,head,length=struct.unpack_from('<HHI',data,position)
  if length<head or length<=0 or position+length>len(data):raise ValueError('Invalid manifest chunk')
  if typ==0x102:
   ns,name,attr_start,attr_size,attr_count,*_=struct.unpack_from('<IIHHHHHH',data,position+16)
   if strings[name]=='manifest':
    for j in range(attr_count):
     at=position+16+attr_start+j*attr_size
     _,name_id,raw_id,val_size,zero,val_type,value=struct.unpack_from('<IIIHBBI',data,at)
     n=strings[name_id]
     if n=='versionCode':
      if val_type!=0x10 or value!=38:raise ValueError('Unexpected base version code')
      struct.pack_into('<I',data,at+16,39);changed+=1
     if n=='versionName':version_name_ok=(val_type==3 and value==indexes[0])
     if n=='package':package_ok=(val_type==3 and strings[value]=='dev.pocketllama')
  position+=length
 if changed!=1 or not version_name_ok or not package_ok:raise ValueError('Unexpected manifest attributes')
 return bytes(data)

def main():
 p=argparse.ArgumentParser();p.add_argument('--baseline',required=True);p.add_argument('--out',required=True);p.add_argument('--apksigner',required=True);p.add_argument('--zipalign',required=True);p.add_argument('--keystore',required=True);a=p.parse_args()
 if not os.environ.get('POCKET_STORE_PASSWORD') or not os.environ.get('POCKET_KEY_PASSWORD'):raise ValueError('Signing passwords must be provided via environment')
 baseline=Path(a.baseline);out=Path(a.out);unsigned=out.with_name('unsigned-ui-update.apk')
 if digest(baseline)!=BASE_SHA:raise ValueError('Unexpected baseline APK')
 release=json.loads((ROOT/'distribution/release.json').read_text())
 if (release['versionName'],release['versionCode'],release['applicationId'])!=(NEW_NAME,39,'dev.pocketllama'):raise ValueError('Source version does not match update')
 allowed={f'app/src/main/assets/{n}' for n in ASSETS}
 diff=set(subprocess.check_output(['git','diff','--name-only','v0.14.0-nemotron.1','--','app','native'],cwd=ROOT,text=True).splitlines())
 if diff!=allowed:raise ValueError('Unexpected application source changes: '+repr(sorted(diff^allowed)))
 with zipfile.ZipFile(baseline) as z:
  replacements={}
  for name in ASSETS:
   path='assets/'+name;original=z.read(path)
   release_source=subprocess.check_output(['git','show','v0.14.0-nemotron.1:app/src/main/assets/'+name],cwd=ROOT)
   if original!=release_source:raise ValueError('Baseline asset differs from release source: '+name)
   replacements[path]=(ROOT/'app/src/main/assets'/name).read_bytes()
  replacements['AndroidManifest.xml']=patch_manifest(z.read('AndroidManifest.xml'))
 shutil.copyfile(baseline,unsigned)
 with zipfile.ZipFile(unsigned,'a') as z:
  originals={i.filename:i for i in z.infolist()}
  def keep(i):return i.filename not in replacements and not i.filename.upper().startswith('META-INF/')
  z.filelist=[i for i in z.filelist if keep(i)]
  z.NameToInfo={i.filename:i for i in z.filelist}
  for name,content in replacements.items():
   previous=originals[name];info=zipfile.ZipInfo(name,previous.date_time);info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=previous.external_attr;info.create_system=previous.create_system
   z.writestr(info,content,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 subprocess.run(['java','-jar',a.apksigner,'sign','--ks',a.keystore,'--ks-key-alias','androiddebugkey','--ks-pass','env:POCKET_STORE_PASSWORD','--key-pass','env:POCKET_KEY_PASSWORD','--out',str(out),str(unsigned)],check=True)
 verify=subprocess.check_output(['java','-jar',a.apksigner,'verify','--verbose','--print-certs',str(out)],text=True,stderr=subprocess.STDOUT)
 match=re.search(r'Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]+)',verify)
 if not match or match.group(1).lower()!=CERT_SHA:raise ValueError('Candidate certificate does not match established community signing certificate')
 subprocess.run([a.zipalign,'-c','-P','16','4',str(out)],check=True)
 with zipfile.ZipFile(baseline) as before,zipfile.ZipFile(out) as after:
  previous={i.filename:i for i in before.infolist() if not i.filename.upper().startswith('META-INF/')}
  current={i.filename:i for i in after.infolist() if not i.filename.upper().startswith('META-INF/')}
  if set(previous)!=set(current):raise ValueError('APK inventory changed unexpectedly')
  for name in previous:
   if name in replacements:
    if after.read(name)!=replacements[name]:raise ValueError('Updated entry mismatch '+name)
    continue
   with before.open(name) as a1,after.open(name) as a2:
    if stream_digest(a1)!=stream_digest(a2):raise ValueError('Runtime or model changed '+name)
 print(json.dumps({'apk':str(out),'bytes':out.stat().st_size,'sha256':digest(out),'certificateSha256':CERT_SHA,'unchangedEntries':len(previous)-len(replacements),'updatedEntries':sorted(replacements)},indent=2))

if __name__=='__main__':main()
