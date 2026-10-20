#!/usr/bin/env python3
"""Build the pinned, offline-installable SearXNG bundle for Android.

Reuse the app's CPython 3.12. Cross-compile msgspec and lxml with NDK r28c;
libxml2/libxslt are static, so no obsolete Android Python wheels are required.
No pip, source downloads or compilation occur on the user's phone.
"""
from pathlib import Path
import argparse
import ast
import concurrent.futures
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile
import search_bundle

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache/search'
LOCK = json.loads((ROOT / 'search-runtime.lock.json').read_text())


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def download(item):
    target = CACHE / item['name']
    if target.is_file() and target.stat().st_size == item['bytes'] and digest(target) == item['sha256']:
        return target
    part = target.with_suffix(target.suffix + '.part')
    with urllib.request.urlopen(item['url'], timeout=90) as source, part.open('wb') as out:
        shutil.copyfileobj(source, out)
    if part.stat().st_size != item['bytes'] or digest(part) != item['sha256']:
        part.unlink(missing_ok=True)
        raise ValueError('Search dependency checksum mismatch: ' + item['name'])
    part.replace(target)
    return target


def unpack(name, directory):
    with tarfile.open(CACHE / name) as archive:
        top = archive.getmembers()[0].name.split('/')[0]
        path = directory / top
        if not path.exists():
            archive.extractall(directory, filter='data')
    return path


def run(args, cwd, env, log):
    print('Search build:', Path(args[0]).name, ' '.join(map(str, args[1:3])), flush=True)
    with log.open('ab') as out:
        result = subprocess.run(list(map(str, args)), cwd=cwd, env=env, stdout=out, stderr=subprocess.STDOUT)
    if result.returncode:
        print(log.read_text(errors='replace')[-10000:])
        raise RuntimeError('Search native build failed; see ' + str(log))


