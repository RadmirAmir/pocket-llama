#!/usr/bin/env python3
"""Exercise the exact production launcher/arguments against the bundled yt-dlp.

Default: a real generic extractor and a synthetic M4A served by a local HTTP proxy.
--url also checks a public platform. This uses host CPython; Android's native
linker, QuickJS binary, MediaCodec and services still require a device test.
Requires javac/java, CHAT_JSON_JAR and ffmpeg (or --fixture).
"""
from pathlib import Path
import argparse,hashlib,http.server,os,shlex,shutil,subprocess,sys,tempfile,threading
ROOT=Path(__file__).resolve().parents[1]
def run(args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--fixture');p.add_argument('--url');p.add_argument('--expected-id');args=p.parse_args()
 json_jar=os.environ['CHAT_JSON_JAR'];build=ROOT/'build/video-runtime-tests';build.mkdir(parents=True,exist_ok=True)
 sources=[ROOT/'app/src/main/java/dev/pocketllama'/n for n in ['security/ChunkedFile.java','video/VideoUrl.java','video/VideoRuntime.java','video/VideoDownloader.java']]
 sources+=sorted((ROOT/'tests/video-runtime-stubs').rglob('*.java'))+[ROOT/'tests/VideoHostRuntimeTests.java']
 run(['javac','--release','11','-cp',json_jar,'-d',build]+sources)
 with tempfile.TemporaryDirectory(prefix='pocket-real-loader-') as folder:
  work=Path(folder);native=work/'native';native.mkdir()
  # Match Android's executable filenames while using the host interpreter only.
  (native/'libpython.so').write_text('#!/bin/sh\nunset PYTHONHOME PYTHONPATH LD_LIBRARY_PATH\nexec '+shlex.quote(sys.executable)+' "$@"\n');(native/'libpython.so').chmod(0o700)
  server=None
  if args.url:url=args.url;digest='-'
  else:
   fixture=Path(args.fixture) if args.fixture else work/'tone.m4a'
   if not args.fixture:run(['ffmpeg','-hide_banner','-loglevel','error','-f','lavfi','-i','sine=frequency=440:duration=2','-c:a','aac','-y',fixture])
   data=fixture.read_bytes();digest=hashlib.sha256(data).hexdigest()
   class Fixture(http.server.BaseHTTPRequestHandler):
    def serve(self,body):
     self.send_response(200);self.send_header('Content-Type','audio/mp4');self.send_header('Content-Length',str(len(data)));self.end_headers()
     if body:self.wfile.write(data)
    def do_HEAD(self):self.serve(False)
    def do_GET(self):self.serve(True)
    def log_message(self,*args):pass
   server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Fixture);threading.Thread(target=server.serve_forever,daemon=True).start()
   url='http://pocket-fixture.example/tone.m4a'
  env=os.environ.copy()
  if server:
   proxy='http://127.0.0.1:'+str(server.server_port)
   for name in ['http_proxy','HTTP_PROXY','https_proxy','HTTPS_PROXY']:env[name]=proxy
   env['no_proxy']=env['NO_PROXY']=''
  try:
   command=['java','-cp',str(build)+os.pathsep+json_jar,'VideoHostRuntimeTests',work,ROOT/'app/src/main/assets',url,digest]
   if args.expected_id:command.append(args.expected_id)
   run(command,env=env,timeout=240)
  finally:
   if server:server.shutdown();server.server_close()
if __name__=='__main__':main()
