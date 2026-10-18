const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../app/src/main/assets'),out=path.resolve(__dirname,'../build/catalogue-ui');fs.mkdirSync(out,{recursive:true});
const models=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../distribution/model-catalog.json'),'utf8')).models;
(async()=>{
 const server=http.createServer((req,res)=>{const file=path.resolve(root,decodeURIComponent(req.url.split('?')[0]).replace(/^\//,''));if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.ttf')?'font/ttf':'text/html');fs.createReadStream(file).pipe(res);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:1});let errors=[],checks=0;page.on('pageerror',e=>errors.push(e.message));const check=(v,m)=>{assert(v,m);checks++;};
 await page.addInitScript(({models})=>{
  const config={modelName:'',projectorName:'',backend:'cpu',contextPerSlot:2048,slots:1,threads:4,gpuLayers:99,budgetMiB:0,systemReserveMiB:1536,unknownKvMiB:1024,port:8080,cacheK:'f16',cacheV:'f16',mmap:true,lan:false,stayAwake:true,strictMemory:false,vulkanCompatibility:true,cpuAcceleration:true};
  const catalogue={revision:2026101801,models:models.map(m=>Object.assign({},m,{installed:false,selected:false,recommended:m.tier==='balanced',compatible:true,hasSpace:true,partial:0,warning:''})),download:{active:false,state:'idle'},refreshAvailable:false};
  window.qa={config,catalogue,calls:[],active:false,installed:[]};const q=window.qa;
  const settings=()=>({config:{...config},models:q.installed,projectors:[],backends:['cpu','vulkan'],active:q.active,ready:false,status:'Модель остановлена',availableBytes:4*1073741824,totalBytes:8*1073741824,importing:false,appearance:'light',busy:false,version:'0.8.0',catalogue});
  window.Studio={appearance(){return 'light';},ready(){setTimeout(()=>StudioEvents({type:'boot',data:{screen:'settings'}}),0);},call(request,action,raw){const a=JSON.parse(raw);q.calls.push({action,args:a});let value={ok:true};
   if(action==='config')value=settings();
   if(action==='snapshot')value={busy:false,recording:false,modelAvailable:!!config.modelName,library:{items:[],more:false,query:''}};
   if(action==='downloadModel'){catalogue.download={active:true,state:'downloading',stage:'Загружаем',id:a.id,done:100000,total:models.find(m=>m.id===a.id).bytes};catalogue.models.find(m=>m.id===a.id).partial=100000;}
   if(action==='pauseModelDownload'){catalogue.download.active=false;catalogue.download.state='paused';catalogue.download.stage='Можно продолжить загрузку';}
   if(action==='useCatalogueModel'){const m=catalogue.models.find(m=>m.id===a.id);config.modelName=m.filename;config.backend='cpu';config.contextPerSlot=m.context;catalogue.models.forEach(x=>x.selected=x.id===a.id);value=settings();}
   if(action==='theme')value=settings();
   setTimeout(()=>StudioEvents({type:'reply',data:{request,value}}),0);
  }};
 },{models});
 try{
  await page.goto('http://127.0.0.1:'+server.address().port+'/studio.html');await page.locator('.catalogue-card').first().waitFor();
  check(await page.locator('.catalogue-card').count()===3,'three catalogue models');check((await page.locator('.catalogue-card.recommended').innerText()).includes('2B'),'balanced recommendation');
  check(await page.getByRole('button',{name:'Импортировать свой GGUF',exact:true}).isEnabled(),'custom import retained');await page.screenshot({path:out+'/catalogue-light.png',fullPage:true});
  await page.locator('.catalogue-card.recommended').getByRole('button',{name:'Скачать',exact:true}).click();check((await page.locator('#sheetContent').innerText()).includes('Wi-Fi'),'download confirmation discloses network');await page.getByRole('button',{name:'Загрузить',exact:true}).click();await page.getByRole('button',{name:'Приостановить',exact:true}).waitFor();
  check(await page.evaluate(()=>qa.calls.some(c=>c.action==='downloadModel'&&c.args.id===qa.catalogue.models[1].id)),'download uses pinned model ID');
  await page.getByRole('button',{name:'Приостановить',exact:true}).focus();await page.evaluate(()=>{qa.catalogue.download.done=40000000;qa.catalogue.models[1].partial=40000000;});await page.waitForTimeout(2200);check(await page.evaluate(()=>document.activeElement.textContent.includes('Приостановить')),'progress polling preserves focused control');await page.screenshot({path:out+'/catalogue-progress.png',fullPage:true});
  await page.getByRole('button',{name:'Приостановить',exact:true}).click();await page.getByRole('button',{name:'Продолжить загрузку',exact:true}).waitFor();check(await page.locator('#catalogueProgress').isVisible(),'paused download visible');
  await page.getByRole('button',{name:'Продолжить загрузку',exact:true}).click();check((await page.locator('#sheetContent').innerText()).includes('Уже сохранено'),'resume explains retained bytes');await page.getByRole('button',{name:'Загрузить',exact:true}).click();
  await page.evaluate(()=>{const m=qa.catalogue.models[1];m.installed=true;qa.installed=[{name:m.filename,bytes:m.bytes}];qa.catalogue.download={active:false,state:'done',id:m.id,done:m.bytes,total:m.bytes};});await page.getByRole('button',{name:'Использовать',exact:true}).waitFor();await page.getByRole('button',{name:'Использовать',exact:true}).click();await page.waitForFunction(()=>qa.config.modelName&&document.querySelector('.catalogue-card.recommended').textContent.includes('Выбрана'));
  check(await page.evaluate(()=>qa.config.backend==='cpu'&&qa.config.contextPerSlot===2048),'safe default profile after selection');
  await page.evaluate(()=>{qa.catalogue.models[2].compatible=false;qa.catalogue.models[2].warning='Для этой модели нужно больше оперативной памяти';qa.catalogue.models[0].hasSpace=false;qa.catalogue.models[0].description='<img src=x onerror="window.attacker=true">';});await page.waitForTimeout(2200);
  check(await page.locator('[data-model-id="'+models[2].id+'"]').getByRole('button',{name:'Скачать',exact:true}).isDisabled(),'unsupported memory cannot download');check(await page.locator('[data-model-id="'+models[0].id+'"]').getByRole('button',{name:'Скачать',exact:true}).isDisabled(),'storage gate visible');check(await page.locator('.catalogue-card img').count()===0&&!await page.evaluate(()=>window.attacker),'catalogue text escaped');
  for(const size of [{width:320,height:640},{width:768,height:1024}]){await page.setViewportSize(size);await page.evaluate(()=>{document.documentElement.style.fontSize='130%';PocketTheme.set('dark');});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'catalogue fits '+size.width);}
  await page.setViewportSize({width:393,height:852});await page.screenshot({path:out+'/catalogue-dark.png',fullPage:true});check(errors.length===0,'no script errors: '+errors.join('; '));console.log('PASS:',checks,'catalogue UI assertions');
 }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exit(1);});
