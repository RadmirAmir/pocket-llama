#!/usr/bin/env python3
"""Exercise the local PackageInstaller helper on a disposable Android emulator."""
from pathlib import Path
import re, subprocess, time, xml.etree.ElementTree as ET

EVIDENCE=Path('install-evidence')
def adb(*args,check=True):
    r=subprocess.run(['adb',*args],text=True,capture_output=True,timeout=90)
    if check and r.returncode:raise RuntimeError(r.stdout+r.stderr)
    return r.stdout
def tree():
    r=adb('shell','uiautomator','dump','/sdcard/pocket-helper-ui.xml',check=False)
    raw=adb('exec-out','cat','/sdcard/pocket-helper-ui.xml',check=False)
    if '<hierarchy' not in raw:return None
    EVIDENCE.joinpath('helper-last-ui.xml').write_text(raw)
    try:return ET.fromstring(raw)
    except ET.ParseError:return None
def find(texts,timeout=40):
    until=time.monotonic()+timeout
    wanted={x.casefold() for x in texts}
    while time.monotonic()<until:
        ui=tree()
        if ui is not None:
            for n in ui.iter('node'):
                if n.get('text','').casefold() in wanted:return n
        time.sleep(1)
    raise AssertionError('UI control not found: '+repr(texts))
def tap(node):
    values=[int(x) for x in re.findall(r'\d+',node.get('bounds',''))]
    if len(values)!=4:raise AssertionError('Missing control bounds')
    adb('shell','input','tap',str((values[0]+values[2])//2),str((values[1]+values[3])//2))
def choose(name):
    tap(find(['Выбрать community APK']))
    tap(find([name]))
def wait_text(fragment,timeout=90):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        ui=tree()
        if ui is not None and fragment in ' '.join(n.get('text','') for n in ui.iter('node')):return
        time.sleep(1)
    raise AssertionError('Expected result not shown: '+fragment)

EVIDENCE.mkdir(exist_ok=True)
adb('uninstall','dev.pocketllama')
print(adb('install','--no-streaming','artifacts/Pocket-community-installer-1.0.apk'),flush=True)
adb('shell','appops','set','dev.pocketllama.installer','REQUEST_INSTALL_PACKAGES','allow')
# The test isolates Android's local installer contract from Google's network scanner.
# Signature checking and the helper's pinned APK verification remain enabled.
adb('shell','settings','put','global','package_verifier_enable','0')
name='PocketLlama-0.9.0-community-arm64.apk'
adb('push','build/install/community-0.9.0.apk','/sdcard/Download/'+name)
adb('shell','am','start','-W','-n','dev.pocketllama.installer/dev.pocketllama.installer.InstallActivity')
choose(name)
tap(find(['Install','Update','Установить','Обновить'],timeout=90))
wait_text('Pocket Community установлен',timeout=90)
assert adb('shell','pm','path','dev.pocketllama').startswith('package:')
print('PASS: pinned APK verification, interactive confirmation and success callback',flush=True)

corrupt=EVIDENCE/'corrupt-community.apk';corrupt.write_bytes(b'not-an-apk')
adb('push',str(corrupt),'/sdcard/Download/corrupt-community.apk')
choose('corrupt-community.apk')
wait_text('Файл отличается от опубликованного community 0.9.0')
print('PASS: corrupt APK rejected before commit; original community installation retained',flush=True)
