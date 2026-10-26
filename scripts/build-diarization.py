#!/usr/bin/env python3
"""Build the isolated Nemotron JNI library from a locked source checkout."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'vendor/nemo-speech')
    parser.add_argument('--host', action='store_true')
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], default='arm64-v8a')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'diarization.lock.json').read_text())['runtime']
    source = args.source.resolve()
    if not source.exists():
        run('git', 'clone', '--no-checkout', '--filter=blob:none', lock['source'], source)
        run('git', '-C', source, 'checkout', '--detach', lock['commit'])
        run('git', '-C', source, 'submodule', 'update', '--init', '--depth', '1', 'llama.cpp')
    for directory, expected in [(source, lock['commit']), (source / 'llama.cpp', lock['llamaCommit'])]:
        actual = subprocess.check_output(['git', '-C', str(directory), 'rev-parse', 'HEAD'], text=True).strip()
        if actual != expected:
            raise SystemExit('Native source pin mismatch: ' + str(directory))
        if subprocess.check_output(['git', '-C', str(directory), 'status', '--porcelain', '--untracked-files=no'], text=True).strip():
            raise SystemExit('Native dependency has uncommitted modifications: ' + str(directory))
    build = ROOT / 'build' / ('nemotron-host' if args.host else 'nemotron-arm64' if args.abi=='arm64-v8a' else 'nemotron-x86_64')
    command = ['cmake', '-S', str(ROOT / 'native/diarization'), '-B', str(build), '-G', 'Ninja',
               '-DCMAKE_BUILD_TYPE=Release', '-DNEMO_SOURCE=' + str(source)]
    if not args.host:
        ndk = os.environ.get('ANDROID_NDK_HOME') or os.environ.get('ANDROID_NDK_ROOT')
        if not ndk:
            raise SystemExit('Set ANDROID_NDK_HOME to the Android r28 toolchain')
        command += ['-DCMAKE_TOOLCHAIN_FILE=' + str(Path(ndk) / 'build/cmake/android.toolchain.cmake'),
                    '-DANDROID_ABI='+args.abi, '-DANDROID_PLATFORM=android-29', '-DANDROID_STL=c++_static']
    run(*command)
    run('cmake', '--build', build, '--parallel', min(4, os.cpu_count() or 2))
    if not args.host and args.abi=='arm64-v8a':
        dest = ROOT / 'app/src/main/jniLibs/arm64-v8a/libpocket_nemotron.so'
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(build / 'libpocket_nemotron.so', dest)
        run(Path(ndk) / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip', '--strip-unneeded', dest)
        print('Built portable ARM64 Nemotron JNI:', dest)


if __name__ == '__main__':
    main()
