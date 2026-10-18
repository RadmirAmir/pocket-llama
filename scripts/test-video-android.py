#!/usr/bin/env python3
"""Run the real native yt-dlp/QuickJS, encrypted decoder and schema upgrades on Android.

Uses public synthetic media and synthetic database keys under a fresh /data/local/tmp
directory. With --apk, yt-dlp, launcher and runtime ID come from that exact APK;
the pinned x86_64 CPython/QuickJS are used for the emulator ABI. Never opens the installed Pocket's data. Requires SDK36, JDK17 and adb.
"""
from pathlib import Path
import argparse,hashlib,http.server,io,json,os,posixpath,shutil,stat,subprocess,threading,urllib.request,uuid,zipfile
ROOT=Path(__file__).resolve().parents[1]
def run(args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk',required=True);p.add_argument('--jdk',required=True);p.add_argument('--fixture',required=True);p.add_argument('--apk');p.add_argument('--compile-only',action='store_true');args=p.parse_args()
 sdk,jdk=Path(args.sdk),Path(args.jdk);adb=sdk/'platform-tools/adb';android=sdk/'platforms/android-36/android.jar';tools=sdk/'build-tools/36.0.0';build=ROOT/'build/video-android';build.mkdir(parents=True,exist_ok=True)
 print('Compiling Android video regression',flush=True)
 for name in ['classes','dex']:
  d=build/name
  if d.exists():shutil.rmtree(d)
  d.mkdir()
 jars=[ROOT/'app/libs'/n for n in ['sqlcipher-android-4.19.1.jar','androidx-sqlite-2.7.0.jar','kotlin-stdlib-2.2.10.jar']]
 source=ROOT/'app/src/main/java/dev/pocketllama'
 files=[source/n for n in ['I18n.java','core/SpeechLanguage.java','security/ChunkedFile.java','security/DatabaseSettings.java','voice/VoiceStore.java','voice/VoiceState.java','voice/PcmFile.java','voice/SincResampler.java','voice/AudioImporter.java','video/VideoUrl.java','video/VideoRuntime.java','video/VideoDownloader.java','video/EncryptedMediaSource.java']]+sorted((ROOT/'tests/android-video').glob('*.java'))
 run([jdk/'bin/javac','--release','11','-cp',os.pathsep.join(map(str,[android]+jars)),'-d',build/'classes']+files)
 run([jdk/'bin/java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','29','--lib',android,'--output',build/'dex']+sorted((build/'classes').rglob('*.class'))+jars)
 archive=build/'regression.jar'
 with zipfile.ZipFile(archive,'w') as z:z.write(build/'dex/classes.dex','classes.dex')
 if args.compile_only:
  print('Android video regression compiled');return
 native=build/'native';native.mkdir(exist_ok=True)
 artifact=json.loads((ROOT/'security-runtime.lock.json').read_text())['artifacts'][0];aar=build/'sqlcipher.aar'
 if not aar.exists():urllib.request.urlretrieve(artifact['url'],aar)
 if hashlib.sha256(aar.read_bytes()).hexdigest()!=artifact['sha256']:raise ValueError('SQLCipher checksum')
 with zipfile.ZipFile(aar) as z:(native/'libsqlcipher.so').write_bytes(z.read('jni/x86_64/libsqlcipher.so'))
 runtime=ROOT/'.cache/video/ytdl.aar'
 with zipfile.ZipFile(runtime) as z:
  for name in ['libpython.so','libqjs.so']:(native/name).write_bytes(z.read('jni/x86_64/'+name))
  python=zipfile.ZipFile(io.BytesIO(z.read('jni/x86_64/libpython.zip.so')))
  def content(name,seen=()):
   if name in seen:raise ValueError('Cyclic symlink')
   info=python.getinfo(name);data=python.read(name)
   if not stat.S_ISLNK(info.external_attr>>16):return data
   return content(posixpath.normpath(posixpath.join(posixpath.dirname(name),data.decode())),seen+(name,))
  pack=io.BytesIO()
  with zipfile.ZipFile(pack,'w',compression=zipfile.ZIP_DEFLATED) as out:
   for info in python.infolist():
    if not info.is_dir():out.writestr(info.filename,content(info.filename))
  with zipfile.ZipFile(build/'assets.apk','w',compression=zipfile.ZIP_DEFLATED) as out:
   out.writestr('assets/video/python.zip',pack.getvalue())
   if args.apk:
    with zipfile.ZipFile(args.apk) as published:
     assert published.getinfo('assets/video/python.zip').file_size>1000000
     for name in ['yt-dlp','runtime-id.txt','launcher.py']:out.writestr('assets/video/'+name,published.read('assets/video/'+name))
   else:
    for name in ['yt-dlp','runtime-id.txt','launcher.py']:out.write(ROOT/'app/src/main/assets/video'/name,'assets/video/'+name)
 fixture=Path(args.fixture).read_bytes()
 class Fixture(http.server.BaseHTTPRequestHandler):
  def serve(self,body):
   self.send_response(200);self.send_header('Content-Type','audio/mp4');self.send_header('Content-Length',str(len(fixture)));self.end_headers()
   if body:self.wfile.write(fixture)
  def do_HEAD(self):self.serve(False)
  def do_GET(self):self.serve(True)
  def log_message(self,*args):pass
 server=http.server.ThreadingHTTPServer(('0.0.0.0',0),Fixture);threading.Thread(target=server.serve_forever,daemon=True).start()
 remote='/data/local/tmp/pocket-video-qa-'+uuid.uuid4().hex
 try:
  print('Waiting for adb device',flush=True);run([adb,'wait-for-device'],timeout=600)
  run([adb,'shell','mkdir','-p',remote+'/native']);run([adb,'push',archive,remote+'/regression.jar']);run([adb,'push',build/'assets.apk',remote+'/assets.apk'])
  for lib in native.glob('*.so'):run([adb,'push',lib,remote+'/native/'+lib.name])
  run([adb,'shell','chmod','-R','755',remote]);run([adb,'shell','chmod','444',remote+'/regression.jar'])
  print('Running native Android video and migration checks',flush=True)
  proxy='http://10.0.2.2:'+str(server.server_port)
  run([adb,'shell','env','http_proxy='+proxy,'HTTP_PROXY='+proxy,'CLASSPATH='+remote+'/regression.jar','app_process','/system/bin','AndroidVideoRegression',remote,hashlib.sha256(fixture).hexdigest()],timeout=300)
 finally:server.shutdown();subprocess.run([adb,'shell','rm','-rf',remote],timeout=20,check=False)
if __name__=='__main__':main()
