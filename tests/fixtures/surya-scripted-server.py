#!/usr/bin/env python3
"""Private loopback fixture, not an inference engine. Accepts the production CLI/auth contract."""
import json,sys,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer

def arg(name):return sys.argv[sys.argv.index(name)+1]
key=sys.stdin.readline().strip();alias=arg('--alias')
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def allowed(self):
  if self.headers.get('Authorization')=='Bearer '+key:return True
  self.send_error(401);return False
 def do_GET(self):
  if not self.allowed():return
  data=json.dumps({'modalities':{'vision':True},'model_alias':alias,'default_generation_settings':{'n_ctx':16384}}).encode()
  self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
 def do_POST(self):
  if not self.allowed():return
  body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  prompt=body['messages'][0]['content'][1]['text']
  self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
  raw=('<p>'+('Один повторяющийся фрагмент документа. '*100) if prompt=='repeat' else "<div data-label='Text' data-bbox='0 0 1000 1000'><p>Готовый текст 125 480</p></div>")
  try:
   for i in range(0,len(raw),7):
    event={'choices':[{'delta':{'content':raw[i:i+7]},'finish_reason':None}]}
    self.wfile.write(('data: '+json.dumps(event,ensure_ascii=False)+'\n\n').encode());self.wfile.flush()
   self.wfile.write(b'data: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n');self.wfile.flush()
  except (BrokenPipeError,ConnectionResetError):pass
ThreadingHTTPServer(('127.0.0.1',int(arg('--port'))),Handler).serve_forever()
