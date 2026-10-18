#!/usr/bin/env python3
"""Run production ChatApi against a verified external GGUF; no model download or APK payload changes."""
import argparse
from pathlib import Path
import subprocess
import time
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--server',required=True);p.add_argument('--model',required=True);p.add_argument('--sha256',required=True);p.add_argument('--json-jar',required=True);p.add_argument('--qemu');p.add_argument('--android-root');p.add_argument('--no-thinking-only',action='store_true');p.add_argument('--reasoning-regression',action='store_true')
args=p.parse_args();build=ROOT/'build/chat-live';build.mkdir(parents=True,exist_ok=True)
names=['core/SseReader.java','core/TextAttachment.java','core/LoopbackHttp.java','core/GgufMetadata.java','core/GgufReader.java','core/GenerationProfile.java','core/ModelFingerprint.java','ChatApi.java']
test_class='ChatReasoningIntegration' if args.reasoning_regression else 'LocalChatIntegration'
sources=[ROOT/'app/src/main/java/dev/pocketllama'/n for n in names]+[ROOT/('tests/'+test_class+'.java')]
subprocess.run(['javac','--release','11','-cp',args.json_jar,'-d',str(build),*map(str,sources)],check=True)
cmd=[args.server,'--model',args.model,'--host','127.0.0.1','--port','18081','--ctx-size','4096','--parallel','1','--threads','4','--threads-batch','4','--batch-size','256','--ubatch-size','64','--cache-ram','0','--no-cache-idle-slots','--ctx-checkpoints','1','--cache-type-k','f16','--cache-type-v','f16','--flash-attn','auto','--load-mode','mmap','--device','none','--fit','off','--jinja','--no-webui','--api-key','test-chat']
if args.qemu:
    if not args.android_root:p.error('--qemu requires --android-root containing Android linker64 and Bionic libraries')
    runtime=Path(args.android_root).resolve()
    cmd=[args.qemu,'-cpu','max','-L',str(runtime),'-E','LD_LIBRARY_PATH='+str(runtime/'system/lib64')]+cmd
with (build/'server.log').open('w') as log:
    server=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT)
    try:
        for _ in range(300):
            try:
                request=urllib.request.Request('http://127.0.0.1:18081/health',headers={'Authorization':'Bearer test-chat'})
                urllib.request.urlopen(request,timeout=1).read();break
            except Exception:
                if server.poll() is not None:raise RuntimeError('Server exited; inspect '+str(build/'server.log'))
                time.sleep(.2)
        else:raise RuntimeError('Server not ready')
        client=['java','-cp',str(build)+':'+args.json_jar,test_class,'18081',args.model,args.sha256]
        if args.reasoning_regression:client.append(str(build/'reasoning-result.json'))
        elif args.no_thinking_only:client.append('--no-thinking-only')
        subprocess.run(client,check=True,timeout=1200 if args.qemu else 600)
    finally:
        server.terminate()
        try:server.wait(timeout=10)
        except subprocess.TimeoutExpired:server.kill();server.wait()
