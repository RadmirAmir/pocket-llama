const { chromium } = require('playwright');
const path = require('node:path');
const assert = require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:2});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.calls=[];window.saved='';window.stopCount=0;
  window.Pocket={
   ready(){setTimeout(()=>{window.PocketEvents({type:'boot',data:{messages:[]}});window.PocketEvents({type:'state',data:{ready:true,vision:false,model:'Qwen3.5-4B-Q8_0-1234abcd.gguf',context:8192,backend:'cpu'}});},0);},
   save(text){window.saved=text;},refresh(){},
   pick(image){window.picked=image;},
   send(id,text){window.calls.push({id,body:JSON.parse(text)});},
   stop(){window.stopCount++;var id=window.calls[window.calls.length-1].id;setTimeout(()=>window.PocketEvents({type:'complete',data:{id,reason:'cancelled',usage:{completion_tokens:7}}}),10);},
   newChat(){window.PocketEvents({type:'reset',data:{}});},
   settings(){window.openedSettings=true;},back(){window.backPressed=true;},startModel(){window.startModelPressed=true;},
   copy(text){window.copied=text;},report(id){window.reported=id;}
  };
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/chat.html'));
 await page.waitForFunction(()=>document.getElementById('status').textContent==='Модель готова');
 assert(await page.locator('#image').isDisabled());
 await page.evaluate(()=>window.PocketEvents({type:'attachment',data:{id:'file1',kind:'text',name:'Протокол.txt',bytes:32,text:'Основное решение: согласовано.'}}));
 await page.locator('#input').fill('Сделай резюме.');
 await page.locator('#send').click();
 let sent=await page.evaluate(()=>window.calls[0]);
 assert.equal(sent.body.messages.length,2);
 assert(sent.body.messages[1].content.includes('Основное решение: согласовано.'));
 assert.equal(sent.body.thinking,false);
 assert(await page.locator('#file').isDisabled());
 await page.evaluate(id=>{window.firstUser=document.querySelector('.message.user');window.PocketEvents({type:'progress',data:{id,prompt:{total:100,cache:20,processed:30}}});},sent.id);
 await page.waitForFunction(()=>document.querySelector('.live-note').textContent.includes('50 / 100'));
 await page.evaluate(id=>window.PocketEvents({type:'chunk',data:{id,content:'',reasoning:'Короткая проверка.'}}),sent.id);
 await page.waitForFunction(()=>document.querySelector('.live-note').textContent.includes('Модель размышляет'));
 await page.locator('.reasoning summary').click();
 await page.evaluate(id=>window.PocketEvents({type:'chunk',data:{id,content:'**Решение принято.**\n<img src=x onerror="window.attacker=true">',reasoning:' Продолжаю.'}}),sent.id);
 await page.waitForFunction(()=>document.querySelector('#messages').textContent.includes('Решение принято.'));
 assert.equal(await page.locator('#messages img').count(),0);
 assert.equal(await page.evaluate(()=>window.attacker),undefined);
 assert(await page.evaluate(()=>window.firstUser===document.querySelector('.message.user')),'Streaming must preserve previous message DOM');
 assert(await page.locator('.reasoning').evaluate(e=>e.open),'Streaming must preserve expanded reasoning');
 await page.locator('#send').click();
 await page.waitForFunction(()=>document.querySelector('#messages').textContent.includes('Ответ остановлен'));
 assert.equal(await page.evaluate(()=>window.stopCount),1);
 assert.equal(await page.evaluate(()=>window.reported),undefined);
 await page.getByRole('button',{name:'Сообщить о проблеме',exact:true}).first().click();
 assert.equal(await page.evaluate(()=>window.reported),sent.id);
 await page.evaluate(()=>window.PocketEvents({type:'state',data:{ready:true,vision:true,model:'Qwen3.5-4B-Q8_0-1234abcd.gguf',context:8192,backend:'cpu'}}));
 assert(!(await page.locator('#image').isDisabled()));
 const png='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0t8AAAAASUVORK5CYII=';
 await page.evaluate(url=>window.PocketEvents({type:'attachment',data:{id:'image1',kind:'image',name:'Фото.png',bytes:80,width:1,height:1,data_url:url}}),png);
 await page.evaluate(()=>window.PocketEvents({type:'attachment',data:{id:'file2',kind:'text',name:'Описание.md',bytes:55,text:'Это приложенная подпись к изображению.'}}));
 await page.locator('#input').fill('Что изображено?');
 await page.screenshot({path:path.resolve(__dirname,'../build/chat-qa.png'),fullPage:true});
 await page.locator('#send').click();
 sent=await page.evaluate(()=>window.calls[1]);
 const parts=sent.body.messages[sent.body.messages.length-1].content;
 assert(Array.isArray(parts));
 assert(parts[0].text.includes('приложенная подпись'));
 assert.equal(parts[1].image_url.url,png);
 assert.equal(parts[1].type,'image_url');
 await page.evaluate(id=>window.PocketEvents({type:'failed',data:{id,message:'Тестовая ошибка контекста'}}),sent.id);
 await page.locator('.retry-button').click();
 assert.equal(await page.evaluate(()=>window.calls.length),3);
 sent=await page.evaluate(()=>window.calls[2]);
 assert.equal(sent.body.messages.filter(m=>m.role==='user').length,2);
 await page.evaluate(id=>{window.PocketEvents({type:'chunk',data:{id,content:'На изображении тестовый пиксель.'}});window.PocketEvents({type:'complete',data:{id,reason:'stop',usage:{completion_tokens:9},metrics:{predicted_per_second:7.5,first_text_ms:2500}}});},sent.id);
 await page.waitForFunction(()=>document.querySelector('#messages').textContent.includes('тестовый пиксель'));
 let history=JSON.parse(await page.evaluate(()=>window.saved));
 assert.equal(history.messages.length,4);
 assert.equal(history.messages[3].content,'На изображении тестовый пиксель.');
 assert.equal(history.messages[3].metrics.predicted_per_second,7.5);
 assert(await page.locator('#messages').textContent().then(t=>t.includes('7.5 ток/с')&&t.includes('первый текст 2.5 с')));
 await page.locator('#input').fill('Черновик сообщения');
 await page.waitForTimeout(850);
 history=JSON.parse(await page.evaluate(()=>window.saved));
 assert.equal(history.draft,'Черновик сообщения');
 await page.locator('#new').click();
 assert.equal(await page.locator('.message').count(),0);
 await page.evaluate(history=>window.PocketEvents({type:'boot',data:history}),history);
 assert.equal(await page.locator('.message').count(),4);
 assert.equal(await page.locator('#input').inputValue(),'Черновик сообщения');
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth);
 assert(!overflow,'Mobile layout must not overflow horizontally');
 await page.evaluate(()=>window.PocketEvents({type:'state',data:{ready:false,active:false,model:'Qwen.gguf',context:2048,backend:'cpu'}}));
 await page.locator('#activateModel').click();assert(await page.evaluate(()=>window.startModelPressed),'Model activation');
 await page.locator('#back').click();assert(await page.evaluate(()=>window.backPressed),'Back to recordings');
 assert.deepEqual(errors,[]);
 await browser.close();
 console.log('PASS: chat UI text/image payloads, streaming, cancellation, retry, draft/history, safe rendering, mobile layout');
})().catch(error=>{console.error(error);process.exit(1);});
