#!/usr/bin/env python3
"""Restore official, pinned PaddleOCR ONNX assets. Build-time only; never runs on the phone."""
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request
import yaml

ROOT = Path(__file__).resolve().parents[1]
def digest(path):
    with path.open('rb') as f: return hashlib.file_digest(f, 'sha256').hexdigest()
def main():
    assets = ROOT / 'app/src/main/assets/ocr'
    assets.mkdir(parents=True, exist_ok=True)
    for item in json.loads((ROOT / 'ocr-models.lock.json').read_text())['models']:
        target = assets / item['name']
        if target.is_file() and target.stat().st_size == item['bytes'] and digest(target) == item['sha256']: continue
        part = target.with_suffix('.part')
        with urllib.request.urlopen(item['url'], timeout=120) as response, part.open('wb') as output: shutil.copyfileobj(response, output)
        if part.stat().st_size != item['bytes'] or digest(part) != item['sha256']: raise ValueError('OCR checksum mismatch: ' + item['name'])
        part.replace(target)
    alphabet = yaml.safe_load((assets / 'rec.yml').read_text())['PostProcess']['character_dict']
    if any('\n' in character for character in alphabet): raise ValueError('Invalid dictionary')
    (assets / 'characters.txt').write_text('\n'.join(alphabet) + '\n')
    for item in json.loads((assets / 'manifest.json').read_text())['files']:
        path = assets / item['name']
        if path.stat().st_size != item['bytes'] or digest(path) != item['sha256']: raise ValueError('OCR derived asset mismatch: ' + item['name'])
    print('All pinned OCR assets verified')
if __name__ == '__main__': main()
