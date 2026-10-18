import subprocess,time,json,urllib.request,base64,io,argparse
from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/surya-live';OUT.mkdir(parents=True,exist_ok=True)
p=argparse.ArgumentParser();p.add_argument('--server',required=True);p.add_argument('--model',required=True);p.add_argument('--mmproj',required=True);args=p.parse_args()
im=Image.new('RGB',(900,1200),'white');d=ImageDraw.Draw(im);font=ImageFont.truetype(str(ROOT/'app/src/main/assets/fonts/Manrope.ttf'),30);d.text((50,90),'Счёт № 7 / Invoice',font=font,fill='black')
xs=[50,450,630,850];ys=[300,400,500,600,700]
for x in xs:d.line((x,ys[0],x,ys[-1]),fill='black',width=2)
for y in ys:d.line((xs[0],y,xs[-1],y),fill='black',width=2)
rows=[['Товар','Кол-во','Сумма'],['Бумага','2','500 ₽'],['Ручка','1','100 ₽'],['Итого','','600 ₽']]
for row,values in enumerate(rows):
 for col,text in enumerate(values):d.text((xs[col]+14,ys[row]+29),text,font=font,fill='black')
im.save(OUT/'page.png');crop=im.crop((45,295,855,705));crop.save(OUT/'table.png')
http=urllib.request.build_opener(urllib.request.ProxyHandler({}));p=subprocess.Popen([args.server,'--model',args.model,'--mmproj',args.mmproj,'--no-mmproj-offload','--device','none','--ctx-size','16384','--parallel','1','--threads','4','--batch-size','128','--ubatch-size','32','--cache-ram','0','--ctx-checkpoints','1','--no-cache-idle-slots','--no-webui','--jinja','--reasoning-budget','0','--image-min-tokens','1024','--image-max-tokens','4096','--port','18093','--host','127.0.0.1'],stdout=open(OUT/'native.log','w'),stderr=subprocess.STDOUT)
def ask(image,prompt,task,schema=None,budget=4096):
 buffer=io.BytesIO();image.save(buffer,format='JPEG',quality=95);data=base64.b64encode(buffer.getvalue()).decode();body={'model':'mobile-llama','messages':[{'role':'user','content':[{'type':'image_url','image_url':{'url':'data:image/jpeg;base64,'+data}},{'type':'text','text':prompt}]}],'max_tokens':budget,'temperature':0,'top_p':.1,'min_p':0,'repeat_penalty':1.,'cache_prompt':False,'reasoning_budget_tokens':0,'reasoning_effort':'none','chat_template_kwargs':{'enable_thinking':False}}
 if schema:body['response_format']={'type':'json_schema','json_schema':{'name':task,'strict':True,'schema':schema}}
 t=time.monotonic();result=json.load(http.open(urllib.request.Request('http://127.0.0.1:18093/v1/chat/completions',json.dumps(body).encode(),{'Content-Type':'application/json'}),timeout=600));(OUT/(task+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2));c=result['choices'][0];assert c['finish_reason']=='stop',c;print(task,round(time.monotonic()-t,1),c['message']['content'],flush=True);return c['message']['content']
try:
 for i in range(120):
  if p.poll() is not None:raise RuntimeError('Native exit')
  try:http.open('http://127.0.0.1:18093/props',timeout=1);break
  except Exception:time.sleep(.25)
 labels=['Caption','Footnote','Equation-Block','List-Group','Page-Header','Page-Footer','Image','Section-Header','Table','Text','Complex-Block','Code-Block','Form','Table-Of-Contents','Figure','Chemical-Block','Diagram','Bibliography','Blank-Page']
 def schema(table=False):
  fields={'label':{'type':'string','enum':['Row','Col'] if table else labels},'bbox':{'type':'string','pattern':'^[0-9]{1,4} [0-9]{1,4} [0-9]{1,4} [0-9]{1,4}$'}};required=['label','bbox']
  if not table:fields['count']={'type':'integer','minimum':0,'maximum':10000};required.append('count')
  return {'type':'array','maxItems':200,'items':{'type':'object','properties':fields,'required':required,'additionalProperties':False}}
 layout=json.loads(ask(im,'Output the layout of this image as JSON. Each entry is a dict with "label", "bbox", and "count" fields. Bbox is x0 y0 x1 y1, normalized 0-1000.','layout',schema()));assert any(x['label']=='Table' for x in layout),layout
 structure=json.loads(ask(crop,'Output the table rows then columns as JSON. Each entry is a dict with "label" ("Row" or "Col") and "bbox" (x0 y0 x1 y1, normalized 0-1000).','table',schema(True)));assert sum(x['label']=='Row' for x in structure)==4 and sum(x['label']=='Col' for x in structure)==3,structure
 ocr=ask(crop,'OCR this block image to HTML.','ocr');assert all(s in ocr for s in ['Бумага','500','100','600','Итого']),ocr
 header=ask(im.crop((40,80,650,150)),'OCR this block image to HTML.','header',budget=256);assert 'Invoice' in header and 'Счёт' in header
 print('Surya strict layout, 4x3 table structure, Russian table OCR and 256-token block passed',flush=True)
finally:p.terminate();p.wait(timeout=10)
