// Production recognition-sheet behavior in a DOM. Layout/device checks use Playwright/Android.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const root=path.resolve(__dirname,'..'),source=fs.readFileSync(path.join(root,'app/src/main/assets/studio.js'),'utf8');
const start=source.indexOf('function processSheet(){'),end=source.indexOf('\nfunction modelNeededSheet()',start);
assert(start>=0&&end>start);
let checks=0;
function sheet(meeting,english=false){
 const dom=new JSDOM('<!doctype html><html><body></body></html>',{runScripts:'outside-only'}),w=dom.window,d=w.document;
 const dictionary=JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/i18n-en.json'),'utf8'));
 w.meeting=Object.assign({diarize:1,expected:0,turnCount:0,asr_done:0,speakers_done:0},meeting);w.selected='fixture';
 w.PocketI18n={language:()=>english?'en':'ru'};w._t=t=>english?(dictionary[t]||t):t;w.plural=(_,a)=>a;
 w.el=(tag,cls,text)=>{const e=d.createElement(tag);if(cls)e.className=cls;if(text)e.textContent=text;return e;};
 w.button=(text,cls,callback)=>{const e=w.el('button',cls,text);e.onclick=callback;return e;};
 w.openSheet=(_,box)=>d.body.append(box);w.perform=(action,args)=>{w.sent={action,args};};
 w.confirmSheet=(_,__,___,callback)=>callback();w.eval(source.slice(start,end));w.processSheet();
 return {w,d,dom,submit:()=>{d.querySelector('button').click();return JSON.parse(JSON.stringify(w.sent));}};
}
function check(ok,label){assert(ok,label);checks++;}
let s=sheet({diarization_engine:'nemotron-q8-v1'});
check(s.d.querySelector('#speakerEngine').value==='nemotron-q8-v1','new job selects actual stored engine');
check(s.d.querySelector('#speakerCount').hidden,'Nemotron does not offer unsupported speaker limit');
check(s.submit().args.diarization_engine==='nemotron-q8-v1'&&s.w.sent.args.expected===0,'submit sends automatic Nemotron settings');
let engine=s.d.querySelector('#speakerEngine');engine.value='cam++';engine.dispatchEvent(new s.w.Event('change'));
check(!s.d.querySelector('#speakerCount').hidden,'CAM++ exposes original count setting');
s.d.querySelector('#speakerCount').value='2';check(s.submit().args.expected===2&&s.w.sent.args.diarization_engine==='cam++','explicit CAM++ count reaches native bridge');
engine.value='nemotron-q8-v1';engine.dispatchEvent(new s.w.Event('change'));
check(s.submit().args.expected===0,'switching to Nemotron discards hidden CAM++ limit');s.dom.window.close();
s=sheet({diarization_engine:'cam++',turnCount:2,speakers_done:1,expected:2});
check(s.d.querySelector('#speakerEngine').disabled,'partial transcript cannot silently switch engine');
check(s.submit().args.diarization_engine==='cam++'&&!s.w.sent.args.restart,'resume retains original checkpoint semantics');s.dom.window.close();
s=sheet({diarization_engine:'cam++',turnCount:2,speakers_done:1,asr_done:1});
check(!s.d.querySelector('#speakerEngine').disabled&&s.submit().args.restart,'completed transcript permits explicit clean restart');s.dom.window.close();
s=sheet({diarization_engine:'nemotron-q8-v1'},true);
check(s.d.body.textContent.includes('up to 8 voices')&&!/[А-Яа-яЁё]/.test(s.d.body.textContent),'new sheet has complete English translations');
s.dom.window.close();
console.log('PASS: '+checks+' production recognition-sheet DOM checks');
