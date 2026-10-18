#!/usr/bin/env python3
"""Review source packaging and require concrete publisher settings for a production upload."""
from pathlib import Path
import argparse,importlib.util,json,os,re,sys
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--production',action='store_true');a=p.parse_args();fail=[]
 spec=importlib.util.spec_from_file_location('export_source',ROOT/'scripts/export-public-source.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);files=list(module.selected())
 config=json.loads((ROOT/'distribution/release.json').read_text());manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text();gradle=(ROOT/'app/build.gradle.kts').read_text()
 for pattern in ['compileSdk = 36','targetSdk = 36']:
  if pattern not in gradle:fail.append('API36 configuration missing')
 if 'FOREGROUND_SERVICE_DATA_SYNC' not in manifest:fail.append('Model transfer service declaration missing')
 if a.production:
  publisher=config['publisher']
  if not publisher['name'].strip():fail.append('Set publisher.name')
  if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+',publisher['supportEmail']):fail.append('Set a support email')
  for key in ['privacyPolicyUrl','reportingUrl']:
   if not publisher[key].startswith('https://') or any(x in publisher[key] for x in ['example.com','localhost','127.0.0.1','<','>']):fail.append('Set a live HTTPS '+key)
  if not os.environ.get('POCKET_KEYSTORE') or not Path(os.environ['POCKET_KEYSTORE']).is_file():fail.append('Provide the production keystore outside the repository')
  if os.environ.get('POCKET_KEY_ALIAS','androiddebugkey')=='androiddebugkey':fail.append('Use a production signing identity')
  if not all(os.environ.get(k) for k in ['POCKET_STORE_PASSWORD','POCKET_KEY_PASSWORD']):fail.append('Provide signing passwords via environment secrets')
  evidence=ROOT/'distribution/device-validation.json'
  if not evidence.is_file() or not json.loads(evidence.read_text()).get('approved'):fail.append('Complete real-device acceptance in distribution/device-validation.json')
 print('Source package reviewed:',len(files),'files; signing keys and model weights excluded.')
 if fail:
  print('\n'.join('REQUIRED: '+f for f in fail));return 1
 print('Production prerequisites present.' if a.production else 'Source and build profile checks passed. Use --production before upload.');return 0
if __name__=='__main__':sys.exit(main())
