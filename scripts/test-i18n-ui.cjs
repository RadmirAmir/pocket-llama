const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../app/src/main/assets'),dictionary=JSON.parse(fs.readFileSync(root+'/i18n-en.json','utf8'));
const models=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../distribution/model-catalog.json'),'utf8')).models;
(async()=>{
 const server=http.createServer((req,res)=>{const file=path.resolve(root,decodeURIComponent(req.url.split('?')[0]).slice(1));if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.ttf')?'font/ttf':'text/html');fs.createReadStream(file).pipe(res);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage({viewport:{width:393,height:852}}),calls=[],errors=[];page.on('pageerror',e=>errors.push(e.message));await page.exposeFunction('nativeCall',(action,args)=>calls.push({action,args}));
 await page.addInitScript(({dictionary,models})=>{
  const lang=()=>localStorage.getItem('test-language')||'ru',translations=()=>JSON.stringify(dictionary);
  const config={modelName:'Qwen3.5-2B-Q5_K_M-3f50d5f8.gguf',backend:'cpu',contextPerSlot:2048,slots:1,threads:4,lan:false,projectorName:'',cacheK:'f16',cacheV:'f16'};
  const meeting={id:'11111111-1111-4111-8111-111111111111',title:'Новая запись',created:1792068000000,samples:160000,state:'interrupted',asr_done:0,turnCount:1,asr_language:'ru',diarize:1,expected:0,summary:''};
  const settings=()=>({config,models:[{name:config.modelName,bytes:1568476256}],projectors:[],backends:['cpu'],active:true,ready:true,status:'Модель готова',availableBytes:4*1073741824,totalBytes:8*1073741824,appearance:'light',busy:false,version:'0.9.0',catalogue:{models:models.map(m=>({...m,compatible:true,hasSpace:true,recommended:m.tier==='balanced'})),download:{active:true,id:models[0].id,state:'downloading',stage:'Загружаем',done:1024,total:models[0].bytes}}});
  window.Studio={language:lang,translations,appearance(){return 'light';},ready(){setTimeout(()=>StudioEvents({type:'boot',data:{screen:localStorage.getItem('test-screen')||'settings'}}),0);},call(request,action,raw){let args=JSON.parse(raw),value={ok:true};nativeCall(action,args);
   if(action==='config')value=settings();
   if(action==='snapshot')value={busy:false,recording:false,modelAvailable:true,library:{items:[meeting],more:false}};
   if(action==='detail')value=meeting;
   if(action==='turns')value={id:meeting.id,turns:[{index:1,start:0,end:2000,speaker:1,name:'Анна',text:'Мой личный текст.'}],total:1};
   if(action==='language'){localStorage.setItem('test-language',args.value);localStorage.setItem('test-screen','settings');setTimeout(()=>location.reload(),30);}
   setTimeout(()=>StudioEvents({type:'reply',data:{request,value}}),0);
  }};
  window.Pocket={language:lang,translations,appearance(){return 'light';},ready(){setTimeout(()=>{PocketEvents({type:'boot',data:{messages:[{role:'user',content:'Новая запись',view_text:'Новая запись'},{role:'assistant',id:'saved',content:'Ваши документы'}]}});PocketEvents({type:'state',data:{ready:true,vision:false,model:config.modelName,context:2048,backend:'cpu'}});},0);},save(raw){window.saved=JSON.parse(raw);},send(id,raw){window.sent=JSON.parse(raw);},refresh(){},back(){},settings(){},pick(){},copy(){},report(){}};
  window.Photos={language:lang,translations,appearance(){return 'light';},ready(){setTimeout(()=>PhotoEvents({type:'boot',data:{token:'test',model:{available:false}}}),0);},call(request,action){let value={ok:true};if(action==='list')value={items:[{id:meeting.id,title:'Ваши документы',pageCount:1,doneCount:1,state:'done',created:meeting.created}],more:false};setTimeout(()=>PhotoEvents({type:'reply',data:{request,value}}),0);}};
 },{dictionary,models});
 try{
  const url='http://127.0.0.1:'+server.address().port;
  await page.goto(url+'/studio.html');await page.locator('[data-language=en]').waitFor();assert.equal(await page.locator('html').getAttribute('lang'),'ru');
  await page.locator('[data-language=en]').click();await page.waitForFunction(()=>document.documentElement.lang==='en');await page.getByText('Interface language',{exact:true}).waitFor();
  assert.equal(await page.locator('[data-language=en]').getAttribute('aria-pressed'),'true');assert((await page.locator('#settingsContent').innerText()).includes('Moonshine Streaming Medium'));
  assert((await page.locator('#settingsContent').innerText()).includes('Pause'));assert.equal(calls.filter(c=>['stopServer','cancel','pauseModelDownload'].includes(c.action)).length,0,'Language change must not stop services or downloads');
  await page.setViewportSize({width:320,height:640});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'English settings fit narrow phone');await page.setViewportSize({width:393,height:852});
  await page.locator('#navLibrary').click();await page.locator('.meeting-row').first().waitFor();assert((await page.locator('.meeting-row').first().innerText()).includes('Новая запись'),'User title matching a UI key must not be translated');
  await page.locator('.meeting-row').first().click();await page.locator('#meetingTitle').filter({hasText:'Новая запись'}).waitFor();assert((await page.locator('#meetingMeta').innerText()).includes('RU · GigaAM'),'A partial RU transcript keeps its engine');
  await page.goto(url+'/chat.html');await page.getByText('Model ready',{exact:true}).waitFor();assert((await page.locator('.message.user .prose').innerText()).includes('Новая запись'));assert((await page.locator('.message.assistant .prose').innerText()).includes('Ваши документы'));
  await page.locator('#input').fill('Оставь мой текст как есть.');await page.locator('#send').click();await page.waitForFunction(()=>window.sent);let sent=await page.evaluate(()=>window.sent);assert(sent.messages[0].content.includes('Reply in English'));assert(sent.messages.some(m=>m.content==='Оставь мой текст как есть.'));
  await page.goto(url+'/photos.html');await page.getByRole('button',{name:'Scan a document',exact:true}).waitFor();assert((await page.locator('.photo-card').innerText()).includes('Ваши документы'),'Document title must not be translated');
  await page.goto(url+'/studio.html');await page.locator('[data-language=ru]').click();await page.waitForFunction(()=>document.documentElement.lang==='ru');await page.getByText('Язык интерфейса',{exact:true}).waitFor();assert.equal(await page.locator('[data-language=ru]').getAttribute('aria-pressed'),'true');
  await page.reload();await page.getByText('Язык интерфейса',{exact:true}).waitFor();assert.equal(await page.locator('html').getAttribute('lang'),'ru');
  assert.deepEqual(errors,[]);console.log('PASS: EN/RU switch, reload persistence, active model/download preservation, English chat prompt, original user content and partial ASR language');
 }catch(error){console.error('Visible UI:',(await page.locator('body').innerText()).slice(0,6000));console.error('Page errors:',errors);throw error;}finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});
