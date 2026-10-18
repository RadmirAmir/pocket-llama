(function(){
'use strict';
var bridge=window.Studio||window.Pocket||window.Photos,language='ru',english={};
try{if(bridge&&bridge.language)language=bridge.language()==='en'?'en':'ru';if(language==='en'&&bridge.translations)english=JSON.parse(bridge.translations());}catch(e){language='ru';}
function t(source){return language==='en'&&Object.prototype.hasOwnProperty.call(english,source)?english[source]:source;}
var phrases=Object.keys(english).filter(function(k){return k.length>=5;}).sort(function(a,b){return b.length-a.length;});
function status(source){if(language!=='en'||typeof source!=='string')return source;if(Object.prototype.hasOwnProperty.call(english,source))return english[source];phrases.forEach(function(k){source=source.split(k).join(english[k]);});return source;}
// Called once, before the app adds user content to its static HTML template.
function template(root){
 var walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT),node;
 while((node=walker.nextNode())){
  if(node.parentElement.closest('script,style'))continue;
  var original=node.nodeValue,trim=original.trim(),translated=t(trim);
  if(trim&&translated!==trim)node.nodeValue=original.replace(trim,translated);
 }
 root.querySelectorAll('[placeholder],[title],[aria-label],[data-prompt]').forEach(function(el){
  ['placeholder','title','aria-label','data-prompt'].forEach(function(attr){if(el.hasAttribute(attr))el.setAttribute(attr,t(el.getAttribute(attr)));});
 });
 document.documentElement.lang=language;
}
window.PocketI18n=Object.freeze({t:t,status:status,template:template,language:function(){return language;},locale:function(){return language==='en'?'en-US':'ru-RU';}});
})();
