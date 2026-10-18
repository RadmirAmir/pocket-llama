#!/usr/bin/env python3
"""Real pinned VLM, CPU-only: compare legacy block work and single-page OCR.

Host measurements are not predictions of Android latency. --quantized evaluates
an experimental local quantization without changing Android's pinned model.
"""
import argparse,base64,io,json,subprocess,time,urllib.request
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/surya-performance';OUT.mkdir(parents=True,exist_ok=True)
P=argparse.ArgumentParser(description=__doc__);P.add_argument('--server',required=True);P.add_argument('--model',required=True);P.add_argument('--mmproj',required=True);P.add_argument('--quantized');A=P.parse_args()
PAGE='OCR this image to HTML. Each block is a div with data-label and data-bbox (x0 y0 x1 y1, normalized 0-1000).'
LAYOUT='Output the layout of this image as JSON. Each entry is a dict with "label", "bbox", and "count" fields. Bbox is x0 y0 x1 y1, normalized 0-1000.'
BLOCK='OCR this block image to HTML.'
font=lambda n:ImageFont.truetype(str(ROOT/'app/src/main/assets/fonts/Manrope.ttf'),n)
page=Image.new('RGB',(900,1200),'white');d=ImageDraw.Draw(page);d.text((50,90),'Счёт № 7 / Invoice',font=font(30),fill='black')
xs=[50,450,630,850];ys=[300,400,500,600,700]
for x in xs:d.line((x,ys[0],x,ys[-1]),fill='black',width=2)
for y in ys:d.line((xs[0],y,xs[-1],y),fill='black',width=2)
for r,row in enumerate([['Товар','Кол-во','Сумма'],['Бумага','2','500 ₽'],['Ручка','1','100 ₽'],['Итого','','600 ₽']]):
 for c,text in enumerate(row):d.text((xs[c]+14,ys[r]+29),text,font=font(30),fill='black')
page.save(OUT/'invoice.png')
text=Image.new('RGB',(1600,2200),'white');d=ImageDraw.Draw(text);d.text((100,100),'План работы / Project plan',font=font(46),fill='black')
paragraphs=['1. Подготовить документы к 25 октября 2026 года.','2. Проверить сумму договора: 125 480 рублей.','3. Ответственный: Иван Петров. Телефон: 123-45-67.','4. Сохранить оригиналы и подписанные копии.']
for i,line in enumerate(paragraphs):d.text((100,280+i*160),line,font=font(32),fill='black')
d.text((100,1300),'Примечание: номер заявки 904172, срок 15 дней.',font=font(24),fill='black');text.save(OUT/'text.png')

http=urllib.request.build_opener(urllib.request.ProxyHandler({}));results=[]
def serve(model,label,old=False):
 log=open(OUT/(label+'.log'),'w');args=[A.server,'--model',model,'--mmproj',A.mmproj,'--no-mmproj-offload','--device','none','--ctx-size','16384','--parallel','1','--threads','4','--threads-batch','4','--batch-size','128' if old else '512','--ubatch-size','32' if old else '128','--cache-ram','0','--ctx-checkpoints','1','--no-cache-idle-slots','--no-webui','--jinja','--reasoning-budget','0','--image-min-tokens','1024' if old else '128','--image-max-tokens','4096','--host','127.0.0.1','--port','18093']
 process=subprocess.Popen(args,stdout=log,stderr=subprocess.STDOUT)
 for _ in range(240):
  if process.poll() is not None:raise RuntimeError('Native exit; see '+label+'.log')
  try:http.open('http://127.0.0.1:18093/props',timeout=1);return process,log
  except Exception:time.sleep(.25)
 process.terminate();raise TimeoutError('Server startup')
def ask(image,prompt,label,old=False,tokens=8192):
 if not old:
  cap=2097152 if prompt==PAGE else 1048576 if prompt==LAYOUT else 4194304
  scale=min(1,(cap/(image.width*image.height))**.5)
  if image.width*image.height<50176:scale=(50176/(image.width*image.height))**.5
  image=image.resize((max(28,round(image.width*scale/28)*28),max(28,round(image.height*scale/28)*28)))
 b=io.BytesIO();image.save(b,format='JPEG',quality=95)
 body={'messages':[{'role':'user','content':[{'type':'image_url','image_url':{'url':'data:image/jpeg;base64,'+base64.b64encode(b.getvalue()).decode()}},{'type':'text','text':prompt}]}],'max_tokens':tokens,'temperature':0,'top_p':.1,'min_p':0,'repeat_penalty':1.,'cache_prompt':False,'reasoning_budget_tokens':0,'reasoning_effort':'none','chat_template_kwargs':{'enable_thinking':False}}
 t=time.monotonic();response=json.load(http.open(urllib.request.Request('http://127.0.0.1:18093/v1/chat/completions',json.dumps(body).encode(),{'Content-Type':'application/json'}),timeout=600));wall=time.monotonic()-t
 result={'label':label,'seconds':wall,'width':image.width,'height':image.height,'response':response};results.append(result);(OUT/(label+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2))
 c=response['choices'][0];assert c['finish_reason']=='stop',c
 print(label,round(wall,2),response.get('usage'),flush=True);return c['message']['content']
def stop(pair):
 p,log=pair;p.terminate()
 try:p.wait(timeout=15)
 except subprocess.TimeoutExpired:p.kill();p.wait()
 log.close()

server=serve(A.model,'f16-fast')
try:
 full=ask(page,PAGE,'f16-invoice');assert all(s in full for s in ['Счёт','Invoice','Бумага','500','100','600','Итого']),full
 full=ask(text,PAGE,'f16-text');assert all(s in full for s in ['25 октября 2026','125','480','Иван Петров','904172','15 дней']),full
 header=ask(page.crop((40,80,650,150)),BLOCK,'f16-small-block',tokens=256);assert 'Invoice' in header and 'Счёт' in header,header
finally:stop(server)
server=serve(A.model,'f16-legacy',True)
try:
 layout=json.loads(ask(page,LAYOUT,'legacy-layout',True,tokens=3072))
 for i,b in enumerate(layout):
  if b['label'] in ['Image','Figure','Diagram','Blank-Page']:continue
  coords=[float(v) for v in b['bbox'].split()];crop=page.crop(tuple(round(v*([page.width,page.height][j%2])/1000) for j,v in enumerate(coords)))
  ask(crop,BLOCK,'legacy-block-'+str(i),True,tokens=4096)
finally:stop(server)
if A.quantized:
 server=serve(A.quantized,'q8-fast')
 try:
  full=ask(page,PAGE,'q8-invoice');assert all(s in full for s in ['Бумага','500','100','600','Итого']),full
  full=ask(text,PAGE,'q8-text');assert all(s in full for s in ['25 октября 2026','Иван Петров','904172','15 дней']),full
 finally:stop(server)
(OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print('Real VLM quality/performance checks passed; results in',OUT,flush=True)
