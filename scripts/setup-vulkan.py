#!/usr/bin/env python3
"""Fetch pinned Khronos headers and prepare a local Vulkan build dependency tree."""
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DEPS = ROOT / 'build/gpu-deps'

def run(*args):
    subprocess.run([str(x) for x in args], check=True)

def main():
    pins = json.loads((ROOT / 'gpu-headers.lock.json').read_text())
    DEPS.mkdir(parents=True, exist_ok=True)
    for name, commit in pins.items():
        target = DEPS / name
        if not target.exists():
            run('git', 'init', target)
            run('git', '-C', target, 'remote', 'add', 'origin', f'https://github.com/KhronosGroup/{name}.git')
            run('git', '-C', target, 'fetch', '--depth', '1', 'origin', commit)
            run('git', '-C', target, 'checkout', '--detach', 'FETCH_HEAD')
        marker = target / 'POCKET_SOURCE_COMMIT'
        actual = marker.read_text().strip() if marker.is_file() else subprocess.check_output(['git', '-C', str(target), 'rev-parse', 'HEAD'], text=True).strip()
        if actual != commit: raise RuntimeError(f'{name} source does not match lock; refusing to overwrite')
    run('cmake', '-S', DEPS / 'SPIRV-Headers', '-B', DEPS / 'spirv-build', f'-DCMAKE_INSTALL_PREFIX={DEPS / "spirv-install"}')
    run('cmake', '--install', DEPS / 'spirv-build')
    include = DEPS / 'include'
    shutil.copytree(DEPS / 'Vulkan-Headers/include', include, dirs_exist_ok=True)
    shutil.copytree(DEPS / 'Vulkan-Hpp/vulkan', include / 'vulkan', dirs_exist_ok=True)
    shutil.copytree(DEPS / 'spirv-install/include/spirv', include / 'spirv', dirs_exist_ok=True)
    notices = ROOT / 'notices'
    notices.mkdir(exist_ok=True)
    for name, filename in [('Vulkan-Headers','LICENSE.md'), ('Vulkan-Hpp','LICENSE.txt'), ('SPIRV-Headers','LICENSE')]:
        shutil.copyfile(DEPS / name / filename, notices / (name + '.txt'))
    print('Pinned Vulkan / SPIR-V headers prepared. Run: bash scripts/build-native.sh vulkan')

if __name__ == '__main__': main()
