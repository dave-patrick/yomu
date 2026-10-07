'use strict';
let resumeState=null, resumeItem=null, textHash='', cursorBase=null, defaultsBase=null;
let pendingCursor=null, pendingDefaults=null, positionTimer=null, flushing=false, positionConflict=false, cursorChosen=false;
const readerClient=localStorage.getItem('yomu-client') || crypto.randomUUID();
localStorage.setItem('yomu-client',readerClient);
const readerTab=sessionStorage.getItem('yomu-reader-tab') || crypto.randomUUID();
sessionStorage.setItem('yomu-reader-tab',readerTab);
const resumeRecord=(state,kind,id)=>state.records.find(row=>row.kind===kind && row.id===id)?.data || null;
const pendingKey=()=> 'yomu.readerPending:'+resumeState.instance+':'+itemId+':'+readerTab;
function persistPending(){
 if(!resumeState)return;
 if(pendingCursor || pendingDefaults)localStorage.setItem(pendingKey(),JSON.stringify({cursor:pendingCursor,defaults:pendingDefaults}));
 else localStorage.removeItem(pendingKey());
}
function jumpReading(offset){
 const paragraphs=[...readerNode('readerText').children];
 const paragraph=paragraphs.filter(node=>Number(node.dataset.offset)<=offset).at(-1) || paragraphs[0];
 if(paragraph){
  const local=Math.min(paragraph.textContent.length-1,Math.max(0,offset-Number(paragraph.dataset.offset)));
  const range=document.createRange();range.setStart(paragraph.firstChild,local);range.setEnd(paragraph.firstChild,local+1);
  const top=range.getBoundingClientRect().top;window.scrollTo(0,window.scrollY+top-100);
 }
 cursorChosen=true;
}
function readingOffset(){
 const paragraphs=[...readerNode('readerText').children];
 const paragraph=paragraphs.find(node=>node.getBoundingClientRect().bottom>100) || paragraphs.at(-1);
 if(!paragraph)return 0;
 const rect=paragraph.getBoundingClientRect(),x=rect.left+2,y=Math.max(100,rect.top+2);
 const caret=document.caretPositionFromPoint?.(x,y);
 const range=caret ? null : document.caretRangeFromPoint?.(x,y);
 const node=caret?.offsetNode || range?.startContainer,offset=caret?.offset ?? range?.startOffset;
 return Number(paragraph.dataset.offset)+(node===paragraph.firstChild ? offset : 0);
}
function showResume(cursor){
 const offset=cursor ? readerModel.resolveAnchor(resumeItem.normalizedText,cursor.anchor,textHash) : null;
 const listening=resumeRecord(resumeState,'reading_progress',itemId);
 readerNode('resumeControls').hidden=!cursor && !listening;
 readerNode('resumeReading').hidden=!cursor || offset===null;
 readerNode('resumeMessage').textContent=cursor ? offset===null ? 'The article text changed and the saved position could not be matched. Choose a new position by reading.' :
  'A reading position is saved from '+(cursor.deviceLabel || 'another device')+'. Resume when you’re ready.' : 'Your listening position is separate from reading.';
 readerNode('resumeReading').onclick=()=>{jumpReading(offset);captureReading()};
 readerNode('resumeListening').hidden=!listening || listening.completed;
 readerNode('resumeListening').onclick=()=>{jumpReading(Math.min(resumeItem.normalizedText.length,listening.characterOffset || 0));captureReading()};
}
async function connectResume(state,item){
 const hash=await readerModel.fingerprint(item.normalizedText);
 if(readerNode('readerArticle').hidden || !item.normalizedText || !localStorage.getItem('yomu-key'))return;
 clearTimeout(positionTimer);resumeState=state;resumeItem=item;textHash=hash;
 cursorBase=resumeRecord(state,'reading_cursors',itemId);defaultsBase=resumeRecord(state,'reader_defaults','global');
 positionConflict=false;cursorChosen=!cursorBase;pendingCursor=null;pendingDefaults=null;
 try{
  let raw=localStorage.getItem(pendingKey());
  if(!raw){
   const prefix='yomu.readerPending:'+resumeState.instance+':'+itemId+':';
   const recover=Object.keys(localStorage).find(key=>key.startsWith(prefix));
   if(recover){raw=localStorage.getItem(recover);localStorage.setItem(pendingKey(),raw);localStorage.removeItem(recover)}
  }
  const pending=JSON.parse(raw || '{}');pendingCursor=pending.cursor || null;pendingDefaults=pending.defaults || null;
 }catch{}
 if(pendingCursor && readerModel.resolveAnchor(item.normalizedText,pendingCursor.data.anchor,textHash)===null){
  readerNode('positionStatus').textContent='Offline position needs review because the article text changed.';positionConflict=true;
  readerNode('keepReadingPosition').hidden=false;readerNode('useRemotePosition').hidden=false;
 }
 showResume(pendingCursor?.data || cursorBase);
 const useLibrary=localStorage.getItem('yomu.readingOverride')==='false';readerNode('localReadingOverride').checked=!useLibrary;
 if(useLibrary && defaultsBase){prefs=readerModel.preferences({...prefs,...defaultsBase});applyReading()}
 if(pendingCursor || pendingDefaults)await flushReading();
}
function captureReading(){
 if(!resumeItem || !cursorChosen || document.hidden || positionConflict)return;
 pendingCursor={base:pendingCursor?.base ?? cursorBase,data:{itemId,anchor:readerModel.anchor(resumeItem.normalizedText,readingOffset(),textHash),updatedAt:Date.now(),deviceLabel:'Website'}};
 persistPending();readerNode('positionStatus').textContent='Reading position saved on this browser. Syncing…';
 clearTimeout(positionTimer);positionTimer=setTimeout(flushReading,1000);
}
async function flushReading(){
 if(!resumeState || flushing || positionConflict || (!pendingCursor && !pendingDefaults))return;
 const key=localStorage.getItem('yomu-key');if(!key)return;
 flushing=true;
 const sentCursor=pendingCursor,sentDefaults=pendingDefaults,sentInstance=resumeState.instance;
 try{
  const response=await fetch('/api/sync',{method:'POST',headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},
   body:JSON.stringify({client:'web-'+readerClient,instance:sentInstance,changes:[
    ...(sentCursor ? [{kind:'reading_cursors',id:itemId,...sentCursor}] : []),
    ...(sentDefaults ? [{kind:'reader_defaults',id:'global',...sentDefaults}] : [])]}),signal:AbortSignal.timeout(20000),keepalive:true});
  if(!response.ok)throw Error(response.status===401 ? 'Reconnect this browser to sync your saved position.' : response.status===409 ? 'The server library changed. Reconnect before syncing this position.' : 'Position remains saved locally. Sync will retry.');
  const state=await response.json();
  if(resumeState.instance!==sentInstance || localStorage.getItem('yomu-key')!==key)return;
  const remote=resumeRecord(state,'reading_cursors',itemId);
  if(!resumeRecord(state,'reading_items',itemId)){
   pendingCursor=null;pendingDefaults=null;persistPending();resumeItem=null;
   readerNode('positionStatus').textContent='This article was removed on another device. Position saving stopped.';return;
  }
  const freshItem=resumeRecord(state,'reading_items',itemId);
  if(freshItem.normalizedText !== resumeItem.normalizedText){
   positionConflict=true;cursorBase=remote;defaultsBase=resumeRecord(state,'reader_defaults','global');
   readerNode('positionStatus').textContent='The article text changed on another device. Reload to review and remap your saved position.';
   return;
  }
  if(state.conflicts?.length){
   positionConflict=true;cursorBase=remote;defaultsBase=resumeRecord(state,'reader_defaults','global');
   readerNode('resumeControls').hidden=false;readerNode('resumeMessage').textContent='Another device changed your position or library defaults. Choose which version to keep.';
   readerNode('keepReadingPosition').hidden=false;readerNode('useRemotePosition').hidden=false;
   readerNode('positionStatus').textContent='Your local changes are preserved until you choose.';return;
  }
  cursorBase=remote;defaultsBase=resumeRecord(state,'reader_defaults','global');
  if(pendingCursor===sentCursor)pendingCursor=null;else if(pendingCursor)pendingCursor.base=cursorBase;
  if(pendingDefaults===sentDefaults)pendingDefaults=null;else if(pendingDefaults)pendingDefaults.base=defaultsBase;
  persistPending();readerNode('positionStatus').textContent='Reading position and preferences synced.';
 }catch(error){readerNode('positionStatus').textContent=error instanceof TypeError || error.name==='TimeoutError' ? 'Offline: your position is saved on this browser and will retry.' : error.message}
 finally{flushing=false}
}
readerNode('keepReadingPosition').onclick=()=>{
 if(pendingCursor){
  const offset=readerModel.resolveAnchor(resumeItem.normalizedText,pendingCursor.data.anchor,textHash);
  if(offset===null){readerNode('positionStatus').textContent='That position cannot be matched. Use the synced position, then choose a new place.';return}
  pendingCursor.data.anchor=readerModel.anchor(resumeItem.normalizedText,offset,textHash);pendingCursor.base=cursorBase;
 }
 if(pendingDefaults)pendingDefaults.base=defaultsBase;
 positionConflict=false;persistPending();readerNode('keepReadingPosition').hidden=true;readerNode('useRemotePosition').hidden=true;flushReading();
};
readerNode('useRemotePosition').onclick=()=>{
 pendingCursor=null;pendingDefaults=null;positionConflict=false;persistPending();
 readerNode('keepReadingPosition').hidden=true;readerNode('useRemotePosition').hidden=true;showResume(cursorBase);
 const offset=cursorBase ? readerModel.resolveAnchor(resumeItem.normalizedText,cursorBase.anchor,textHash) : null;
 if(offset!==null)jumpReading(offset);else cursorChosen=true;
 if(!readerNode('localReadingOverride').checked && defaultsBase){prefs=readerModel.preferences({...prefs,...defaultsBase});applyReading()}
};
readerNode('localReadingOverride').onchange=event=>{
 localStorage.setItem('yomu.readingOverride',String(event.target.checked));
 if(!event.target.checked && defaultsBase){prefs=readerModel.preferences({...prefs,...defaultsBase});applyReading()}
};
readerNode('shareReading').onclick=()=>{
 if(!resumeState)return;
 pendingDefaults={base:pendingDefaults?.base ?? defaultsBase,data:{id:'global',schemaVersion:1,font:prefs.font,size:prefs.size,spacing:prefs.spacing}};
 persistPending();flushReading();
};
window.addEventListener('scroll',captureReading,{passive:true});
// An explicit local interaction permits choosing a new place instead of Resume.
for(const event of ['wheel','touchmove','keydown'])window.addEventListener(event,()=>{if(resumeItem && !positionConflict)cursorChosen=true},{passive:true});
window.addEventListener('online',flushReading);
window.addEventListener('pagehide',()=>{clearTimeout(positionTimer);flushReading()});
setInterval(flushReading,15000);
