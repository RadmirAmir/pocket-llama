const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
module.exports=async function notesUI(){
 const root=path.resolve(__dirname,'../app/src/main/assets'),out=path.resolve(__dirname,'../build/notes-ux');fs.mkdirSync(out,{recursive:true});
 const dictionary=JSON.parse(fs.readFileSync(root+'/i18n-en.json','utf8'));
 const server=http.createServer((req,res)=>{const file=path.resolve(root,decodeURIComponent(req.url.split('?')[0]).slice(1));if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.ttf')?'font/ttf':'text/html');fs.createReadStream(file).pipe(res);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHAT_QA_BROWSER||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
 let checks=0;const check=(condition,label)=>{assert(condition,label);checks++;};
 try{for(const language of ['ru','en']){
  const page=await browser.newPage({viewport:{width:393,height:852}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(({dictionary,language})=>{
   const KEY='notes-test-store';
   let persisted=localStorage.getItem(KEY),notes=persisted?JSON.parse(persisted):[];
   const q=window.qa={notes,calls:[],editing:null,failSave:false,theme:'light'};
   const fold=s=>s.normalize('NFC').toLowerCase().replace(/ё/g,'е');
   function persist(){localStorage.setItem(KEY,JSON.stringify(notes));}
   function emit(type,data){window.NoteEvents({type,data});}
   function commit(){if(!q.editing||!q.editing.draft)return;if(q.failSave)throw new Error(language==='en'?'Test: storage full':'Тест: нет места');let e=q.editing,s=e.draft,n=notes.find(n=>n.id===e.id);n.title=s.title;n.body=s.body;n.updated=Date.now();n.revision++;e.draft=null;persist();emit('saved',{token:e.token,sequence:s.sequence,updated:n.updated});}
   const get=id=>notes.find(n=>n.id===id);
   window.Notes={language:()=>language,translations:()=>JSON.stringify(dictionary),appearance:()=>q.theme,ready(){setTimeout(()=>emit('boot',{}),0);},stage(token,sequence,title,body){if(!q.editing||q.editing.token!==token)return 'Editor closed';q.editing.draft={sequence,title,body};setTimeout(()=>{try{commit();}catch(e){emit('saveError',{token,message:e.message});}},200);return '';},call(request,action,raw){let a=JSON.parse(raw);q.calls.push({action,args:a});let value={ok:true},error;
    try{
     if(action==='flush'||action==='close'||action==='list'||action==='create'||action==='open'||action==='pin'||action==='trash'||action==='back')commit();
     if(action==='list'){let found=notes.filter(n=>(!!n.deleted===!!a.trash)&&(!a.query||fold(n.title+' '+n.body).includes(fold(a.query.trim())))).sort((a,b)=>Number(b.pinned)-Number(a.pinned)||b.updated-a.updated||a.id.localeCompare(b.id));value={items:found.slice(a.offset,a.offset+a.limit).map(n=>({...n,preview:n.body.slice(0,180)})),more:found.length>a.offset+a.limit,activeCount:notes.filter(n=>!n.deleted).length,trashCount:notes.filter(n=>n.deleted).length};}
     if(action==='create'){let now=Date.now(),n={id:crypto.randomUUID(),title:'',body:'',created:now,updated:now,pinned:false,deleted:0,revision:1};notes.push(n);value={...n,token:crypto.randomUUID()};q.editing={id:n.id,token:value.token};persist();}
     if(action==='open'){value={...get(a.id)};if(!value.deleted){value.token=crypto.randomUUID();q.editing={id:a.id,token:value.token};}}
     if(action==='pin'){get(a.id).pinned=a.pinned;value={...get(a.id)};persist();}
     if(action==='close')q.editing=null;
     if(action==='trash'){get(a.id).deleted=Date.now();q.editing=null;persist();}
     if(action==='restore'){get(a.id).deleted=0;persist();}
     if(action==='purge'){notes.splice(notes.findIndex(n=>n.id===a.id),1);persist();}
    }catch(e){error=e.message;}
    setTimeout(()=>emit('reply',{request,value,error}),a.query==='old'?450:0);
   }};
  },{dictionary,language});
  const ui=s=>language==='en'?(dictionary[s]||s):s;
  try{
   await page.goto('http://127.0.0.1:'+server.address().port+'/notes.html');await page.locator('.notes-empty').waitFor();
   check(await page.locator('html').getAttribute('lang')===language,'notes use '+language+' interface');
   check((await page.locator('#listTitle').innerText()).includes(ui('Ваши заметки')),'localized heading');
   await page.locator('#newNote').click();await page.locator('#noteTitle').waitFor({state:'visible'});
   await page.locator('#noteTitle').fill('Ваши заметки');
   const body='Личный ЁЖИК и plans <img src=x onerror="window.attacker=true">\n100%_literal\nСохранить оригинальный текст.';
   await page.locator('#noteBody').fill(body);await page.waitForFunction(()=>qa.notes.some(n=>n.body.includes('Сохранить оригинальный текст.')));
   check(await page.locator('#saveStatus').innerText()===ui('Сохранено'),'autosave acknowledges actual save');
   check(await page.locator('#noteTitle').inputValue()==='Ваши заметки','user title matching translation key remains verbatim');
   await page.screenshot({path:out+'/editor-'+language+'.png'});
   await page.locator('#editorBack').click();await page.locator('.note-card').waitFor();
   check(await page.locator('#noteList img').count()===0&&await page.evaluate(()=>!window.attacker),'stored note markup is escaped');
   await page.locator('#noteSearch').fill('ЕЖИК');await page.waitForTimeout(300);check(await page.locator('.note-card').count()===1,'body search is Russian case insensitive');
   await page.locator('#noteSearch').fill('%_literal');await page.waitForTimeout(300);check(await page.locator('.note-card').count()===1,'literal punctuation search');
   await page.locator('#noteSearch').fill('не найдено');await page.locator('.notes-empty').waitFor();check(await page.locator('.note-card').count()===0,'no-results state');
   await page.locator('#noteSearch').fill('old');await page.waitForTimeout(220);await page.locator('#noteSearch').fill('ЕЖИК');await page.waitForTimeout(750);check(await page.locator('.note-card').count()===1,'late search result cannot replace newer query');
   await page.locator('#clearSearch').click();await page.locator('.note-card').waitFor();await page.locator('.note-card').click();
   await page.locator('#pinNote').click();await page.waitForFunction(()=>document.querySelector('#pinNote').getAttribute('aria-pressed')==='true');check(await page.locator('#pinnedLabel').isVisible(),'pin state is visible and accessible');
   await page.locator('#noteBody').fill('Сохранить выделенный текст');await page.locator('#noteBody').evaluate(n=>n.setSelectionRange(2,6));await page.locator('#checklist').click();check(await page.locator('#noteBody').inputValue()==='☐ Сохранить выделенный текст','checklist preserves selected text');
   await page.locator('#noteBody').fill('');await page.locator('#checklist').click();await page.locator('#noteBody').pressSequentially('Первый пункт');await page.locator('#noteBody').press('Enter');await page.locator('#noteBody').pressSequentially('Второй пункт');
   check((await page.locator('#noteBody').inputValue()).includes('\n☐ Второй пункт'),'checklist continues on Enter');
   await page.locator('#noteBody').evaluate(n=>n.setSelectionRange(0,0));await page.locator('#checklist').click();check((await page.locator('#noteBody').inputValue()).startsWith('☑ '),'checklist action toggles current item');
   await page.locator('#noteBody').fill(body);await page.locator('#editorBack').click();await page.locator('.notes-group').filter({hasText:ui('Закреплённые')}).waitFor();
   await page.reload();await page.locator('.note-card').waitFor();await page.locator('.note-card').click();await page.locator('#editorScreen').waitFor({state:'visible'});check(await page.locator('#noteBody').inputValue()===body,'reopened note preserves body');
   await page.evaluate(()=>qa.failSave=true);await page.locator('#noteBody').fill('Несохранённая правка');await page.locator('#saveError').waitFor({state:'visible'});await page.locator('#editorBack').click();
   check(await page.locator('#editorScreen').isVisible()&&await page.locator('#noteBody').inputValue()==='Несохранённая правка','failed save keeps editor and typed content open');
   await page.evaluate(()=>qa.failSave=false);await page.locator('#retrySave').click();await page.locator('#saveError').waitFor({state:'hidden'});check(await page.evaluate(()=>qa.notes[0].body==='Несохранённая правка'),'failed write can be retried');
   await page.locator('#noteBody').fill(body);await page.locator('#noteMenu').click();await page.getByRole('button',{name:ui('В корзину'),exact:true}).click();await page.locator('.notes-empty').waitFor();
   await page.locator('#trashToggle').click();await page.locator('.note-card').waitFor();await page.locator('.note-card').click();await page.locator('#restoreNote').click();await page.locator('.note-card').waitFor();check(await page.locator('#listTitle').innerText()===ui('Ваши заметки')+'.','restore returns note to active list');
   await page.locator('.note-card').click();await page.locator('#editorScreen').waitFor({state:'visible'});check(await page.locator('#noteBody').inputValue()===body,'trash restore preserves text');
   await page.locator('#editorBack').click();await page.locator('.note-card').waitFor();
   await page.screenshot({path:out+'/list-'+language+'.png'});
   await page.evaluate(()=>PocketTheme.set('dark'));await page.screenshot({path:out+'/list-'+language+'-dark.png'});
   for(const size of [{width:320,height:640},{width:768,height:1024},{width:852,height:393}]){await page.setViewportSize(size);check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'notes list fits '+size.width+' in '+language);}
   await page.locator('.note-card').click();await page.evaluate(()=>document.documentElement.style.fontSize='130%');check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'editor fits large text and landscape');await page.screenshot({path:out+'/editor-'+language+'-large.png'});
   if(process.env.STUDIO_AXE_PATH){await page.evaluate(fs.readFileSync(process.env.STUDIO_AXE_PATH,'utf8'));const failures=await page.evaluate(async()=>{const r=await axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa']}});return r.violations.map(v=>v.id);});check(failures.length===0,'notes accessibility: '+failures.join(','));}
   await page.locator('#noteMenu').click();await page.getByRole('button',{name:ui('В корзину'),exact:true}).click();await page.locator('.notes-empty').waitFor();await page.locator('#trashToggle').click();await page.locator('.note-card').click();await page.locator('#deleteForever').click();
   await page.locator('#closeDialog').click();check(await page.locator('#trashDetail').isVisible(),'permanent deletion can be cancelled');await page.locator('#deleteForever').click();await page.locator('#dialogActions .danger').click();await page.locator('.notes-empty').waitFor();check(await page.evaluate(()=>qa.notes.length===0),'confirmed permanent deletion removes note');
   check(errors.length===0,'no notes JavaScript errors: '+errors.join('; '));
  }catch(e){console.error('Notes '+language+' visible UI:',(await page.locator('body').innerText()).slice(0,3000));throw e;}finally{await page.close();}
 }}finally{await browser.close();await new Promise(r=>server.close(r));}
 console.log('PASS: '+checks+' notes UI checks in RU/EN');
};
if(require.main===module)module.exports().catch(e=>{console.error(e);process.exitCode=1;});
