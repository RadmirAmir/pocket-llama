#!/usr/bin/env python3
"""Run the production summary engine against a real local llama-server (same process tree).
Needs JDK17 on PATH and a desktop org.json jar, supplied with --json-jar.
"""
import argparse
import http.server
import json
from pathlib import Path
import subprocess
import time
import threading
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--server',required=True);p.add_argument('--model',required=True);p.add_argument('--json-jar',required=True);p.add_argument('--inject-length',action='store_true',help='Force the first real model response to end at 32 tokens; exercise recovery');p.add_argument('--video-regression',action='store_true',help='Check substantive video content and citations across 84 synthetic turns');p.add_argument('--context',type=int);args=p.parse_args()
context=args.context or (8192 if args.video_regression else 2048)
test_class='VideoSummaryIntegration' if args.video_regression else 'LocalSummaryIntegration'
build=ROOT/'build/summary-live';build.mkdir(exist_ok=True,parents=True)
sources=[ROOT/'app/src/main/java/dev/pocketllama'/s for s in ['core/SseReader.java','core/TextAttachment.java','core/LoopbackHttp.java','core/GgufMetadata.java','core/GenerationProfile.java','ChatApi.java','voice/SummarySource.java','voice/LocalSummary.java']]+[ROOT/'tests'/(test_class+'.java')]
subprocess.run(['javac','--release','11','-cp',args.json_jar,'-d',str(build),*map(str,sources)],check=True)
with open(build/'server.log','w') as log:
    server=subprocess.Popen([args.server,'--model',args.model,'--ctx-size',str(context),'--threads','4','--batch-size','128','--ubatch-size','32','--host','127.0.0.1','--port','18080','--parallel','1','--api-key','test-voice','--no-webui','--jinja','--reasoning-budget','0','--no-warmup'],stdout=log,stderr=subprocess.STDOUT)
    try:
        for _ in range(150):
            try:
                req=urllib.request.Request('http://127.0.0.1:18080/health',headers={'Authorization':'Bearer test-voice'})
                urllib.request.urlopen(req,timeout=1).read();break
            except Exception:
                if server.poll() is not None:raise RuntimeError('llama-server exited; inspect '+str(build/'server.log'))
                time.sleep(.2)
        else:raise RuntimeError('llama-server did not become ready')
        proxy=None;calls=[]
        if args.inject_length:
            class Proxy(http.server.BaseHTTPRequestHandler):
                def do_POST(self):
                    body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                    if self.path=='/v1/chat/completions':
                        calls.append(body['max_tokens'])
                        if len(calls)==1:body['max_tokens']=32
                    request=urllib.request.Request('http://127.0.0.1:18080'+self.path,data=json.dumps(body).encode(),headers={'Authorization':'Bearer test-voice','Content-Type':'application/json'})
                    with urllib.request.urlopen(request,timeout=180) as response:
                        self.send_response(response.status);self.send_header('Content-Type',response.headers.get('Content-Type','application/json'));self.end_headers()
                        while True:
                            data=response.read(1024)
                            if not data:break
                            self.wfile.write(data);self.wfile.flush()
                def log_message(self,*args):pass
            proxy=http.server.ThreadingHTTPServer(('127.0.0.1',0),Proxy);threading.Thread(target=proxy.serve_forever,daemon=True).start()
        try:
            subprocess.run(['java','-XX:-UsePerfData','-cp',str(build)+':'+args.json_jar,test_class,str(proxy.server_port if proxy else 18080),str(build/('video-summary.md' if args.video_regression else 'control-summary.md')),str(context)],check=True,timeout=300 if args.video_regression else 180)
            if args.inject_length:
                if len(calls)<2:raise AssertionError('Injected real length did not exercise retry')
                print('PASS: injected 32-token native length recovered; real inference calls='+str(len(calls)))
        finally:
            if proxy:proxy.shutdown();proxy.server_close()
    finally:
        server.terminate()
        try:server.wait(timeout=10)
        except subprocess.TimeoutExpired:server.kill();server.wait()
