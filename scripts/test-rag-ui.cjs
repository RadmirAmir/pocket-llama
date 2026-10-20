const { chromium } = require('playwright');
const path = require('node:path');
const assert = require('node:assert/strict');
async function run(){
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
 try{
  for(const language of ['ru','en']){
   const page=await browser.newPage({viewport:{width:320,height:740}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
   const dictionary=require('../app/src/main/assets/i18n-en.json');
   await page.addInitScript(({language,dictionary})=>{
    window.calls=[];window.Pocket={language(){return language;},translations(){return JSON.stringify(dictionary);},appearance(){return 'light';},ready(){setTimeout(()=>{PocketEvents({type:'boot',data:{messages:[]}});PocketEvents({type:'state',data:{ready:true,model:'local.gguf',context:4096,backend:'cpu'}});PocketEvents({type:'ragState',data:{modelReady:true,working:false,passages:230,sources:130}});},0);},
    save(raw){window.historySaved=JSON.parse(raw);},send(id,raw){window.sent={id,body:JSON.parse(raw)};},rag(action){calls.push(action);},openRagSource(raw){window.opened=JSON.parse(raw);},newChat(){PocketEvents({type:'reset',data:{}});},copy(){},refresh(){},pick(){},back(){},settings(){} };
   },{language,dictionary});
   await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/chat.html'));await page.waitForFunction(()=>document.getElementById('status').textContent==='Model ready'||document.getElementById('status').textContent==='Модель готова');
   await page.locator('#input').fill('Какая дата оплаты счета?');await page.locator('#send').click();
   let sent=await page.evaluate(()=>window.sent);assert.equal(sent.body.rag,false);assert(!('rag_query' in sent.body),'disconnected chat sends no knowledge query');
   await page.evaluate(id=>PocketEvents({type:'complete',data:{id,reason:'stop',usage:{}}}),sent.id);
   await page.locator('#tools').click();assert(await page.locator('#toolsPanel').isVisible());assert.equal(await page.locator('#ragEnabled').isChecked(),false);
   await page.locator('#ragEnabled').check();await page.locator('#closeTools').click();
   assert(await page.locator('#ragBadge').isVisible());await page.locator('#input').fill('Find invoice 773311');await page.locator('#send').click();
   sent=await page.evaluate(()=>window.sent);assert.equal(sent.body.rag,true);assert.equal(sent.body.rag_query,undefined,'the model chooses the knowledge query');assert(await page.locator('#tools').isDisabled());
   const source={citation:'S1',kind:'ocr',sourceId:'ba585069-fde2-4e86-ac81-4c8dddb8da6a',title:'<img src=x onerror="window.attacked=true">',text:'Invoice 773311 — pay by Friday. <script>window.attacked=true</script>',location:{page:2}};
   await page.evaluate(({id,source})=>{PocketEvents({type:'ragSources',data:{id,result:{sources:[source],contextLimited:false}}});PocketEvents({type:'chunk',data:{id,content:'Pay by Friday [S1].'}});PocketEvents({type:'complete',data:{id,reason:'stop',usage:{}}});},{id:sent.id,source});
   assert.equal(await page.locator('.citation').count(),1);await page.locator('.citation').click();assert.equal((await page.evaluate(()=>window.opened)).sourceId,source.sourceId);
   await page.locator('.rag-sources summary').click();assert((await page.locator('.rag-source').innerText()).includes('773311'));assert.equal(await page.evaluate(()=>window.attacked),undefined);assert.equal(await page.locator('.rag-source img,.rag-source script').count(),0);
   await page.evaluate(()=>document.documentElement.style.fontSize='160%');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'composer fits 320px at enlarged text');
   await page.locator('#tools').click();assert(await page.evaluate(()=>document.getElementById('toolsPanel').scrollWidth<=document.getElementById('toolsPanel').clientWidth),'tool dialog fits enlarged text');await page.locator('#closeTools').click();
   await page.locator('#new').click();assert.equal(await page.locator('#ragEnabled').isChecked(),false);assert(await page.locator('#ragBadge').isHidden());
   assert.deepEqual(errors,[]);await page.close();
  }
  console.log('PASS: RU/EN opt-in knowledge tool, disconnected requests, citations, safe source rendering and narrow layout');
 }finally{await browser.close();}
}
module.exports={run};if(require.main===module)run().catch(e=>{console.error(e);process.exitCode=1;});
