(function(){
'use strict';
var i18n=window.PocketI18n,t=i18n.t,$=function(id){return document.getElementById(id);};
i18n.template(document.documentElement);
var native=window.Notes,serial=0,pending=new Map(),note=null,sequence=0,savedSequence=0;
var trash=false,query='',items=[],more=false,generation=0,searchTimer=0,toastTimer=0,busy=false,started=false;
var paths={back:'m14 5-7 7 7 7',search:'m20 20-4-4M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z',trash:'M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7',lock:'M7 11V7a5 5 0 0 1 10 0v4M5 11h14v10H5zM12 15v2',edit:'m15 4 5 5M4 20l5-1L21 7l-5-5L4 14v6Z',plus:'M12 5v14M5 12h14',pin:'m9 3 6 0v5l4 5v2H5v-2l4-5V3ZM12 15v7',more:'M5 12h.01M12 12h.01M19 12h.01',check:'M3 6h4v4H3zM11 8h10M3 15l2 2 4-4M11 16h10',file:'M14 3H5v18h14V8l-5-5ZM14 3v6h5M9 13h6M9 17h4'};
function icon(name){var svg=document.createElementNS('http://www.w3.org/2000/svg','svg'),path=document.createElementNS(svg.namespaceURI,'path');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('class','icon');svg.setAttribute('aria-hidden','true');path.setAttribute('d',paths[name]||paths.file);svg.append(path);return svg;}
document.querySelectorAll('[data-icon]').forEach(function(el){el.replaceWith(icon(el.dataset.icon));});
function el(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;}
function call(action,args){return new Promise(function(resolve,reject){
 if(!native){reject(new Error(t('Откройте приложение Pocket Llama на телефоне.')));return;}
 var id=String(++serial),timer=setTimeout(function(){pending.delete(id);reject(new Error(t('Действие занимает больше времени. Попробуйте ещё раз.')));},30000);
 pending.set(id,{resolve:resolve,reject:reject,timer:timer});try{native.call(id,action,JSON.stringify(args||{}));}catch(e){clearTimeout(timer);pending.delete(id);reject(e);}
});}
function toast(message){clearTimeout(toastTimer);$('noteToast').textContent=message;$('noteToast').hidden=false;toastTimer=setTimeout(function(){$('noteToast').hidden=true;},4000);}
function fail(error){toast(error.message||String(error));}
function saveError(error){$('saveErrorText').textContent=error.message||String(error);$('saveError').hidden=false;$('saveStatus').textContent=t('Не сохранено');$('saveStatus').classList.add('failed');}
function saving(){ $('saveStatus').textContent=t('Сохраняем…');$('saveStatus').classList.remove('failed'); }
function saved(){ $('saveStatus').textContent=t('Сохранено');$('saveStatus').classList.remove('failed');$('saveError').hidden=true; }
function date(value,full){return new Date(value).toLocaleString(i18n.locale(),full?{day:'numeric',month:'long',hour:'2-digit',minute:'2-digit'}:{day:'numeric',month:'short',year:'numeric'});}
function fold(value){return value.normalize('NFC').toLocaleLowerCase(i18n.locale()).replace(/ё/g,'е');}
function marked(node,value){
 var needle=fold(query.trim()),at=needle?fold(value).indexOf(needle):-1;
 // Mark only exact-length matches so Unicode normalization cannot hide user text.
 if(at<0||fold(value.slice(at,at+needle.length))!==needle){node.textContent=value;return;}
 node.append(document.createTextNode(value.slice(0,at)),el('mark','',value.slice(at,at+needle.length)),document.createTextNode(value.slice(at+needle.length)));
}
function heading(){
 $('listEyebrow').textContent=t(trash?'Можно вернуть':'Мысли под рукой');
 $('listTitle').replaceChildren(document.createTextNode(t(trash?'Корзина':'Ваши заметки')),el('span','','.'));
 $('listIntro').textContent=t(trash?'Удалённые заметки остаются здесь, пока вы не удалите их навсегда.':'Идеи, планы и всё, что хочется сохранить.');
 $('trashLabel').textContent=t(trash?'Все заметки':'Корзина');$('newNote').hidden=trash;
 $('noteSearch').placeholder=t(trash?'Найти в корзине':'Найти в заметках');
}
function showList(){ $('editorScreen').hidden=true;$('trashDetail').hidden=true;$('listScreen').hidden=false;note=null;heading(); }
function render(){
 var root=$('noteList');root.replaceChildren();
 if(!items.length){var empty=el('div','notes-empty'),art=el('div','empty-note');art.append(icon(query.trim()?'search':trash?'trash':'edit'));empty.append(art,el('h2','',t(query.trim()?'Ничего не найдено':trash?'Корзина пуста':'Место для ваших мыслей')),el('p','',t(query.trim()?'Попробуйте другое слово или фразу.':trash?'Удалённые заметки можно будет восстановить здесь.':'Запишите идею, план или список дел. Pocket сохранит всё автоматически.')));root.append(empty);}
 var group='';items.forEach(function(item){
  var key=trash?'':item.pinned?'Закреплённые':items.some(function(n){return n.pinned;})?'Все остальные':'Заметки';
  if(key!==group){group=key;root.append(el('h2','notes-group',t(key)));}
  var card=el('button','note-card'),title=el('span','note-card-title'),preview=el('span','note-preview'),meta=el('span','note-card-date');card.type='button';card.dataset.id=item.id;
  marked(title,item.title.trim()||t('Без названия'));marked(preview,item.preview||t('Пустая заметка'));meta.append(document.createTextNode(date(item.deleted||item.updated,false)));if(item.pinned&&!trash)meta.prepend(icon('pin'));
  card.append(title,preview,meta);card.onclick=function(){openNote(item.id);};root.append(card);
 });
 $('moreNotes').hidden=!more;$('clearSearch').hidden=!query;$('listStatus').textContent=items.length?(t('Показано')+' '+items.length+(more?' · '+t('есть ещё'):'')):'';
}
async function load(append){
 var ticket=append?generation:++generation,q=query,bin=trash,offset=append?items.length:0;
 $('retryList').hidden=true;$('moreNotes').disabled=true;$('listStatus').textContent=t('Ищем…');
 try{var data=await call('list',{query:q,trash:bin,offset:offset,limit:50});if(ticket!==generation||q!==query||bin!==trash)return;
  items=append?items.concat(data.items):data.items;more=data.more;render();
  if(!trash&&data.trashCount)$('trashLabel').textContent=t('Корзина')+' · '+data.trashCount;
 }catch(e){if(ticket!==generation)return;$('listStatus').textContent=e.message||String(e);$('retryList').hidden=false;}
 finally{if(ticket===generation)$('moreNotes').disabled=false;}
}
function fitTitle(){var n=$('noteTitle');n.style.height='auto';n.style.height=Math.min(165,n.scrollHeight)+'px';}
function paintPin(){var pinned=!!note.pinned;$('pinNote').setAttribute('aria-pressed',String(pinned));$('pinNote').setAttribute('aria-label',t(pinned?'Открепить заметку':'Закрепить заметку'));$('pinnedLabel').hidden=!pinned;}
async function transition(work){if(busy)return;busy=true;$('noteTitle').readOnly=true;$('noteBody').readOnly=true;try{await work();}catch(e){if(note&&!$('editorScreen').hidden)saveError(e);else fail(e);}finally{busy=false;$('noteTitle').readOnly=false;$('noteBody').readOnly=false;}}
function edit(data,fresh){
 note=data;sequence=0;savedSequence=0;$('listScreen').hidden=true;$('trashDetail').hidden=true;$('editorScreen').hidden=false;
 $('noteTitle').value=data.title;$('noteBody').value=data.body;$('noteDate').textContent=date(data.updated,true);$('noteDate').dateTime=new Date(data.updated).toISOString();$('noteLimit').hidden=data.body.length<180000;paintPin();saved();fitTitle();
 if(fresh)setTimeout(function(){$('noteTitle').focus();},0);
}
function readTrash(data){note=data;trash=true;heading();$('listScreen').hidden=true;$('editorScreen').hidden=true;$('trashDetail').hidden=false;$('trashTitle').textContent=data.title.trim()||t('Без названия');$('trashBody').textContent=data.body||t('Пустая заметка');}
async function openNote(id){return transition(async function(){var data=await call('open',{id:id});if(data.deleted)readTrash(data);else edit(data,false);});}
function stage(){
 if(!note||busy)return;
 sequence++;saving();$('noteLimit').hidden=$('noteBody').value.length<180000;fitTitle();
 try{var error=native.stage(note.token,sequence,$('noteTitle').value,$('noteBody').value);if(error)saveError(new Error(error));}catch(e){saveError(e);}
}
async function flush(){if(note&&!note.deleted){var error=native.stage(note.token,sequence,$('noteTitle').value,$('noteBody').value);if(error)throw new Error(error);await call('flush');savedSequence=sequence;saved();}}
async function backToList(){await transition(async function(){await flush();await call('close');showList();await load(false);});}
function closeDialog(){if($('noteDialog').open)$('noteDialog').close();}
function dialog(title,copy,actions){$('dialogTitle').textContent=t(title);$('dialogText').textContent=t(copy);$('dialogText').hidden=!copy;$('dialogActions').replaceChildren();actions.forEach(function(a){var b=el('button',a.danger?'danger':'secondary',t(a.label));b.type='button';b.onclick=function(){closeDialog();a.run();};$('dialogActions').append(b);});$('noteDialog').showModal();}
function trashCurrent(){transition(async function(){await flush();await call('trash',{id:note.id});showList();await load(false);toast(t('Заметка в корзине. Её можно восстановить.'));});}
function pinCurrent(){transition(async function(){await flush();var data=await call('pin',{id:note.id,pinned:!note.pinned});note.pinned=data.pinned;paintPin();});}
window.NoteEvents=function(event){var data=event.data||{};
 if(event.type==='reply'){var p=pending.get(data.request);if(!p)return;clearTimeout(p.timer);pending.delete(data.request);if(data.error)p.reject(new Error(data.error));else p.resolve(data.value);}
 if(event.type==='boot'&&!started){started=true;(async function(){if(data.selection)await openNote(data.selection);if(!note)await load(false);})().catch(fail);}
 if(event.type==='resume'&&started&&!note)load(false);
 if(event.type==='saved'&&note&&data.token===note.token){savedSequence=Math.max(savedSequence,data.sequence);$('noteDate').textContent=date(data.updated,true);if(savedSequence>=sequence)saved();}
 if(event.type==='saveError'&&note&&(!data.token||data.token===note.token)&&savedSequence<sequence)saveError(new Error(data.message));
 if(event.type==='error'){started=true;$('listStatus').textContent=data.message;$('retryList').hidden=false;}
};
window.NoteBack=function(){if($('noteDialog').open){closeDialog();return;}if(busy)return;if(!$('editorScreen').hidden){backToList();return;}if(!$('trashDetail').hidden){showList();load(false);return;}if(trash){trash=false;query='';$('noteSearch').value='';showList();load(false);return;}transition(async function(){await call('back');});};
$('exitNotes').onclick=window.NoteBack;$('editorBack').onclick=backToList;$('trashBack').onclick=function(){showList();load(false);};
$('newNote').onclick=function(){transition(async function(){edit(await call('create'),true);});};
$('trashToggle').onclick=function(){if(busy)return;trash=!trash;query='';$('noteSearch').value='';heading();load(false);};
$('noteSearch').oninput=function(){query=this.value;$('clearSearch').hidden=!query;generation++;clearTimeout(searchTimer);searchTimer=setTimeout(function(){load(false);},180);};
$('clearSearch').onclick=function(){query='';$('noteSearch').value='';clearTimeout(searchTimer);load(false);$('noteSearch').focus();};
$('moreNotes').onclick=function(){load(true);};$('retryList').onclick=function(){native.ready();started=false;};
$('noteTitle').oninput=stage;$('noteBody').oninput=stage;$('pinNote').onclick=pinCurrent;
$('doneEditing').onclick=function(){transition(async function(){await flush();$('noteTitle').blur();$('noteBody').blur();});};
$('retrySave').onclick=function(){transition(flush);};
$('noteMenu').onclick=function(){if(!note||busy)return;dialog('Заметка','',[{label:note.pinned?'Открепить заметку':'Закрепить заметку',run:pinCurrent},{label:'В корзину',run:trashCurrent}]);};
$('restoreNote').onclick=function(){transition(async function(){var id=note.id;await call('restore',{id:id});trash=false;query='';$('noteSearch').value='';showList();await load(false);toast(t('Заметка восстановлена'));});};
$('deleteForever').onclick=function(){dialog('Удалить заметку навсегда?','Восстановить её после этого не получится.',[{label:'Удалить навсегда',danger:true,run:function(){transition(async function(){await call('purge',{id:note.id});showList();await load(false);});}}]);};
$('closeDialog').onclick=closeDialog;
$('checklist').onclick=function(){var b=$('noteBody');if(!note||busy)return;var start=b.selectionStart,end=b.selectionEnd,line=b.value.lastIndexOf('\n',start-1)+1;
 var match=/^(☐|☑) /.exec(b.value.slice(line));
 if(match){b.setRangeText(match[1]==='☐'?'☑':'☐',line,line+1,'preserve');}
 else{if(b.value.length+2>200000){toast(t('Лимит заметки — 200 000 символов'));return;}b.setRangeText('☐ ',line,line,'preserve');b.setSelectionRange(start+2,end+2);}
 b.focus();stage();};
$('noteBody').addEventListener('keydown',function(e){if(e.key!=='Enter'||e.isComposing||e.shiftKey||this.selectionStart!==this.selectionEnd)return;var at=this.selectionStart,line=this.value.lastIndexOf('\n',at-1)+1,text=this.value.slice(line,at);if(!/^[☐☑] /.test(text))return;e.preventDefault();if(text.length===2)this.setRangeText('',line,at,'end');else{if(this.value.length+3>200000){toast(t('Лимит заметки — 200 000 символов'));return;}this.setRangeText('\n☐ ',at,at,'end');}stage();});
document.addEventListener('keydown',function(e){if((e.ctrlKey||e.metaKey)&&e.key==='s'){e.preventDefault();transition(flush);}if(e.key==='Escape'&&!$('noteDialog').open)window.NoteBack();});
heading();if(native)native.ready();else{$('listStatus').textContent=t('Откройте приложение Pocket Llama на телефоне.');}
})();