def native(abi, ndk):
    work = ROOT / 'build/search-native' / abi
    work.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256((ROOT / 'search-runtime.lock.json').read_bytes() + Path(__file__).read_bytes()).hexdigest()
    marker = work / 'ready.txt'
    if marker.is_file() and marker.read_text() == key:
        return work
    toolchain = ndk / 'toolchains/llvm/prebuilt/linux-x86_64'
    triple = {'arm64-v8a': 'aarch64-linux-android', 'x86_64': 'x86_64-linux-android'}[abi]
    compiler = toolchain / 'bin' / (triple + '29-clang')
    if not compiler.is_file():
        raise ValueError('Set ANDROID_NDK_HOME to NDK ' + LOCK['ndk'])
    env = os.environ.copy()
    prefix = work / 'prefix'
    env.update(CC=str(compiler), AR=str(toolchain / 'bin/llvm-ar'),
               RANLIB=str(toolchain / 'bin/llvm-ranlib'), STRIP=str(toolchain / 'bin/llvm-strip'),
               CFLAGS='-O2 -fPIC', LDFLAGS='-Wl,-z,max-page-size=16384',
               PKG_CONFIG_LIBDIR=str(prefix / 'lib/pkgconfig'))
    log = work / 'build.log'
    xml = unpack('libxml2-2.15.3.tar.xz', work)
    xslt = unpack('libxslt-1.1.45.tar.xz', work)
    if not (prefix / 'lib/libxml2.a').is_file():
        run([xml / 'configure', '--host=' + triple, '--prefix=' + str(prefix),
             '--disable-shared', '--enable-static', '--with-pic', '--without-python',
             '--without-lzma', '--without-zlib', '--without-iconv', '--without-icu',
             '--without-http', '--without-ftp', '--without-history'], xml, env, log)
        run(['make', '-j4'], xml, env, log)
        run(['make', 'install'], xml, env, log)
    if not (prefix / 'lib/libxslt.a').is_file():
        run([xslt / 'configure', '--host=' + triple, '--prefix=' + str(prefix),
             '--disable-shared', '--enable-static', '--with-pic', '--without-python',
             '--without-crypto', '--with-libxml-prefix=' + str(prefix)], xslt, env, log)
        run(['make', '-j4'], xslt, env, log)
        run(['make', 'install'], xslt, env, log)
    # Generate target pyconfig.h from the *bundled Android interpreter's* own
    # build variables, not the Linux build host's ABI or optional features.
    python_source = unpack('Python-3.12.11.tar.xz', work)
    headers = work / 'headers'
    shutil.copytree(python_source / 'Include', headers, dirs_exist_ok=True)
    with zipfile.ZipFile(ROOT / '.cache/video/ytdl.aar') as aar:
        with zipfile.ZipFile(io.BytesIO(aar.read('jni/' + abi + '/libpython.zip.so'))) as runtime:
            config_name = next(n for n in runtime.namelist() if '_sysconfigdata' in n and n.endswith('.py'))
            tree = ast.parse(runtime.read(config_name).decode())
            variables = ast.literal_eval(next(n.value for n in tree.body if isinstance(n, ast.Assign)))
            python_library = work / variables['INSTSONAME']
            python_library.write_bytes(runtime.read('usr/lib/' + variables['INSTSONAME']))
    template = (python_source / 'pyconfig.h.in').read_text()

    def define(match):
        name = match.group(1)
        value = variables.get(name)
        if value in (None, '', 0):
            return '/* #undef ' + name + ' */'
        if isinstance(value, str):
            value = json.dumps(value)
        return '#define ' + name + ' ' + str(value)

    (headers / 'pyconfig.h').write_text(re.sub(r'^#undef (\w+)\s*$', define, template, flags=re.M))
    lxml = unpack(next(i['name'] for i in LOCK['artifacts'] if i.get('package', '').lower() == 'lxml'), work)
    msgspec = unpack(next(i['name'] for i in LOCK['artifacts'] if i.get('package', '').lower() == 'msgspec'), work)
    (lxml / 'src/lxml/includes/lxml-version.h').write_text('#define LXML_VERSION_STRING "6.1.2"\n')
    common = [compiler, '-shared', '-fPIC', '-O2', '-g0', '-Wl,-z,max-page-size=16384',
              '-I' + str(headers), '-DPY_SSIZE_T_CLEAN']
    run([*common, '-I' + str(msgspec / 'src/msgspec'), msgspec / 'src/msgspec/_core.c',
         '-o', work / '_core.so', python_library, '-lm'], work, env, log)
    run([*common, '-DCYTHON_CLINE_IN_TRACEBACK=0', '-DCYTHON_USE_SYS_MONITORING=0',
         '-DLIBXML_STATIC', '-DLIBXSLT_STATIC', '-DLIBEXSLT_STATIC',
         '-I' + str(lxml / 'src'), '-I' + str(lxml / 'src/lxml/includes'),
         '-I' + str(prefix / 'include/libxml2'), '-I' + str(prefix / 'include'),
         lxml / 'src/lxml/etree.c', '-o', work / 'etree.so',
         prefix / 'lib/libexslt.a', prefix / 'lib/libxslt.a', prefix / 'lib/libxml2.a', python_library, '-lm'], work, env, log)
    # Android's linker uses each extension's dependency group for Python data
    # symbols. Unlike a Linux host build, both modules need explicit DT_NEEDED.
    for extension in ('_core.so', 'etree.so'):
        dynamic = subprocess.check_output([str(toolchain / 'bin/llvm-readelf'), '-d', str(work / extension)], text=True)
        if '[' + variables['INSTSONAME'] + ']' not in dynamic:
            raise ValueError('Missing bundled Python dependency in ' + extension)
    marker.write_text(key)
    return work


