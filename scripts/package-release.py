#!/usr/bin/env python3
"""Package a public source export and optionally a private rebuild snapshot. Keys are always excluded."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,shutil,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[1]
def digest(p):
 with p.open('rb') as r:return hashlib.file_digest(r,'sha256').hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--include-maintainer-runtime',action='store_true');a=p.parse_args();out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
 version=json.loads((ROOT/'distribution/release.json').read_text())['versionName']
 public=out/('PocketLlama-'+version+'-github-source.zip')
 subprocess.run([sys.executable,str(ROOT/'scripts/export-public-source.py'),'--output',str(public)],check=True)
 files=[public]
 if a.include_maintainer_runtime:
  spec=importlib.util.spec_from_file_location('export_source',ROOT/'scripts/export-public-source.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);selected={rel.as_posix():source for source,rel in module.selected()}
  # This private checkpoint keeps the already reviewed runtime and noncommercial
  # personal OCR detector. It is deliberately separate from the GitHub export.
  for directory in ['app/libs','app/src/main/jniLibs','app/src/main/assets/speech','app/src/main/assets/ocr','app/src/main/assets/surya','app/src/main/assets/video','docs/internal']:
   for source in (ROOT/directory).rglob('*'):
    if source.is_file():
     rel=source.relative_to(ROOT)
     if any(part.startswith('.') for part in rel.parts) or source.suffix in ['.part','.tmp','.pem','.jks','.keystore','.p12','.pfx']:continue
     selected[rel.as_posix()]=source
  private=out/('PocketLlama-'+version+'-maintainer-source.zip')
  with zipfile.ZipFile(private,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
   for rel,source in sorted(selected.items()):z.write(source,'pocket-llama/'+rel)
   z.writestr('PRIVATE-REBUILD-NOT-FOR-PUBLICATION.txt','Personal rebuild checkpoint. Includes runtime binaries and model assets under their individual licenses. Use the separate github-source.zip for public source publication. No signing keys are included. Surya VLM/projector can be restored with scripts/prepare-surya-weights.py for a permitted use.\n')
  files.append(private)
 readme=out/'PocketLlama-README-RU.md';shutil.copyfile(ROOT/'README-RU.md',readme);files.append(readme)
 for name in ['PocketLlama-'+version+'-personal-update-arm64.apk','PocketLlama-'+version+'-community-arm64.apk','PocketLlama-'+version+'-play-unsigned.aab']:
  target=out/name
  if target.is_file():files.append(target)
 for file in [public]+([private] if a.include_maintainer_runtime else []):
  with zipfile.ZipFile(file) as z:
   if z.testzip():raise SystemExit('Archive CRC failed')
 sums=out/'SHA256SUMS-0.8.0.txt';sums.write_text(''.join(digest(f)+'  '+f.name+'\n' for f in files))
 for f in files:print(f.name,f.stat().st_size,digest(f))
if __name__=='__main__':main()
