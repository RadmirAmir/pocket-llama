#!/usr/bin/env python3
"""Reproduce the 0.5.0 SQLite error and test production 0.5.1 settings on Android ART.

Uses only synthetic databases in a fresh /data/local/tmp directory; never opens app data.
Requires Android SDK35, JDK17, an adb device and prepared security runtime JARs.
"""
import argparse,hashlib,os,shutil,subprocess,sys,urllib.request,uuid,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(args,**kwargs):return subprocess.run(list(map(str,args)),check=True,**kwargs)
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
    parser.add_argument('--jdk',default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--adb');parser.add_argument('--serial')
    args=parser.parse_args()
    if not args.sdk or not args.jdk:parser.error('Provide --sdk/--jdk or ANDROID_SDK_ROOT/JAVA_HOME')
    sdk,jdk=Path(args.sdk).resolve(),Path(args.jdk).resolve();tools=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
    adb=[args.adb or str(sdk/'platform-tools/adb')]+(['-s',args.serial] if args.serial else [])
    abi=run(adb+['shell','getprop','ro.product.cpu.abi'],capture_output=True,text=True).stdout.strip()
    if abi not in ['arm64-v8a','armeabi-v7a','x86','x86_64']:raise RuntimeError('Unsupported adb device ABI: '+abi)
    build=ROOT/'build/sqlite-android';build.mkdir(parents=True,exist_ok=True)
    classes,dex=build/'classes',build/'dex'
    for path in [classes,dex]:
        if path.exists():shutil.rmtree(path)
        path.mkdir()
    jars=[ROOT/'app/libs'/name for name in ['sqlcipher-android-4.19.1.jar','androidx-sqlite-2.7.0.jar','kotlin-stdlib-2.2.10.jar']]
    run([jdk/'bin/javac','--release','11','-cp',os.pathsep.join(map(str,[android]+jars)),'-d',classes,ROOT/'app/src/main/java/dev/pocketllama/security/DatabaseSettings.java',ROOT/'tests/SQLiteAndroidRegression.java'])
    run([jdk/'bin/java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--min-api','29','--lib',android,'--output',dex]+sorted(classes.rglob('*.class'))+jars)
    archive=build/'regression.jar'
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
    library=build/'libsqlcipher.so'
    if abi=='arm64-v8a':shutil.copyfile(ROOT/'app/src/main/jniLibs/arm64-v8a/libsqlcipher.so',library)
    else:
        import json
        artifact=json.loads((ROOT/'security-runtime.lock.json').read_text())['artifacts'][0]
        aar=build/'sqlcipher-android-4.19.1.aar'
        if not aar.exists():urllib.request.urlretrieve(artifact['url'],aar)
        if hashlib.sha256(aar.read_bytes()).hexdigest()!=artifact['sha256']:raise RuntimeError('AAR checksum mismatch')
        with zipfile.ZipFile(aar) as z:library.write_bytes(z.read('jni/'+abi+'/libsqlcipher.so'))
    remote='/data/local/tmp/pocket-sqlite-qa-'+uuid.uuid4().hex
    try:
        run(adb+['shell','mkdir','-p',remote])
        run(adb+['push',archive,remote+'/regression.jar']);run(adb+['push',library,remote+'/libsqlcipher.so'])
        run(adb+['shell','chmod','444',remote+'/regression.jar',remote+'/libsqlcipher.so'])
        run(adb+['shell','CLASSPATH='+remote+'/regression.jar','app_process','/system/bin','SQLiteAndroidRegression',remote+'/data',remote+'/libsqlcipher.so'])
    finally:subprocess.run(adb+['shell','rm','-rf',remote],check=False)
if __name__=='__main__':main()
