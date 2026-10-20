#!/usr/bin/env python3
"""Install pinned host-only test packages and restore the exact SearXNG source."""
from pathlib import Path
import json
import subprocess
import sys
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
lock = json.loads((ROOT / 'search-runtime.lock.json').read_text())
requirements = ROOT / 'build/search-host-requirements.txt'
requirements.parent.mkdir(parents=True, exist_ok=True)
requirements.write_text(''.join(i['package'] + '==' + i['version'] + '\n'
                                for i in lock['artifacts'] if i.get('package')))
subprocess.run([sys.executable, '-m', 'pip', 'install', '-r', str(requirements)], check=True)
spec = importlib.util.spec_from_file_location('search_prepare', ROOT / 'scripts/prepare-search.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.CACHE.mkdir(parents=True, exist_ok=True)
source = next(i for i in lock['artifacts'] if i['name'].startswith('searxng-'))
module.download(source)
path = module.unpack(source['name'], ROOT / 'build/search-host')
(ROOT / 'build/search-host-source.txt').write_text(str(path) + '\n')
