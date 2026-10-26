#!/usr/bin/env python3
"""Run the real Nemotron JNI, encrypted audio and model in disposable Android ART."""
from pathlib import Path
import argparse
import json
import subprocess
import uuid
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def run(*args, **kwargs):
    return subprocess.run(list(map(str,args)),check=True,**kwargs)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk',required=True,type=Path)
    p.add_argument('--jdk',required=True,type=Path)
    args=p.parse_args();sdk=args.sdk;jdk=args.jdk
    build=ROOT/'build/diarization-android';build.mkdir(parents=True,exist_ok=True)
    classes=build/'classes';classes.mkdir(exist_ok=True);dex=build/'dex';dex.mkdir(exist_ok=True)
    source=ROOT/'app/src/main/java/dev/pocketllama'
    files=[source/'voice'/(n+'.java') for n in ['SpeakerDiarization','NemotronDiarizer','PcmFile']]
    files += [source/'security/ChunkedFile.java',ROOT/'tests/NemotronNativeSmoke.java']
    run(jdk/'bin/javac','--release','11','-d',classes,*files)
    run(jdk/'bin/java','-cp',sdk/'build-tools/36.0.0/lib/d8.jar','com.android.tools.r8.D8','--min-api','29',
        '--lib',sdk/'platforms/android-36/android.jar','--output',dex,*sorted(classes.rglob('*.class')))
    archive=build/'test.jar'
    with zipfile.ZipFile(archive,'w') as z:
        for f in dex.glob('*.dex'):z.write(f,f.name)
    adb=sdk/'platform-tools/adb'
    if subprocess.check_output([str(adb),'shell','getprop','ro.kernel.qemu'],text=True).strip()!='1' and subprocess.check_output([str(adb),'shell','getprop','ro.boot.qemu'],text=True).strip()!='1':
        raise SystemExit('Refusing QA on a physical device')
    remote='/data/local/tmp/pocket-diar-'+uuid.uuid4().hex
    run(adb,'shell','mkdir','-p',remote)
    lock=json.loads((ROOT/'diarization.lock.json').read_text())
    files=[(archive,'test.jar'),(ROOT/'build/nemotron-x86_64/libpocket_nemotron.so','libpocket_nemotron.so'),
           (ROOT/'app/src/main/assets/diarization'/lock['model']['name'],'model.gguf'),
           (ROOT/'vendor/nemo-speech/test_files/diar/ami_en2002d_2132.wav','meeting.wav')]
    try:
        for local,name in files:run(adb,'push',local,remote+'/'+name)
        run(adb,'shell','chmod','444',remote+'/test.jar')
        run(adb,'shell',f'CLASSPATH={remote}/test.jar app_process -Djava.library.path={remote} -Djava.io.tmpdir={remote} /system/bin NemotronNativeSmoke {remote}/model.gguf {remote}/meeting.wav',timeout=180)
    finally:run(adb,'shell','rm','-rf',remote)


if __name__=='__main__':main()
