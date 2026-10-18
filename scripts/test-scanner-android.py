#!/usr/bin/env python3
"""Exercise production image transforms, text detector and exports on Android ART.

Requires SDK35/JDK17 and a running adb device (API29+). Uses synthetic fixtures
under a fresh /data/local/tmp directory; never reads an installed app's data.
Native dependencies are extracted from checksum-pinned upstream AARs.
"""
from pathlib import Path
import argparse,hashlib,os,shutil,subprocess,urllib.request,uuid,zipfile,json
ROOT=Path(__file__).resolve().parents[1]
def run(cmd,**kw):return subprocess.run(list(map(str,cmd)),check=True,**kw)
def download(url,path,sha):
 if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest()==sha:return
 path.parent.mkdir(parents=True,exist_ok=True);part=path.with_suffix('.part')
 with urllib.request.urlopen(url,timeout=60) as src,part.open('wb') as out:shutil.copyfileobj(src,out)
 if hashlib.sha256(part.read_bytes()).hexdigest()!=sha:raise RuntimeError('Incomplete dependency download; retry: '+path.name)
 os.replace(part,path)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk',required=True);p.add_argument('--jdk',required=True);p.add_argument('--serial');p.add_argument('--postprocess-only',action='store_true');a=p.parse_args();sdk,jdk=Path(a.sdk).resolve(),Path(a.jdk).resolve();adb=[sdk/'platform-tools/adb']+(['-s',a.serial] if a.serial else []);work=ROOT/'build/scanner-android';work.mkdir(parents=True,exist_ok=True)
 abi=run(adb+['shell','getprop','ro.product.cpu.abi'],capture_output=True,text=True).stdout.strip()
 if abi not in ['arm64-v8a','x86_64']:raise RuntimeError('Use ARM64 or x86_64 Android')
 for name in ['classes','dex']:
  d=work/name
  if d.exists():shutil.rmtree(d)
  d.mkdir()
 jars=[ROOT/'app/libs'/n for n in ['onnxruntime-android-1.22.0.jar','opencv-4.12.0.jar','jsoup-1.21.2.jar']];android=sdk/'platforms/android-35/android.jar';src=ROOT/'app/src/main/java/dev/pocketllama'
 names=['PhotoStore','DocumentStore','PhotoImages','EncryptedFiles','ScanGeometry','ScanImages','DocumentEdges','SuryaAssets','SuryaDetector','SafeOcrHtml','SuryaProtocol','ScanExports','ScanPdf','ScanXlsx','OcrEngine','OcrRaster','OcrGeometry']
 files=[src/'photo'/(n+'.java') for n in names]+[src/'core/TextAttachment.java',ROOT/'tests/scanner-stubs/dev/pocketllama/photo/PhotoKey.java']+sorted((ROOT/'tests/android-scanner').glob('*.java'))
 run([jdk/'bin/javac','--release','11','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[android]+jars)),'-d',work/'classes']+files)
 run([jdk/'bin/java','-cp',sdk/'build-tools/35.0.0/lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','29','--lib',android,'--output',work/'dex']+sorted((work/'classes').rglob('*.class'))+jars)
 with zipfile.ZipFile(work/'regression.jar','w') as z:
  for f in (work/'dex').glob('*.dex'):z.write(f,f.name)
 with zipfile.ZipFile(work/'assets.apk','w',compression=zipfile.ZIP_DEFLATED) as z:
  for f in (ROOT/'app/src/main/assets/surya').glob('*'):z.write(f,'assets/surya/'+f.name)
  z.write(ROOT/'app/src/main/assets/fonts/Manrope.ttf','assets/fonts/Manrope.ttf')
 native=work/'native';native.mkdir(exist_ok=True)
 scanner=json.loads((ROOT/'scanner-runtime.lock.json').read_text());ort=json.loads((ROOT/'speech-runtime.lock.json').read_text())
 for artifact in [{'url':scanner['artifacts'][0]['source'],'sha':scanner['artifacts'][0]['sha256'],'file':'opencv.aar'},{'url':ort['source'],'sha':ort['aar_sha256'],'file':'ort.aar'}]:
  archive=work/artifact['file'];download(artifact['url'],archive,artifact['sha'])
  with zipfile.ZipFile(archive) as z:
   for n in z.namelist():
    if n.startswith('jni/'+abi+'/') and n.endswith('.so'):(native/Path(n).name).write_bytes(z.read(n))
 remote='/data/local/tmp/pocket-scanner-qa-'+uuid.uuid4().hex
 try:
  run(adb+['shell','mkdir','-p',remote+'/native'])
  for f in [work/'regression.jar',work/'assets.apk',ROOT/'tests/fixtures/scan-page.jpg',ROOT/'tests/fixtures/scan-heatmap.f32']:run(adb+['push',f,remote+'/'+f.name])
  for f in native.glob('*.so'):run(adb+['push',f,remote+'/native/'+f.name])
  run(adb+['shell','chmod','444',remote+'/regression.jar'])
  run(adb+['shell','env','LD_LIBRARY_PATH='+remote+'/native','CLASSPATH='+remote+'/regression.jar','app_process','-Djava.library.path='+remote+'/native','/system/bin','ScannerAndroidTests',remote]+(['post'] if a.postprocess_only else []),timeout=600)
  for n in ['rectified.png','detection.json','export.pdf']:run(adb+['pull',remote+'/'+n,work/n])
 finally:subprocess.run(list(map(str,adb+['shell','rm','-rf',remote])),check=False,timeout=20)
if __name__=='__main__':main()
