const $ = id => document.getElementById(id);
let key = localStorage.getItem('yomu-key') || '', snapshot = null, view = 'toRead', busy = false;
const client = localStorage.getItem('yomu-client') || crypto.randomUUID();
localStorage.setItem('yomu-client', client);
function notice(message){$('notice').textContent = message;}
async function api(path, options = {}) {
  const response = await fetch('/api/' + path, {...options, headers: {...options.headers, Authorization:'Bearer '+key}});
  if(!response.ok){let body = await response.json().catch(()=>({}));throw Error(body.detail || 'Connection failed ('+response.status+').');}
  return response.json();
}
const records = kind => (snapshot?.records || []).filter(r=>r.kind===kind && r.data).map(r=>r.data);
const lookup = (kind,id) => snapshot?.records.find(r=>r.kind===kind && r.id===id)?.data || null;
async function refresh(){if(!key)return;try{snapshot=await api('state');$('connection').textContent='Synced '+new Date().toLocaleTimeString([], {hour:'numeric',minute:'2-digit'});render();}catch(e){notice(e.message);$('connection').textContent='Disconnected';}}
async function mutate(changes){const result=await api('sync',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({client:'web-'+client,instance:snapshot.instance,changes})});snapshot=result;render();notice(result.conflicts.length?'A change conflicted with another device. The current server value was kept.':'Changes saved.');}
const change = (kind,id,data) => ({kind,id,data,base:lookup(kind,id)});
function el(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;}
function button(text,action){const e=el('button',text);e.onclick=()=>Promise.resolve().then(action).catch(e=>notice(e.message));return e;}
function render(){
  const nav=$('navigation');nav.replaceChildren();const views=[['toRead','To read'],['queue','Up next'],['history','Read history'],['archive','Archived'],...records('playlists').map(p=>[p.id,p.name])];
  views.forEach(([id,name])=>{const b=button(name,()=>{view=id;render();});b.className='quiet'+(id===view?' active':'');nav.append(b);});
  $('heading').textContent=views.find(v=>v[0]===view)?.[1]||'To read';
  let items=records('reading_items');const query=$('search').value.toLowerCase();
  if(view==='toRead')items=items.filter(i=>i.inToRead && !i.archived).sort((a,b)=>b.manualOrder-a.manualOrder);
  else if(view==='queue'){const queue=records('up_next').sort((a,b)=>a.position-b.position);items=queue.map(q=>items.find(i=>i.id===q.itemId)).filter(Boolean);}
  else if(view==='history'){const ids=new Set(records('read_history').map(h=>h.itemId));items=items.filter(i=>ids.has(i.id));}
  else if(view==='archive')items=items.filter(i=>i.archived);
  else{const members=records('playlist_memberships').filter(m=>m.playlistId===view).sort((a,b)=>a.sortOrder-b.sortOrder);items=members.map(m=>items.find(i=>i.id===m.itemId)).filter(Boolean);}
  items=items.filter(i=>(i.title+' '+(i.sourceName||'')).toLowerCase().includes(query));$('count').textContent=items.length+' items';$('library').replaceChildren();
  if(!items.length){const empty=el('div',undefined,'empty');const img=el('img');img.src='/icon.png';img.alt='';empty.append(img,el('p',query?'No matching reading.':'Save an article or document to start your library.'));$('library').append(empty);}
  for(const item of items){const row=el('article',undefined,'reading');row.append(el('div',item.sourceType==='url'?'Link':item.sourceType.toUpperCase(),'filemark'));const content=el('div');content.append(el('h2',item.title));const progress=lookup('reading_progress',item.id);content.append(el('p',(item.sourceName||'Document')+' · '+(item.importState==='ready'?Math.max(1,Math.round(item.estimatedSecondsAtOneX/60))+' min':item.warning||'Waiting for phone'),'metadata'));
    if(progress && item.normalizedText){const bar=el('div',undefined,'progress'),fill=el('div');fill.style.width=Math.min(100,progress.completed?100:100*progress.characterOffset/item.normalizedText.length)+'%';bar.append(fill);content.append(bar);}
    const actions=el('div',undefined,'actions');actions.append(button('Read',()=>{if(!item.normalizedText){notice('Open Yomu on your phone to finish importing this item.');return;}$('readerTitle').textContent=item.title;$('readerText').textContent=item.normalizedText;$('reader').showModal();}));
    actions.append(button(progress?.completed?'Mark unread':'Mark read',async()=>{const completed=!progress?.completed;const now=Date.now();const changes=[change('reading_items',item.id,{...item,inToRead:!completed,archived:false,updatedAt:now}),change('reading_progress',item.id,{...(progress||{itemId:item.id,sentenceIndex:0,characterOffset:0,playbackSpeed:1,lastListenedAt:0}),completed})];if(completed){if(lookup('up_next',item.id))changes.push(change('up_next',item.id,null));const id=crypto.randomUUID();changes.push(change('read_history',id,{id,itemId:item.id,completedAt:now}));}else records('read_history').filter(h=>h.itemId===item.id).forEach(h=>changes.push(change('read_history',h.id,null)));await mutate(changes);}));
    const queued=lookup('up_next',item.id);if(view==='queue' && queued){for(const [label,delta] of [['Move up',-1],['Move down',1]])actions.append(button(label,()=>{const q=records('up_next').sort((a,b)=>a.position-b.position);const n=q.findIndex(x=>x.itemId===item.id),m=n+delta;if(m<0||m>=q.length)return;[q[n],q[m]]=[q[m],q[n]];return mutate(q.map((entry,index)=>change('up_next',entry.itemId,{...entry,position:index})));}));}actions.append(button(queued?'Remove from Up next':'Add to Up next',()=>mutate([change('up_next',item.id,queued?null:{itemId:item.id,position:records('up_next').length,addedAt:Date.now()})])));
    actions.append(button(item.archived?'Restore':'Archive',()=>mutate([change('reading_items',item.id,{...item,archived:!item.archived,inToRead:!!item.archived,updatedAt:Date.now()}),...(queued?[change('up_next',item.id,null)]:[])])));
    actions.append(button('Rename',()=>{const title=prompt('Reading title',item.title);if(title?.trim())return mutate([change('reading_items',item.id,{...item,title:title.trim(),updatedAt:Date.now()})]);}));
    const select=el('select');select.setAttribute('aria-label','Add to playlist');const first=el('option','Add to playlist');first.value='';select.append(first);records('playlists').forEach(p=>{const o=el('option',p.name);o.value=p.id;select.append(o);});select.onchange=()=>{if(select.value){const id=select.value+':'+item.id;mutate([change('playlist_memberships',id,{playlistId:select.value,itemId:item.id,sortOrder:Math.max(-1,...records('playlist_memberships').filter(m=>m.playlistId===select.value).map(m=>m.sortOrder))+1,addedAt:Date.now()})]).catch(e=>notice(e.message));}};actions.append(select);
    if(!['toRead','queue','history','archive'].includes(view))actions.append(button('Remove from playlist',()=>mutate([change('playlist_memberships',view+':'+item.id,null)])));
    actions.append(button('Delete',()=>{if(confirm('Delete this saved item from your synced library?'))return mutate([change('reading_items',item.id,null),...['up_next','reading_progress','playlist_memberships','read_history'].flatMap(kind=>(snapshot.records||[]).filter(r=>r.kind===kind&&r.data?.itemId===item.id).map(r=>change(kind,r.id,null)))]);}));
    content.append(actions);row.append(content);$('library').append(row);
  }
}
$('saveLink').onsubmit=async e=>{e.preventDefault();try{await api('articles',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url:$('url').value})});$('url').value='';await refresh();notice('Saved. Your phone will import the article on its next sync.');}catch(e){notice(e.message);}};
async function upload(files){for(const file of files){try{if(file.size>20*1024*1024)throw Error(file.name+' exceeds 20 MB.');notice('Uploading '+file.name+'…');const id=crypto.randomUUID();await api('files/'+id,{method:'PUT',headers:{'Content-Type':file.type||'application/octet-stream','X-File-Name':encodeURIComponent(file.name)},body:file});await refresh();const now=Date.now();await mutate([change('reading_items',id,{id,title:file.name,sourceType:file.name.split('.').pop().toLowerCase(),sourceName:file.name,fileId:id,normalizedText:'',importState:'pending',warning:'Waiting for phone to extract this document.',inToRead:true,importedAt:now,updatedAt:now,estimatedSecondsAtOneX:0,contentVersion:1,manualOrder:now,archived:false})]);notice('Saved '+file.name+'.');}catch(e){notice(e.message);}}}
$('files').onchange=e=>upload(e.target.files);$('drop').ondragover=e=>e.preventDefault();$('drop').ondrop=e=>{e.preventDefault();upload(e.dataTransfer.files);};
$('refresh').onclick=refresh;$('search').oninput=render;$('pairButton').onclick=()=>$('pair').showModal();$('closePair').onclick=()=>$('pair').close();$('closeReader').onclick=()=>$('reader').close();
$('pairForm').onsubmit=async e=>{e.preventDefault();key=$('key').value.trim();try{snapshot=await api('state');localStorage.setItem('yomu-key',key);$('key').value='';$('pair').close();await refresh();}catch(e){notice(e.message);}};
$('disconnect').onclick=()=>{key='';snapshot=null;localStorage.removeItem('yomu-key');render();$('pair').showModal();};
$('newPlaylist').onclick=()=>{const name=prompt('Playlist name');if(name?.trim()){const id=crypto.randomUUID();mutate([change('playlists',id,{id,name:name.trim(),createdAt:Date.now(),sortOrder:records('playlists').length})]).catch(e=>notice(e.message));}};
render();if(key)refresh();else $('pair').showModal();setInterval(()=>{if(key&&!document.hidden&&!$('reader').open)refresh();},15000);


