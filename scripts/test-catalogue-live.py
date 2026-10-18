#!/usr/bin/env python3
"""Compatibility smoke test for the exact catalog files; does not benchmark phone quality or speed."""
from pathlib import Path
import argparse,hashlib,json,socket,subprocess,time,urllib.request
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',required=True);p.add_argument('--models',required=True);a=p.parse_args();results=[]
 out=ROOT/'build';out.mkdir(exist_ok=True)
 for m in json.loads((ROOT/'distribution/model-catalog.json').read_text())['models']:
  weights=Path(a.models)/m['filename']
  if weights.stat().st_size!=m['bytes']:raise ValueError('Model size mismatch')
  with weights.open('rb') as f:
   if hashlib.file_digest(f,'sha256').hexdigest()!=m['sha256']:raise ValueError('Model checksum mismatch')
  with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
  with (out/('catalog-live-'+m['id']+'.log')).open('w') as log:
   process=subprocess.Popen([str(Path(a.server).resolve()),'-m',str(weights.resolve()),'--host','127.0.0.1','--port',str(port),'-c','2048','-np','1','-t','4','-ngl','0','--jinja','--no-warmup'],stdout=log,stderr=subprocess.STDOUT)
   try:
    start=time.monotonic()
    while time.monotonic()-start<120:
     if process.poll() is not None:raise RuntimeError('Server exited during loading')
     try:
      with urllib.request.urlopen('http://127.0.0.1:'+str(port)+'/health',timeout=2) as r:
       if r.status==200:break
     except Exception:time.sleep(.2)
    else:raise TimeoutError('Server loading deadline')
    body={'messages':[{'role':'system','content':'Ты полезный помощник. Отвечай кратко по-русски.'},{'role':'user','content':'Скажи одним предложением, для чего нужен диктофон.'}],'max_tokens':96,'temperature':0,'chat_template_kwargs':{'enable_thinking':False}}
    request=urllib.request.Request('http://127.0.0.1:'+str(port)+'/v1/chat/completions',data=json.dumps(body).encode(),headers={'Content-Type':'application/json'});start=time.monotonic()
    with urllib.request.urlopen(request,timeout=120) as r:answer=json.load(r)
    text=answer['choices'][0]['message'].get('content','');reason=answer['choices'][0]['finish_reason']
    if not text.strip() or not any('А'<=c<='я' for c in text) or reason!='stop':raise ValueError('Russian reply smoke failed')
    row={'id':m['id'],'sha256':m['sha256'],'seconds':round(time.monotonic()-start,3),'answer':text,'finishReason':reason};results.append(row);print(json.dumps(row,ensure_ascii=False),flush=True)
   finally:
    process.terminate()
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.kill();process.wait()
 (out/'catalog-live-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
if __name__=='__main__':main()
