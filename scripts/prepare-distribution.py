#!/usr/bin/env python3
"""Stage a distribution channel, preserving the free community model terms."""
import argparse,hashlib,json,os,shutil,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import video_bundle
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
def free_community(config,channel):
 a=config.get('communitySurya',{})
 return bool(channel=='community' and a.get('enabled') is True and a.get('distribution')=='free-personal-use' and a.get('monetized') is False and a.get('noticeFile')=='notices/Surya-community.txt' and (ROOT/a['noticeFile']).is_file())
def surya_policy(config,channel,with_surya=None,bundle_surya=None):
 personal=channel=='personal';community=free_community(config,channel)
 if with_surya is None:with_surya=personal or community
 if bundle_surya is None:bundle_surya=personal or channel=='play'
 if with_surya and not personal:
  licensed=approval(config,'suryaRedistribution')
  if not (community or licensed):raise SystemExit('Surya requires the free personal-use community profile or documented rights for the Surya VLM. See docs/LICENSING.md.')
  if community and bundle_surya and not licensed:raise SystemExit('Free community downloads the VLM directly after accepting its terms; use the compact package.')
 return bool(with_surya),bool(with_surya and bundle_surya)
def assert_scanner_assets(common,bundle_surya,scanner):
 expected={'bundled/'+f['name'] for f in scanner['surya']['models']} if bundle_surya else set()
 actual={p.relative_to(common/'surya').as_posix() for p in (common/'surya').rglob('*') if p.is_file() and not any(part.startswith('.') for part in p.relative_to(common/'surya').parts)}
 if actual!=expected or any((common/'notices').glob('Surya-detector-*')):
  raise SystemExit('Unexpected retired/stale scanner assets in staging; remove the build directory and retry.')
def prepare(channel,with_surya=None,bundle_surya=None):
 config=json.loads((ROOT/'distribution/release.json').read_text());personal=channel=='personal'
 with_surya,bundle_surya=surya_policy(config,channel,with_surya,bundle_surya)
 assets=ROOT/'app/src/main/assets'
 video=video_bundle.inventory(ROOT)
 allowed={'video':set(video_bundle.ASSETS)}
 for group in ['speech','ocr','moonshine']:
  manifest=json.loads((assets/group/'manifest.json').read_text())
  allowed[group]={'manifest.json'}|{f['name'] for f in manifest['files']}
  for f in manifest['files']:checked(assets/group/f['name'],f['bytes'],f['sha256'])
 if with_surya:
  scanner=json.loads((ROOT/'scanner-runtime.lock.json').read_text())
  if bundle_surya:
   for f in scanner['surya']['models']:checked(ROOT/'.cache/surya'/f['name'],f['bytes'],f['sha256'])
 stage=ROOT/'build/distribution'/channel
 if stage.exists():shutil.rmtree(stage)
 common=stage/'assets';common.mkdir(parents=True)
 for p in sorted(assets.rglob('*')):
  if not p.is_file():continue
  rel=p.relative_to(assets)
  if any(part.startswith('.') for part in rel.parts):continue
  if rel.parts[0] in allowed and rel.name not in allowed[rel.parts[0]]:continue
  if rel.parts[0]=='surya':continue  # Only explicitly staged GGUF files may be bundled.
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
   packs.update({name:sum(p.stat().st_size for p in (ROOT/name/'src/main/assets').rglob('*') if p.is_file()) for name in ['surya_pack','surya_vision_pack']})
  else:
   for f in scanner['surya']['models']:copy(ROOT/'.cache/surya'/f['name'],common/'surya/bundled'/f['name'])
 video_bundle.validate_assets(common,video)
 assert_scanner_assets(common,bundle_surya and channel!='play',scanner if with_surya else {})
 report={'channel':channel,'surya':with_surya,'suryaBundled':bool(with_surya and bundle_surya),'assetBytes':sum(p.stat().st_size for p in common.rglob('*') if p.is_file()),'packs':packs}
 (stage/'manifest.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report),flush=True);return stage
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--channel',choices=['personal','community','play'],default='community');g=p.add_mutually_exclusive_group();g.add_argument('--with-surya',action='store_true',dest='with_surya',default=None);g.add_argument('--without-surya',action='store_false',dest='with_surya');p.add_argument('--no-bundled-surya',action='store_true',help='Download the large VLM weights separately after accepting model terms.');a=p.parse_args();prepare(a.channel,a.with_surya,False if a.no_bundled_surya else None)
if __name__=='__main__':main()
