#!/usr/bin/env python3
"""Stage one distribution channel. Public packages exclude restricted weights unless rights are recorded."""
import argparse,hashlib,json,os,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def checked(path,size,sha):
 if not path.is_file() or path.stat().st_size!=size:raise SystemExit('Missing or incomplete asset: '+str(path.relative_to(ROOT)))
 with path.open('rb') as r:
  if hashlib.file_digest(r,'sha256').hexdigest()!=sha:raise SystemExit('Asset checksum mismatch: '+str(path.relative_to(ROOT)))
def copy(source,target):
 target.parent.mkdir(parents=True,exist_ok=True)
 if target.exists():target.unlink()
 try:os.link(source,target)
 except OSError:shutil.copy2(source,target)
def approval(config,name):
 a=config[name];e=a.get('evidenceFile','')
 return bool(a.get('approved') and e and Path(e).is_file())
def prepare(channel,with_surya=None,bundle_surya=True):
 config=json.loads((ROOT/'distribution/release.json').read_text());personal=channel=='personal'
 if with_surya is None:with_surya=personal
 if with_surya and not personal and not (approval(config,'suryaRedistribution') and approval(config,'detectorRedistribution')):
  raise SystemExit('Public Surya distribution requires documented rights for BOTH the VLM and the CC-BY-NC-SA detector. See docs/LICENSING.md.')
 assets=ROOT/'app/src/main/assets'
 allowed={}
 for group in ['speech','ocr','moonshine']:
  manifest=json.loads((assets/group/'manifest.json').read_text())
  allowed[group]={'manifest.json'}|{f['name'] for f in manifest['files']}
  for f in manifest['files']:checked(assets/group/f['name'],f['bytes'],f['sha256'])
 if with_surya:
  scanner=json.loads((ROOT/'scanner-runtime.lock.json').read_text())
  if bundle_surya:
   for f in scanner['surya']['models']:checked(ROOT/'.cache/surya'/f['name'],f['bytes'],f['sha256'])
  d=scanner['detector'];checked(assets/'surya'/d['file'],d['bytes'],d['sha256'])
 stage=ROOT/'build/distribution'/channel
 if stage.exists():shutil.rmtree(stage)
 common=stage/'assets';common.mkdir(parents=True)
 for p in sorted(assets.rglob('*')):
  if not p.is_file():continue
  rel=p.relative_to(assets)
  if any(part.startswith('.') for part in rel.parts):continue
  if rel.parts[0] in allowed and rel.name not in allowed[rel.parts[0]]:continue
  if rel.parts[0]=='surya' and not with_surya:continue
  if channel=='play' and rel.parts[0] in ['speech','ocr','moonshine','surya']:continue
  copy(p,common/rel)
 for p in (ROOT/'notices').glob('*.txt'):copy(p,common/'notices'/p.name)
 runtime={'channel':channel,'suryaEnabled':with_surya,'catalogUrl':config['publisher']['catalogUrl'],'reportingUrl':config['publisher']['reportingUrl'],'privacyPolicyUrl':config['publisher']['privacyPolicyUrl'],'supportEmail':config['publisher']['supportEmail']}
 (common/'distribution.json').write_text(json.dumps(runtime,ensure_ascii=False,indent=2)+'\n')
 packs={}
 if channel=='play':
  for pack_name in ['speech_pack','ocr_pack']:
   pack=ROOT/pack_name/'src/main/assets'
   if pack.exists():shutil.rmtree(pack)
  for group,pack_name in [('speech','speech_pack'),('moonshine','speech_pack'),('ocr','ocr_pack')]:
   pack=ROOT/pack_name/'src/main/assets'
   for p in (assets/group).rglob('*'):
    if p.is_file() and p.name in allowed[group]:copy(p,pack/group/p.relative_to(assets/group))
   packs[pack_name]=sum(p.stat().st_size for p in pack.rglob('*') if p.is_file())
 if with_surya and bundle_surya:
  if channel=='play':
   for f,pack_name in zip(scanner['surya']['models'],['surya_pack','surya_vision_pack']):
    pack=ROOT/pack_name/'src/main/assets'
    if pack.exists():shutil.rmtree(pack)
    copy(ROOT/'.cache/surya'/f['name'],pack/'surya/bundled'/f['name'])
   for p in (assets/'surya').glob('*'):
    if p.is_file():copy(p,ROOT/'surya_vision_pack/src/main/assets/surya'/p.name)
   packs.update({name:sum(p.stat().st_size for p in (ROOT/name/'src/main/assets').rglob('*') if p.is_file()) for name in ['surya_pack','surya_vision_pack']})
  else:
   for f in scanner['surya']['models']:copy(ROOT/'.cache/surya'/f['name'],common/'surya/bundled'/f['name'])
 report={'channel':channel,'surya':with_surya,'suryaBundled':bool(with_surya and bundle_surya),'assetBytes':sum(p.stat().st_size for p in common.rglob('*') if p.is_file()),'packs':packs}
 (stage/'manifest.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report),flush=True);return stage
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--channel',choices=['personal','community','play'],default='community');p.add_argument('--with-surya',action='store_true',default=None);p.add_argument('--no-bundled-surya',action='store_true',help='Keep permitted Surya support, with its large VLM weights downloaded separately.');a=p.parse_args();prepare(a.channel,a.with_surya,not a.no_bundled_surya)
if __name__=='__main__':main()
