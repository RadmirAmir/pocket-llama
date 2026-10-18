#!/usr/bin/env python3
"""Build/validate an AAB from Gradle's module ZIPs using a pinned official bundletool.

Exact ZIP paths avoid assuming that intermediate folders contain no temporary
files. Model packs remain standard install-time Play AI packs.
"""
from pathlib import Path
import argparse,json,os,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--bundletool',required=True);p.add_argument('--output');p.add_argument('--sign',action='store_true');a=p.parse_args()
 config=json.loads((ROOT/'distribution/release.json').read_text());stage=json.loads((ROOT/'build/distribution/play/manifest.json').read_text())
 if stage['channel']!='play':raise SystemExit('Prepare the play distribution first')
 packs=['speech_pack','ocr_pack']+(['surya_pack','surya_vision_pack'] if stage['surya'] else [])
 modules=[ROOT/'app/build/intermediates/module_bundle/release/buildReleasePreBundle/base.zip']+[ROOT/'app/build/intermediates/asset_pack_bundle/release/assetPackReleasePreBundleTask'/name/(name+'.zip') for name in packs]
 seen=set()
 for index,module in enumerate(modules):
  if not module.is_file():raise SystemExit('Build the Gradle pre-bundle tasks first: '+str(module))
  with zipfile.ZipFile(module) as z:
   for name in z.namelist():
    if name.startswith('assets/'):
     if name in seen:raise SystemExit('Asset appears in multiple modules: '+name)
     seen.add(name)
    if index>0 and (name.startswith('lib/') or name.endswith(('.dex','.so','.jar'))):raise SystemExit('Executable code is not permitted in AI packs')
 required=['assets/speech/gigaam.int8.onnx','assets/ocr/rec.onnx','assets/moonshine/manifest.json','assets/moonshine/encoder.ort','assets/moonshine/decoder_kv.ort','assets/moonshine/decoder_kv_with_attention.ort','assets/moonshine/tokenizer.bin']
 if any(name not in seen for name in required):raise SystemExit('Required RU / EN speech or OCR install-time models missing')
 if not stage['surya'] and any(n.startswith('assets/surya/') for n in seen):raise SystemExit('Restricted OCR weights in public profile')
 output=Path(a.output or ROOT/'artifacts'/('PocketLlama-'+config['versionName']+('-play.aab' if a.sign else '-play-unsigned.aab'))).resolve();output.parent.mkdir(parents=True,exist_ok=True)
 work=ROOT/'build/play-bundle';work.mkdir(parents=True,exist_ok=True);unsigned=work/'unsigned.aab'
 java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
 tool=Path(a.bundletool).resolve()
 subprocess.run([java,'-jar',str(tool),'build-bundle','--modules='+','.join(str(x) for x in modules),'--output='+str(unsigned),'--overwrite'],check=True)
 subprocess.run([java,'-jar',str(tool),'validate','--bundle='+str(unsigned)],check=True)
 staged=unsigned
 if a.sign:
  required=['POCKET_KEYSTORE','POCKET_KEY_ALIAS','POCKET_STORE_PASSWORD','POCKET_KEY_PASSWORD']
  if not all(os.environ.get(x) for x in required):raise SystemExit('Provide production signing variables; see docs/RELEASE.md')
  signer=str(Path(os.environ['JAVA_HOME'])/'bin/jarsigner') if os.environ.get('JAVA_HOME') else 'jarsigner'
  staged=work/'signed.aab'
  subprocess.run([signer,'-keystore',os.environ['POCKET_KEYSTORE'],'-storepass:env','POCKET_STORE_PASSWORD','-keypass:env','POCKET_KEY_PASSWORD','-signedjar',str(staged),str(unsigned),os.environ['POCKET_KEY_ALIAS']],check=True)
  subprocess.run([signer,'-verify',str(staged)],check=True)
 shutil.copyfile(staged,output);print('Built:',output,output.stat().st_size)
if __name__=='__main__':main()
