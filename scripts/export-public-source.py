#!/usr/bin/env python3
"""Export a reviewed source-only repository; never includes signing keys, weights or build products."""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[1]
DIRS={'app','native','scripts','tests','tools','notices','model-configs','gradle','distribution','docs','.github','speech_pack','ocr_pack','surya_pack','surya_vision_pack'}
FILES={'.gitignore','LICENSE','NOTICE','README.md','README-RU.md','CHANGELOG.md','SECURITY.md','CONTRIBUTING.md','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','requirements-build.txt'}
DENY={'.git','.cache','.gradle','build','artifacts','vendor','node_modules','__pycache__','signing'}
def selected():
 for p in sorted(ROOT.rglob('*')):
  if not p.is_file():continue
  rel=p.relative_to(ROOT)
  if any(x in DENY for x in rel.parts):continue
  if rel.as_posix().startswith('docs/internal/'):continue
  if any(x.startswith('.') for x in rel.parts) and rel.parts[0]!='.github' and rel.as_posix()!='.gitignore':continue
  if rel.parts[0] not in DIRS and rel.as_posix() not in FILES and not (len(rel.parts)==1 and (p.name.endswith('.lock.json') or p.name=='llama.lock')):continue
  if p.suffix.lower() in {'.so','.onnx','.ort','.gguf','.apk','.aab','.apks','.zip','.part','.pem','.jks','.keystore','.p12','.pfx','.pyc'}:continue
  if rel.as_posix().startswith('app/src/main/assets/moonshine/') and rel.name!='manifest.json':continue
  if p.suffix=='.jar' and rel.as_posix()!='gradle/wrapper/gradle-wrapper.jar':continue
  if rel.as_posix().startswith('app/src/main/assets/video/') and rel.name!='launcher.py':continue
  if rel.as_posix().startswith('app/src/main/assets/search/') and rel.name!='server.py':continue
  if len(rel.parts)>2 and rel.parts[0].endswith('_pack') and 'assets' in rel.parts:continue
  if p.is_symlink():raise SystemExit('Review source symlink: '+str(rel))
  if p.stat().st_size>8*1024*1024:raise SystemExit('Unexpected large source file: '+str(rel))
  data=p.read_bytes()
  if re.search(rb'^-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----',data,re.M):raise SystemExit('Private key in '+str(rel))
  yield p,rel
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);a=p.parse_args();target=Path(a.output).resolve();target.parent.mkdir(parents=True,exist_ok=True)
 files=list(selected())
 with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for source,rel in files:z.write(source,'pocket-llama/'+rel.as_posix())
 with zipfile.ZipFile(target) as z:
  if z.testzip():raise SystemExit('Source archive CRC failed')
 print(json.dumps({'files':len(files),'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}))
if __name__=='__main__':main()
