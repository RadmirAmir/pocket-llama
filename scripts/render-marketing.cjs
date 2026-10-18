// Documentation previews only. Real web UI, synthetic content, no native bridges or model inference.
// Requires Playwright. CHAT_QA_BROWSER may point to an installed Chromium executable.
const {chromium} = require('playwright');
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const base = path.resolve(__dirname, '..'), assets = path.join(base, 'app/src/main/assets');
const out = path.join(base, 'docs/media');
fs.mkdirSync(out, {recursive:true});
const ID = 'e105e1c0-39f6-4355-a467-dbe017099d7f';
const paper = `<svg xmlns="http://www.w3.org/2000/svg" width="900" height="1200"><rect width="900" height="1200" fill="#fff"/><g fill="#252a27" font-family="sans-serif"><text x="75" y="140" font-size="44">Счёт за услуги</text><text x="75" y="220" font-size="25">15 октября 2026 · № 042</text><path d="M75 270H825" stroke="#ccc"/><text x="75" y="350" font-size="29">Дизайн печатных материалов</text><text x="75" y="410" font-size="29">Подготовка макета · 1 шт.</text><text x="75" y="540" font-size="34">Итого: 5 000 ₽</text><text x="75" y="650" font-size="24">Спасибо за сотрудничество.</text></g></svg>`;
function studioFixture({id}) {
  const meeting = {id, title:'Планы на большой запуск',created:1792068000000,samples:17280000,state:'done',detail:'Готово',progress:1,diarize:1,expected:0,vad_done:1,speakers_done:1,asr_done:1,turnCount:3,summaryTag:'demo',has_summary:true,names:['Спикер не определён','Аня','Михаил'],summary:'## Главное\n- Открываем ранний доступ в пятницу. [#1]\n- Первый запуск должен занимать три понятных шага. [#2]\n\n## Следующие шаги\n- Аня: подготовить короткую инструкцию. [#3]\n- Михаил: проверить установку на телефоне. [#3]'};
  const turns = [
    {index:1,start:0,end:8000,speaker:1,name:'Аня',text:'Давайте откроем ранний доступ в пятницу. Хочется, чтобы люди сразу поняли, зачем им приложение.'},
    {index:2,start:8200,end:18000,speaker:2,name:'Михаил',text:'Согласен. Первый запуск — три понятных шага: установить, выбрать модель и начать пользоваться.'},
    {index:3,start:18500,end:27000,speaker:1,name:'Аня',text:'Я подготовлю инструкцию. Михаил, проверь установку на телефоне до четверга.'}
  ];
  const config = {modelName:'Qwen3.5-2B-Q5_K_M-3f50d5f8.gguf',projectorName:'',backend:'cpu',contextPerSlot:2048,slots:1,threads:4,gpuLayers:0,budgetMiB:0,systemReserveMiB:1536,unknownKvMiB:1024,port:8080,cacheK:'f16',cacheV:'f16',mmap:true,lan:false,stayAwake:true,strictMemory:false};
  const items = [{...meeting,speakers:2},{...meeting,id:id.slice(0,-1)+'0',title:'Лекция о внимании',samples:24960000,created:1792064400000,has_summary:false,speakers:1},{...meeting,id:id.slice(0,-1)+'1',title:'Идея, которую нельзя забыть',samples:1248000,created:1791981600000,state:'ready',asr_done:0,has_summary:false,speakers:0}];
  window.Studio = {appearance(){return 'light';}, ready(){setTimeout(()=>StudioEvents({type:'boot',data:{screen:'library'}}),0);}, call(request,action,raw){
    let value = {ok:true};
    if(action==='snapshot') value={busy:false,recording:false,modelAvailable:true,library:{items,more:false,query:''},meeting:{...meeting,turns,notes:[],names:meeting.names}};
    if(action==='detail') value=meeting;
    if(action==='turns') value={id,query:'',offset:0,total:turns.length,turns};
    if(action==='source') value=turns[0];
    if(action==='config') value={config,models:[{name:config.modelName,bytes:1568476256}],projectors:[],backends:['cpu'],active:false,ready:false,status:'Модель остановлена',availableBytes:4*1073741824,totalBytes:8*1073741824,importing:false,appearance:'light',busy:false,version:'0.8.0'};
    setTimeout(()=>StudioEvents({type:'reply',data:{request,value}}),0);
  }};
}
function chatFixture() {
  window.Pocket={ready(){setTimeout(()=>{
    PocketEvents({type:'boot',data:{messages:[
      {role:'user',id:'demo-user',content:'Помоги составить план встречи по запуску проекта.',view_text:'Помоги составить план встречи по запуску проекта.'},
      {role:'assistant',id:'demo-assistant',content:'**Три вопроса для команды**\n\n1. Кому помогаем и какую задачу решаем?\n2. Что человек должен успеть за первые пять минут?\n3. Кто отвечает за инструкцию, тестирование и обратную связь?\n\nВ конце зафиксируйте три ближайших действия и сроки.'}
    ]}});
    PocketEvents({type:'state',data:{ready:true,vision:false,model:'Qwen3.5-2B-Q5_K_M-3f50d5f8.gguf',context:2048,backend:'cpu'}});
  },0);},save(){},refresh(){},pick(){},send(){},stop(){},newChat(){},settings(){},back(){},startModel(){},copy(){},report(){}};
}
function photoFixture() {
  const doc={id:'11111111-1111-4111-8111-111111111111',title:'Счёт за дизайн',created:1792068000000,updated:1,state:'done',progress:1,engine:'paddle',detail:'Документ распознан · 1 стр.'};
  const page={id:'22222222-2222-4222-8222-222222222221',title:'Страница 1',width:900,height:1200,renderWidth:900,renderHeight:1200,rotation:0,created:1792068000000,updated:1,bytes:240000,state:'done',progress:1,engine:'paddle',text:'Счёт за услуги\n15 октября 2026 · № 042\nДизайн печатных материалов\nПодготовка макета · 1 шт.\nИтого: 5 000 ₽\nСпасибо за сотрудничество.',edited:false,lines:[]};
  window.Photos={appearance(){return 'light';},ready(){setTimeout(()=>PhotoEvents({type:'boot',data:{token:'documentation-demo',model:{available:false,ready:false}}}),0);},call(request,action){
    let value={ok:true};
    if(action==='list')value={items:[{...doc,cover:page.id,pageCount:1,doneCount:1}],more:false};
    if(action==='detail')value={document:doc,pages:[page],model:{available:false,ready:false}};
    if(action==='page')value=page;
    setTimeout(()=>PhotoEvents({type:'reply',data:{request,value}}),0);
  }};
}
const mark = '<svg viewBox="0 0 50 50" aria-hidden="true"><path d="M5 20v10m10-19v28m20-25v22m10-16v10"/><path class="red" d="M25 4v42"/></svg>';
function hero(locale) {
  const ru=locale==='ru';
  return `<!doctype html><meta charset="utf-8"><style>
  @font-face{font-family:Manrope;src:url('/fonts/Manrope.ttf')}*{box-sizing:border-box}body{margin:0;background:#f6f5ef;color:#252a27;font-family:Manrope,sans-serif}.canvas{width:1600px;height:900px;position:relative;overflow:hidden;padding:62px 76px;background:radial-gradient(ellipse at 85% 30%,#e9ece3,transparent 53%)}.brand{display:flex;align-items:center;gap:18px;font-size:47px;font-weight:800;letter-spacing:-2px}.brand svg{width:43px;height:43px;fill:none;stroke:#252a27;stroke-width:4;stroke-linecap:round}.red{stroke:#b84432}.dot{color:#b84432}.eyebrow{margin-top:66px;font-size:19px;font-weight:750;letter-spacing:3.3px;color:#496a59;text-transform:uppercase}h1{font-size:${ru?100:110}px;letter-spacing:-6.5px;line-height:1.07;margin:20px 0 26px;font-weight:800;position:relative;z-index:2}.sub{font-size:27px;line-height:1.6;color:#62685f;margin:0;max-width:690px}.strip{display:flex;gap:27px;position:absolute;left:76px;bottom:70px;font-size:18px;font-weight:700;align-items:center}.strip span+span{padding-left:27px;border-left:1px solid #cacfc4}.phone{position:absolute;right:158px;top:74px;width:328px;height:712px;padding:9px;background:#252a27;border:1px solid #555e54;border-radius:43px;box-shadow:0 35px 70px #1d2b242b;transform:rotate(6deg);z-index:2}.phone img{width:100%;height:100%;object-fit:cover;object-position:top;border-radius:34px}.back{right:-33px;top:170px;transform:rotate(-8deg);width:291px;height:632px;background:#99a78f;box-shadow:0 20px 50px #1d2b241a;opacity:.94;z-index:1}.privacy{position:absolute;right:120px;bottom:53px;z-index:3;display:flex;gap:17px;align-items:center;background:#fffefa;border:1px solid #dce1d4;border-radius:21px;padding:23px 27px;box-shadow:0 15px 40px #1d2b2412;font-size:20px;font-weight:750;transform:rotate(-3deg)}.privacy svg{width:27px;height:31px;stroke:#496a59;stroke-width:2;fill:none}.note{font-size:13px;color:#747b70;position:absolute;right:18px;top:20px;letter-spacing:1px}.ring{position:absolute;width:630px;height:630px;border-radius:50%;border:1px solid #d7ddcf;right:-130px;top:30px}.ring.second{width:780px;height:780px;right:-205px;top:-45px}
  </style><div class="canvas"><div class="ring"></div><div class="ring second"></div><div class="brand">${mark}<span>Pocket<span class="dot">.</span></span></div><div class="eyebrow">${ru?'Личный ИИ на вашем телефоне':'A personal AI that stays with you'}</div><h1>${ru?'Личный ИИ.<br>Только у вас.':'Your AI.<br>Your phone.<br>Your business.'}</h1><p class="sub">${ru?'Записывайте разговоры. Сканируйте документы.<br>Обсуждайте идеи с ИИ. Даже без интернета.':'Record conversations. Scan documents.<br>Think things through. Even offline.'}</p><div class="strip"><span>${ru?'Без аккаунта':'No account'}</span><span>${ru?'Без облачного ИИ':'No cloud AI'}</span><span>Open source</span></div><div class="phone back"><img src="/media/chat.png"></div><div class="phone"><img src="/media/recorder.png"></div><div class="privacy"><svg viewBox="0 0 24 28"><rect x="3" y="12" width="18" height="14" rx="3"/><path d="M7 12V8a5 5 0 0 1 10 0v4M12 17v5"/></svg>${ru?'Зашифровано на телефоне':'Encrypted on your phone'}</div><span class="note">${ru?'ДЕМО ИНТЕРФЕЙСА':'INTERFACE DEMO'}</span></div>`;
}
(async()=>{
  const server=http.createServer((req,res)=>{
    const name=decodeURIComponent(req.url.split('?')[0]);
    if(name.startsWith('/private/')){res.setHeader('Content-Type','image/svg+xml');return res.end(paper);}
    if(name==='/hero-en'||name==='/hero-ru'){res.setHeader('Content-Type','text/html');return res.end(hero(name.slice(-2)));}
    const root=name.startsWith('/media/')?out:assets;
    const file=path.resolve(root,name.replace(name.startsWith('/media/')?'/media/':'/',''));
    if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}
    const types={'.css':'text/css','.js':'text/javascript','.ttf':'font/ttf','.png':'image/png','.html':'text/html'};
    res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');fs.createReadStream(file).pipe(res);
  });
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const url='http://127.0.0.1:'+server.address().port;
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
  try {
    const errors=[];
    async function screen(fixture,file,selector){
      const p=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:2});
      p.on('pageerror',e=>errors.push(e.message));await p.addInitScript(fixture,{id:ID});
      await p.goto(url+'/'+file);await p.locator(selector).first().waitFor();await p.evaluate(()=>document.fonts.ready);return p;
    }
    const studio=await screen(studioFixture,'studio.html','.meeting-row');
    await studio.screenshot({path:out+'/library.png'});
    await studio.locator('.meeting-row').first().click();await studio.locator('.summary-prose').waitFor();
    await studio.screenshot({path:out+'/recorder.png'});
    const chat=await screen(chatFixture,'chat.html','.message.assistant');
    await chat.screenshot({path:out+'/chat.png'});
    const photos=await screen(photoFixture,'photos.html','.photo-card');
    await photos.screenshot({path:out+'/scanner.png'});
    for(const locale of ['en','ru']){
      const p=await browser.newPage({viewport:{width:1600,height:900},deviceScaleFactor:1});
      await p.goto(url+'/hero-'+locale);await p.evaluate(()=>document.fonts.ready);
      await p.locator('.phone img').first().evaluate(i=>i.decode());
      await p.locator('.phone img').nth(1).evaluate(i=>i.decode());
      await p.screenshot({path:out+'/hero-'+locale+'.png'});await p.close();
    }
    if(errors.length)throw new Error(errors.join('\n'));
    console.log('Rendered 4 UI previews and 2 hero images with synthetic content.');
  } finally {await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});
