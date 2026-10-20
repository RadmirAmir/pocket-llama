const {chromium}=require('playwright');
const path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage({viewport:{width:393,height:852}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.calls=[];window.consent=[];
  window.Pocket={appearance(){return 'light';},ready(){setTimeout(()=>{PocketEvents({type:'boot',data:{messages:[],webConnected:true}});PocketEvents({type:'state',data:{ready:true,model:'Qwen.gguf',context:4096,backend:'cpu'}});PocketEvents({type:'ragState',data:{modelReady:true,working:false}});},0);},
   rag(){},save(text){window.saved=JSON.parse(text);},setWebSearch(enabled){window.consent.push(enabled);},send(id,text){window.calls.push({id,body:JSON.parse(text)});},
   openWebSource(url){window.openedUrl=url;},newChat(){PocketEvents({type:'reset',data:{}});},refresh(){},copy(){},report(){},stop(){},settings(){},back(){}};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/chat.html'));
 await page.getByText('Модель готова',{exact:true}).waitFor();
 await page.locator('#tools').click();assert(!(await page.locator('#webEnabled').isChecked()),'Persisted history cannot enable search');
 await page.locator('#webEnabled').click();assert(!(await page.locator('#webEnabled').isChecked()),'Checkbox waits for native consent');
 assert.deepEqual(await page.evaluate(()=>window.consent),[true]);
 await page.evaluate(()=>PocketEvents({type:'webState',data:{enabled:false}}));assert(!(await page.locator('#webEnabled').isChecked()));
 await page.locator('#webEnabled').click();await page.evaluate(()=>PocketEvents({type:'webState',data:{enabled:true}}));
 assert(await page.locator('#webEnabled').isChecked());assert(await page.locator('#webBadge').isVisible());
 assert((await page.locator('.footnote').innerText()).includes('ИНТЕРНЕТ'));
 await page.locator('#ragEnabled').check();await page.locator('#closeTools').click();
 await page.evaluate(()=>PocketEvents({type:'attachment',data:{id:'private',kind:'text',name:'Личный файл.txt',bytes:30,text:'PRIVATE_ATTACHMENT_123'}}));
 await page.locator('#input').fill('Когда появился SearXNG?');await page.locator('#send').click();
 const sent=await page.evaluate(()=>window.calls[0]);assert.equal(sent.body.web,true);assert.equal(sent.body.rag,true);
 assert.equal(sent.body.web_query,'Когда появился SearXNG?');assert(!sent.body.web_query.includes('PRIVATE_ATTACHMENT'));assert(sent.body.messages.some(m=>typeof m.content==='string'&&m.content.includes('PRIVATE_ATTACHMENT_123')),'Attachment remains available to local model');
 await page.evaluate(id=>PocketEvents({type:'webProgress',data:{id,detail:'SearXNG ищет в интернете…',query:'Когда появился SearXNG?'}}),sent.id);
 await page.waitForFunction(()=>document.querySelector('.live-note').textContent.includes('SearXNG ищет'));
 await page.evaluate(id=>{PocketEvents({type:'webSources',data:{id,result:{query:'Когда появился SearXNG?',sources:[{citation:'W1',title:'<img src=x onerror=alert(1)>',url:'https://example.org/search',text:'Quoted search snippet.'}],failedEngines:['google'],contextLimited:true}}});PocketEvents({type:'chunk',data:{id,content:'Подтверждено источником [W1].'}});PocketEvents({type:'complete',data:{id,reason:'stop',usage:{completion_tokens:8}}});},sent.id);
 await page.locator('.citation').click();assert.equal(await page.evaluate(()=>window.openedUrl),'https://example.org/search');
 assert.equal(await page.locator('.web-sources img').count(),0,'Untrusted result titles remain text');
 await page.locator('.web-sources summary').click();assert((await page.locator('.web-sources').innerText()).includes('Часть поисковиков недоступна: google'));
 assert.equal(await page.evaluate(()=>window.saved.messages.at(-1).web.sources.length),1,'Sources persist with encrypted chat history');
 await page.locator('#tools').click();await page.screenshot({path:path.resolve(__dirname,'../build/search-tools-qa.png'),fullPage:true});
 await page.setViewportSize({width:320,height:720});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Tools fit a narrow phone');
 await page.locator('#closeTools').click();await page.locator('#new').click();
 assert(await page.locator('#webBadge').isHidden());assert.equal(await page.evaluate(()=>window.consent.at(-1)),false,'New chat revokes native search permission');
 assert.deepEqual(errors,[]);await browser.close();
 console.log('PASS: native consent state, default off, RAG coexistence, query isolation, search progress, safe sources, citations and mobile layout');
})().catch(e=>{console.error(e);process.exit(1);});
