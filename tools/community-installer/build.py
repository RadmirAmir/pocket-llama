#!/usr/bin/env python3
"""Build the tiny public-APK diagnostic installer with the official Android SDK."""
from pathlib import Path
import argparse, hashlib, os, shutil, subprocess, urllib.request, zipfile

ROOT=Path(__file__).resolve().parents[2]
SOURCE=Path(__file__).resolve().parent
ECJ_SHA='29f6d3918ee02db4400c103bc25dd90a22491c3a395867d9393070cb96a7dd29'
def run(*args):subprocess.run([str(x) for x in args],check=True)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build-tools',required=True)
    p.add_argument('--android-jar',required=True)
    p.add_argument('--ecj')
    p.add_argument('--keystore',required=True)
    p.add_argument('--key-alias',default='androiddebugkey')
    p.add_argument('--output',default=str(ROOT/'artifacts/Pocket-community-installer-1.0.apk'))
    a=p.parse_args()
    if not os.environ.get('POCKET_INSTALLER_PASSWORD'):p.error('Set POCKET_INSTALLER_PASSWORD for the signing key')
    tools=Path(a.build_tools).resolve();android=Path(a.android_jar).resolve()
    compiler=Path(a.ecj or ROOT/'build/test-deps/ecj-3.42.0.jar').resolve()
    if not compiler.is_file():
        compiler.parent.mkdir(parents=True,exist_ok=True)
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.42.0/ecj-3.42.0.jar',timeout=90) as src,compiler.open('wb') as out:shutil.copyfileobj(src,out)
    if hashlib.sha256(compiler.read_bytes()).hexdigest()!=ECJ_SHA:raise SystemExit('Compiler checksum mismatch')
    build=ROOT/'build/community-installer'
    if build.exists():shutil.rmtree(build)
    classes=build/'classes';dex=build/'dex'
    classes.mkdir(parents=True);dex.mkdir()
    resource=build/'resources.apk'
    run(tools/'aapt2','link','-o',resource,'-I',android,'--manifest',SOURCE/'AndroidManifest.xml','--min-sdk-version','26','--target-sdk-version','36','--version-code','1','--version-name','1.0')
    boot=os.pathsep.join([str(android),str(tools/'core-lambda-stubs.jar')])
    run('java','-jar',compiler,'-source','1.8','-target','1.8','-bootclasspath',boot,'-encoding','UTF-8','-d',classes,*sorted(SOURCE.glob('*.java')))
    run('java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',android,'--output',dex,*sorted(classes.rglob('*.class')))
    unsigned=build/'unsigned.apk';shutil.copyfile(resource,unsigned)
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as archive:
        for item in dex.glob('*.dex'):archive.write(item,item.name)
    aligned=build/'aligned.apk';run(tools/'zipalign','-f','4',unsigned,aligned)
    output=Path(a.output).resolve();output.parent.mkdir(parents=True,exist_ok=True)
    run('java','-jar',tools/'lib/apksigner.jar','sign','--ks',Path(a.keystore).resolve(),'--ks-key-alias',a.key_alias,'--ks-pass','env:POCKET_INSTALLER_PASSWORD','--key-pass','env:POCKET_INSTALLER_PASSWORD','--v2-signing-enabled','true','--v3-signing-enabled','true','--out',output,aligned)
    run('java','-jar',tools/'lib/apksigner.jar','verify','--verbose',output)
    print('Built:',output,'bytes:',output.stat().st_size,'SHA-256:',hashlib.sha256(output.read_bytes()).hexdigest())
if __name__=='__main__':main()