def build(abi, ndk):
    CACHE.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(download, LOCK['artifacts']))
    work = native(abi, ndk)
    packages = work / 'packages'
    if packages.exists():
        shutil.rmtree(packages)
    packages.mkdir()
    for item in LOCK['artifacts']:
        if item['kind'] == 'bdist_wheel':
            with zipfile.ZipFile(CACHE / item['name']) as wheel:
                wheel.extractall(packages)
        elif item.get('package', '').lower() in ('pyyaml', 'markupsafe', 'msgspec', 'lxml'):
            source = unpack(item['name'], work)
            package = {'pyyaml': ('lib/yaml', 'yaml'), 'markupsafe': ('src/markupsafe', 'markupsafe'),
                       'msgspec': ('src/msgspec', 'msgspec'), 'lxml': ('src/lxml', 'lxml')}[item['package'].lower()]
            shutil.copytree(source / package[0], packages / package[1], dirs_exist_ok=True,
                            ignore=shutil.ignore_patterns('*.c', '*.h', '*.pxd', '*.pxi', '*.pyx', '__pycache__', '*.pyc'))
            for license in source.glob('*'):
                if license.is_file() and license.name.lower().startswith(('license', 'copying')):
                    shutil.copyfile(license, packages / package[1] / license.name)
    shutil.copyfile(work / '_core.so', packages / 'msgspec/_core.so')
    shutil.copyfile(work / 'etree.so', packages / 'lxml/etree.so')
    upstream = unpack('searxng-15b0c8e.tar.gz', work)
    shutil.copytree(upstream / 'searx', packages / 'searx', dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('__pycache__', '*.pyc', 'static', 'templates'))
    frozen = ('VERSION_STRING = "2026.9.4+15b0c8e"\nVERSION_TAG = "2026.9.4+15b0c8e"\n'
              'DOCKER_TAG = "2026.9.4-15b0c8e"\nGIT_URL = "https://github.com/searxng/searxng"\n'
              'GIT_BRANCH = "15b0c8ef3ab02a86131040e5eabc0d5b82bb9470"\n')
    (packages / 'searx/version_frozen.py').write_text(frozen)
    shutil.copyfile(upstream / 'LICENSE', packages / 'searx/LICENSE')
    for name in ('libxml2-2.15.3', 'libxslt-1.1.45'):
        license = work / name / 'Copyright'
        if not license.is_file():
            license = work / name / 'COPYING'
        shutil.copyfile(license, packages / (name + '-LICENSE.txt'))
    (packages / 'SOURCES.json').write_text(json.dumps(LOCK, indent=2) + '\n')
    assets = ROOT / ('app/src/main/assets/search' if abi == 'arm64-v8a' else 'build/search-test-assets')
    assets.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(assets / 'python.zip', 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for file in sorted(packages.rglob('*')):
            if file.is_file():
                info = zipfile.ZipInfo(file.relative_to(packages).as_posix(), (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100600 << 16
                archive.writestr(info, file.read_bytes())
    (assets / 'runtime-id.txt').write_text(digest(assets / 'python.zip') + '\n')
    if abi == 'arm64-v8a':
        files = [{'path': 'app/src/main/assets/search/' + name, 'bytes': (assets / name).stat().st_size,
                  'sha256': digest(assets / name)} for name in search_bundle.ASSETS]
        (ROOT / 'search-runtime.manifest.json').write_text(json.dumps({'revision': LOCK['searxngRevision'], 'files': files}, indent=2) + '\n')
        search_bundle.inventory(ROOT)
    print('Bundled SearXNG:', abi, (assets / 'python.zip').stat().st_size, 'bytes', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], default='arm64-v8a')
    parser.add_argument('--ndk', default=os.environ.get('ANDROID_NDK_HOME') or os.environ.get('ANDROID_NDK_ROOT'))
    args = parser.parse_args()
    if not args.ndk:
        parser.error('Set ANDROID_NDK_HOME to NDK ' + LOCK['ndk'])
    build(args.abi, Path(args.ndk).resolve())
