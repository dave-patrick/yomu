'use strict';
const $ = id => document.getElementById(id);
const model = YomuLibrary;
const libraryQuery = new URLSearchParams(location.search);
let key = localStorage.getItem('yomu-key') || '', snapshot = null, view = libraryQuery.get('view') || 'toRead';
$('search').value = libraryQuery.get('search') || '';
let returnContext = null;
try { returnContext = JSON.parse(sessionStorage.getItem('yomu.libraryReturn') || 'null'); } catch {}
function libraryRoute() {
  const params = new URLSearchParams();
  if (view !== 'toRead') params.set('view', view);
  if ($('search').value) params.set('search', $('search').value);
  return '/' + (params.size ? '?' + params : '');
}
let connectionEpoch = 0, writeDepth = 0, checking = false, connectionError = '', nameAction = null;
const queue = model.createSerialQueue();
const client = localStorage.getItem('yomu-client') || crypto.randomUUID();
localStorage.setItem('yomu-client', client);
const records = kind => model.live(snapshot, kind);
const lookup = (kind,id) => snapshot?.records.find(r => r.kind === kind && r.id === id)?.data || null;
const nowLabel = value => new Date(value).toLocaleTimeString([], {hour:'numeric', minute:'2-digit'});
function notice(message, error = false, target = 'notice') {
  $(target).textContent = message;
  $(target).classList.toggle('is-error', error);
}
function el(tag, text, cls) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (cls) node.className = cls;
  return node;
}
function updateControls() {
  document.querySelectorAll('[data-write]').forEach(node => {
    node.disabled = writeDepth > 0 || node.dataset.unavailable === 'true' || (!key && !node.closest('#pair')) ||
      (!snapshot && !node.closest('#pair'));
  });
  $('files').disabled = writeDepth > 0 || !key || !snapshot;
  $('refresh').disabled = checking || writeDepth > 0 || !key;
  $('refresh').textContent = checking ? 'Checking…' : 'Sync now';
  $('disconnect').hidden = !key;
  $('disconnect').disabled = writeDepth > 0;
  $('pairButton').disabled = writeDepth > 0;
  $('pairButton').textContent = key ? 'Reconnect' : 'Connect devices';
  $('connection').textContent = !key ? 'Not connected' : connectionError ? 'Needs attention' : checking && !snapshot ? 'Connecting…' : 'Connected';
  const last = Number(localStorage.getItem('yomu.lastCheck') || 0);
  $('lastCheck').textContent = !key ? 'Connect this browser to your library.' :
    connectionError || (last ? 'Last successful check: ' + nowLabel(last) : 'Waiting for the first library check.');
  $('closePair').disabled = writeDepth > 0;
  $('closeName').disabled = writeDepth > 0;
}
async function api(path, options = {}, accessKey = key) {
  try {
    const response = await fetch('/api/' + path, {...options,
      signal: options.signal || AbortSignal.timeout(options.method ? 90000 : 20000),
      headers: {...options.headers, Authorization:'Bearer ' + accessKey}});
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      const error = Error(response.status === 401 ? 'Access key rejected. Reconnect with your library key.' :
        typeof body.detail === 'string' ? body.detail : 'Yomu returned HTTP ' + response.status + '. Try again.');
      error.status = response.status;
      throw error;
    }
    return response.json();
  } catch (error) {
    if (error.name === 'TimeoutError') throw Error('Yomu took too long to respond. Try Sync now again.');
    if (error instanceof TypeError) throw Error('Cannot reach Yomu. Check your connection and try again.');
    throw error;
  }
}
function acceptSnapshot(result, epoch) {
  if (epoch !== connectionEpoch) throw Error('The browser connection changed. Check the current library before trying again.');
  snapshot = result;
  connectionError = '';
  localStorage.setItem('yomu.lastCheck', String(Date.now()));
  render();
}
async function runAction(action, errorTarget = 'notice') {
  if (writeDepth) { notice('Wait for the current change to finish.'); return; }
  writeDepth++;
  updateControls();
  return queue.run(async () => {
    try { await action(connectionEpoch); }
    catch (error) {
      notice(error.message, true, errorTarget);
      if (error.status === 401 && errorTarget !== 'pairError') connectionError = error.message;
    } finally {
      writeDepth--;
      updateControls();
    }
  });
}
async function refresh({quiet = false} = {}) {
  if (!key || (quiet && queue.pending)) return;
  if (checking) return;
  checking = true;
  updateControls();
  return queue.run(async () => {
    const epoch = connectionEpoch;
    try {
      const result = await api('state');
      acceptSnapshot(result, epoch);
      if (!quiet) notice('Library checked. New content becomes readable after your phone finishes importing it.');
    } catch (error) {
      if (epoch !== connectionEpoch) return;
      connectionError = error.message;
      notice(error.message, true);
      if (!snapshot) render();
    } finally {
      checking = false;
      updateControls();
    }
  });
}
const change = (kind,id,data) => ({kind,id,data,base:lookup(kind,id)});
async function mutate(changes, message, epoch) {
  if (!snapshot) throw Error('Load your library before making changes.');
  const result = await api('sync', {method:'POST', headers:{'Content-Type':'application/json'},
    body:JSON.stringify({client:'web-' + client, instance:snapshot.instance, changes})});
  acceptSnapshot(result, epoch);
  notice(result.conflicts?.length ? 'Some changes conflicted with another device. The current server value was kept.' : message);
}
function currentItem(id) {
  const item = lookup('reading_items', id);
  if (!item) throw Error('This item was removed from your library. Check for updates.');
  return item;
}
function actionButton(text, action, {write = false, unavailable = false, cls = 'secondary', label} = {}) {
  const node = el('button', text, cls);
  node.type = 'button';
  if (write) node.dataset.write = '';
  node.dataset.unavailable = String(unavailable);
  node.disabled = unavailable || (write && writeDepth > 0);
  if (label) node.setAttribute('aria-label', label);
  node.onclick = () => Promise.resolve().then(action).catch(error => notice(error.message, true));
  return node;
}
function views() {
  return [['toRead','To read'], ['queue','Up next'], ['history','Read history'], ['archive','Archived'],
    ...records('playlists').sort((a,b) => a.sortOrder - b.sortOrder).map(p => [p.id,p.name])];
}
const summaries = {
  toRead:'Your articles and documents, together.',
  queue:'Your listening order. The phone plays these items in sequence.',
  history:'Most recently completed first.',
  archive:'Saved items kept out of To read.'
};
function reconcile(parent, entries, create, update) {
  const focused = document.activeElement;
  const existing = new Map([...parent.children].map(node => [node.dataset.key, node]));
  entries.forEach((entry,index) => {
    let node = existing.get(entry.id);
    if (!node) { node = create(entry); node.dataset.key = entry.id; }
    existing.delete(entry.id);
    update(node, entry);
    if (parent.children[index] !== node) parent.insertBefore(node, parent.children[index] || null);
  });
  for (const node of existing.values()) {
    const lostFocus = node.contains(focused);
    node.remove();
    if (lostFocus) { $('heading').focus({preventScroll:true}); notice('The focused item was removed from this view.'); }
  }
  if (focused?.isConnected && document.activeElement !== focused && focused !== document.body) focused.focus({preventScroll:true});
}
function rowSignature(item, dates) {
  const progress = lookup('reading_progress', item.id);
  return JSON.stringify([item.title, item.sourceType, item.sourceUrl, item.sourceName, item.importState, item.warning,
    item.archived, item.estimatedSecondsAtOneX, model.canRead(item), item.normalizedText?.length || 0,
    progress, lookup('up_next', item.id), records('playlists'), records('playlist_memberships').filter(m => m.itemId === item.id),
    view, dates.get(item.id)]);
}
function render() {
  const availableViews = views();
  if (snapshot && !availableViews.some(([id]) => id === view)) view = 'toRead';
  reconcile($('navigation'), availableViews.map(([id,name]) => ({id,name})),
    entry => actionButton(entry.name, () => { view = entry.id; render(); }, {cls:'quiet'}),
    (node,entry) => {
      if (node.textContent !== entry.name) node.textContent = entry.name;
      node.className = 'quiet' + (entry.id === view ? ' active' : '');
      if (entry.id === view) node.setAttribute('aria-current', 'page'); else node.removeAttribute('aria-current');
    });
  $('heading').textContent = availableViews.find(([id]) => id === view)?.[1] || 'Playlist';
  $('summary').textContent = summaries[view] || 'A playlist shares reading progress with the rest of your library.';
  const items = model.selectItems(snapshot, view, $('search').value);
  $('count').textContent = items.length + (items.length === 1 ? ' item' : ' items');
  const dates = model.historyDates(snapshot);
  if (!items.length) {
    const empty = !key ? {title:'Connect your library', detail:'Use Connection or Settings to pair this browser.'} :
      !snapshot ? {title:connectionError ? 'Library unavailable' : 'Loading your library', detail:connectionError || 'Checking for saved articles and documents…'} :
      model.emptyState(view, $('search').value, records('reading_items').length > 0);
    reconcile($('library'), [{id:'empty', ...empty}], () => {
      const node = el('div', undefined, 'empty');
      const image = el('img'); image.src = '/icon.png'; image.alt = '';
      node.append(image, el('h2'), el('p')); return node;
    }, (node,entry) => { node.querySelector('h2').textContent = entry.title; node.querySelector('p').textContent = entry.detail; });
  } else reconcile($('library'), items.map(item => ({id:item.id,item})), entry => {
    const row = el('article', undefined, 'reading');
    row.dataset.icon = JSON.stringify([entry.item.sourceType, entry.item.sourceUrl]);
    row.append(sourceIcon(entry.item), el('div', undefined, 'reading-content'));
    return row;
  }, (row,{item}) => {
    const signature = rowSignature(item, dates);
    if (row.dataset.signature === signature) return;
    if (row.dataset.signature && (row.contains(document.activeElement) || row.querySelector('details[open]'))) return;
    const iconSignature = JSON.stringify([item.sourceType, item.sourceUrl]);
    if (row.dataset.icon !== iconSignature) { row.firstElementChild.replaceWith(sourceIcon(item)); row.dataset.icon = iconSignature; }
    row.dataset.signature = signature;
    fillRow(row.lastElementChild, item, dates);
  });
  updateControls();
  if (snapshot) {
    history.replaceState(history.state, '', libraryRoute());
    if (returnContext) {
      const context = returnContext; returnContext = null;
      sessionStorage.removeItem('yomu.libraryReturn');
      if (context.route === libraryRoute()) requestAnimationFrame(() => {
        const row = [...$('library').children].find(node => node.dataset.key === context.id);
        row?.querySelector('.read-action')?.focus({preventScroll:true});
        window.scrollTo(0, Number(context.scroll) || 0);
      });
    }
  }
}
function fillRow(content, item, dates) {
  const progress = lookup('reading_progress', item.id), status = model.importStatus(item), ready = model.canRead(item);
  content.replaceChildren();
  const heading = el('h2', item.title || 'Untitled reading');
  content.append(heading);
  const meta = el('div', undefined, 'metadata');
  meta.append(el('span', item.sourceName || (item.sourceType || 'Document').toUpperCase()),
    el('span', status.label, 'import-status ' + status.tone));
  if (ready) meta.append(el('span', Math.max(1, Math.round((item.estimatedSecondsAtOneX || 0) / 60)) + ' min'));
  content.append(meta);
  if (view === 'history' && dates.get(item.id)) {
    const time = el('time', 'Last read ' + new Intl.DateTimeFormat(undefined, {dateStyle:'medium', timeStyle:'short'}).format(dates.get(item.id)), 'history-date');
    time.dateTime = new Date(dates.get(item.id)).toISOString(); content.append(time);
  }
  if (!ready) content.append(el('p', status.detail, 'import-help'));
  if (progress && ready && (progress.completed || progress.characterOffset > 0)) {
    const percent = model.progressPercent(item, progress);
    const bar = el('progress'); bar.max = 100; bar.value = percent;
    bar.setAttribute('aria-label', 'Reading progress for ' + item.title);
    bar.setAttribute('aria-valuetext', Math.round(percent) + ' percent');
    const wrapper = el('div', undefined, 'progress'); wrapper.append(bar, el('span', Math.round(percent) + '%', 'progress-label')); content.append(wrapper);
  }
  const actions = el('div', undefined, 'actions');
  const read = actionButton('Read', () => openReader(item.id), {cls:'read-action', unavailable:!ready, label:'Read ' + item.title});
  if (!ready) read.title = 'Your phone needs to finish importing this item.';
  actions.append(read);
  const play = actionButton('Play · Coming later', () => {}, {
    cls:'secondary play-placeholder', unavailable:true,
    label:'Play ' + item.title + '. Website audio coming later.'
  });
  play.title = 'Website audio coming later. Listen in the phone app for now.';
  actions.append(play);
  const more = el('details', undefined, 'item-menu');
  const summary = el('summary', 'More');
  summary.setAttribute('aria-label', 'More actions for ' + item.title);
  const panel = el('div', undefined, 'menu-panel');
  panel.setAttribute('role', 'group');
  panel.setAttribute('aria-label', 'Actions for ' + item.title);
  const add = (text, action, unavailable = false, cls = 'menu-item') => panel.append(actionButton(text, () => runAction(async epoch => {
    await action(currentItem(item.id), epoch);
    more.open = false;
  }), {write:true, unavailable, cls}));
  add(progress?.completed ? 'Mark unread' : 'Mark read', async (fresh,epoch) => {
    const state = lookup('reading_progress', item.id);
    const completed = !state?.completed, now = Date.now();
    const changes = [change('reading_items', item.id, {...fresh, inToRead:!completed, archived:false, updatedAt:now}),
      change('reading_progress', item.id, {...(state || {itemId:item.id, sentenceIndex:0, characterOffset:0, playbackSpeed:1, lastListenedAt:0}), completed})];
    if (completed) {
      if (lookup('up_next', item.id)) changes.push(change('up_next', item.id, null));
      const id = crypto.randomUUID(); changes.push(change('read_history', id, {id, itemId:item.id, completedAt:now}));
    } else records('read_history').filter(h => h.itemId === item.id).forEach(h => changes.push(change('read_history', h.id, null)));
    await mutate(changes, completed ? 'Marked as read.' : 'Returned to To read.', epoch);
  });
  const queued = lookup('up_next', item.id);
  add(queued ? 'Remove from Up next' : 'Add to Up next', async (fresh,epoch) => {
    const entry = lookup('up_next', item.id);
    await mutate([change('up_next', item.id, entry ? null : {itemId:item.id, position:Math.max(-1,...records('up_next').map(q => q.position)) + 1, addedAt:Date.now()})],
      entry ? 'Removed from Up next.' : 'Added to Up next.', epoch);
  }, !queued && !ready);
  if (view === 'queue') {
    const ordered = records('up_next').sort((a,b) => a.position - b.position), position = ordered.findIndex(q => q.itemId === item.id);
    for (const [label,delta] of [['Move up',-1], ['Move down',1]]) add(label, async (fresh,epoch) => {
      const entries = records('up_next').sort((a,b) => a.position - b.position), index = entries.findIndex(q => q.itemId === item.id), destination = index + delta;
      if (index < 0 || destination < 0 || destination >= entries.length) return;
      [entries[index],entries[destination]] = [entries[destination],entries[index]];
      await mutate(entries.map((entry,n) => change('up_next', entry.itemId, {...entry, position:n})), 'Up next order updated.', epoch);
    }, position + delta < 0 || position + delta >= ordered.length);
  }
  add(item.archived ? 'Restore to To read' : 'Archive', async (fresh,epoch) => {
    const entry = lookup('up_next', item.id);
    await mutate([change('reading_items', item.id, {...fresh, archived:!fresh.archived, inToRead:!!fresh.archived, updatedAt:Date.now()}),
      ...(entry ? [change('up_next', item.id, null)] : [])], fresh.archived ? 'Restored to To read.' : 'Moved to Archive.', epoch);
  });
  panel.append(actionButton('Rename', () => {
    more.open = false; openName('rename', item.id);
  }, {write:true, cls:'menu-item'}));
  const destination = el('label', 'Add to playlist', 'playlist-label');
  const select = el('select');
  select.setAttribute('aria-label', 'Playlist for ' + item.title);
  const first = el('option', records('playlists').length ? 'Choose a playlist' : 'No playlists yet'); first.value = ''; select.append(first);
  for (const playlist of records('playlists')) { const option = el('option', playlist.name); option.value = playlist.id; select.append(option); }
  select.dataset.write = ''; select.dataset.unavailable = String(!records('playlists').length);
  destination.append(select); panel.append(destination);
  const addPlaylist = actionButton('Add to playlist', () => runAction(async epoch => {
    const playlistId = select.value;
    if (!playlistId) throw Error('Choose a playlist first.');
    if (!lookup('playlists', playlistId)) throw Error('That playlist was removed. Choose another playlist.');
    const id = playlistId + ':' + item.id;
    if (lookup('playlist_memberships', id)) { notice('Already in that playlist.'); more.open = false; return; }
    currentItem(item.id);
    await mutate([change('playlist_memberships', id, {playlistId, itemId:item.id,
      sortOrder:Math.max(-1,...records('playlist_memberships').filter(m => m.playlistId === playlistId).map(m => m.sortOrder)) + 1, addedAt:Date.now()})],
      'Added to playlist.', epoch);
    more.open = false;
  }), {write:true, unavailable:true, cls:'menu-item'});
  select.onchange = () => { addPlaylist.dataset.unavailable = String(!select.value); updateControls(); };
  panel.append(addPlaylist);
  if (!model.builtInViews.includes(view)) add('Remove from this playlist', async (fresh,epoch) => {
    await mutate([change('playlist_memberships', view + ':' + item.id, null)], 'Removed from this playlist. The saved item remains in your library.', epoch);
  });
  panel.append(actionButton('Import details', () => {
    const fresh = currentItem(item.id); $('detailsTitle').textContent = fresh.title || 'Import details';
    $('detailsText').textContent = model.importStatus(fresh).detail || 'Ready to read. Extracted text is available in your library.';
    more.open = false; $('itemDetails').showModal();
  }, {cls:'menu-item'}));
  add('Delete saved item', async (fresh,epoch) => {
    if (!confirm('Permanently delete “' + fresh.title + '” from your synced library? This also removes its queue, playlist, progress, and history entries. Archive it instead if you want to keep it.')) return;
    await mutate([change('reading_items', item.id, null), ...['up_next','reading_progress','playlist_memberships','read_history'].flatMap(kind =>
      (snapshot.records || []).filter(r => r.kind === kind && r.data?.itemId === item.id).map(r => change(kind, r.id, null))),
      ...(lookup('reading_cursors',item.id) ? [change('reading_cursors',item.id,null)] : [])], 'Saved item deleted.', epoch);
  }, false, 'menu-item destructive');
  more.append(summary,panel);
  more.addEventListener('toggle', () => {
    if (more.open) document.querySelectorAll('.item-menu[open]').forEach(other => { if (other !== more) other.open = false; });
    else render();
  });
  more.addEventListener('keydown', event => { if (event.key === 'Escape') { event.preventDefault(); more.open = false; summary.focus(); } });
  actions.append(more); content.append(actions);
}
function openReader(id) {
  const item = currentItem(id);
  if (!model.canRead(item)) { notice('Open Yomu on your phone and sync to finish importing this item.'); return; }
  const route = libraryRoute();
  sessionStorage.setItem('yomu.libraryReturn', JSON.stringify({route, id, scroll:window.scrollY}));
  location.href = '/reader.html?' + new URLSearchParams({id, from:route});
}
function openName(type, id) {
  nameAction = {type,id};
  $('nameTitle').textContent = type === 'rename' ? 'Rename saved item' : 'New playlist';
  $('nameLabel').textContent = type === 'rename' ? 'Reading title' : 'Playlist name';
  $('nameSubmit').textContent = type === 'rename' ? 'Save title' : 'Create playlist';
  $('nameInput').value = type === 'rename' ? currentItem(id).title : '';
  notice('', false, 'nameError'); $('nameInput').removeAttribute('aria-invalid');
  $('nameDialog').showModal(); $('nameInput').focus();
}
$('nameForm').onsubmit = event => {
  event.preventDefault();
  const name = $('nameInput').value.trim();
  if (!name) { notice('Enter a name.', true, 'nameError'); $('nameInput').setAttribute('aria-invalid','true'); return; }
  return runAction(async epoch => {
    if (nameAction.type === 'rename') {
      const item = currentItem(nameAction.id);
      await mutate([change('reading_items', item.id, {...item, title:name, updatedAt:Date.now()})], 'Title updated.', epoch);
    } else {
      const id = crypto.randomUUID();
      await mutate([change('playlists', id, {id, name, createdAt:Date.now(), sortOrder:records('playlists').length})], 'Playlist created.', epoch);
    }
    $('nameDialog').close();
  }, 'nameError');
};
$('saveLink').onsubmit = event => {
  event.preventDefault();
  const url = $('url').value.trim();
  return runAction(async epoch => {
    notice('Saving article…');
    const result = await api('articles', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({url})});
    acceptSnapshot(await api('state'), epoch);
    $('url').value = '';
    notice(result.status === 'already_saved' ? 'Already in your library. Use More to add it to a playlist or Up next.' :
      'Article saved. Waiting for your phone to prepare readable text.');
  });
};
async function upload(files) {
  const selected = [...files];
  if (!selected.length) return;
  return runAction(async epoch => {
    let saved = 0; const errors = [];
    for (const [index,file] of selected.entries()) {
      try {
        if (!/\.(pdf|docx|txt)$/i.test(file.name)) throw Error(file.name + ': choose a PDF, DOCX, or text file.');
        if (file.size > 20 * 1024 * 1024) throw Error(file.name + ' exceeds 20 MB.');
        notice('Uploading ' + file.name + ' (' + (index + 1) + ' of ' + selected.length + ')…');
        const id = crypto.randomUUID();
        await api('files/' + id, {method:'PUT', headers:{'Content-Type':file.type || 'application/octet-stream', 'X-File-Name':encodeURIComponent(file.name)}, body:file});
        const now = Date.now();
        await mutate([change('reading_items', id, {id, title:file.name, sourceType:file.name.split('.').pop().toLowerCase(), sourceName:file.name,
          fileId:id, normalizedText:'', importState:'pending', warning:'Waiting for phone to extract this document.', inToRead:true,
          importedAt:now, updatedAt:now, estimatedSecondsAtOneX:0, contentVersion:1, manualOrder:now, archived:false})], 'Document saved.', epoch);
        saved++;
      } catch (error) { errors.push(error.message); }
    }
    notice(saved + (saved === 1 ? ' document saved.' : ' documents saved.') + (errors.length ? ' ' + errors.join(' ') : ' Waiting for your phone to prepare the text.'), errors.length > 0);
    $('files').value = '';
  });
}
function openPair() {
  notice('', false, 'pairError'); $('key').removeAttribute('aria-invalid'); $('pair').showModal();
}
$('pairForm').onsubmit = event => {
  event.preventDefault();
  const candidate = $('key').value.trim();
  if (candidate.length < 32) { notice('Enter your complete private access key.', true, 'pairError'); $('key').setAttribute('aria-invalid','true'); return; }
  return runAction(async () => {
    notice('Checking the access key…', false, 'pairError');
    const result = await api('state', {}, candidate);
    key = candidate; connectionEpoch++;
    localStorage.setItem('yomu-key', key);
    $('key').value = ''; $('key').removeAttribute('aria-invalid');
    acceptSnapshot(result, connectionEpoch);
    $('pair').close(); notice('Connected to your library.');
  }, 'pairError');
};
$('disconnect').onclick = () => {
  if (writeDepth) return;
  key = ''; snapshot = null; connectionError = ''; connectionEpoch++;
  localStorage.removeItem('yomu-key'); localStorage.removeItem('yomu.lastCheck');
  render(); notice('Browser disconnected. Saved items stay on your phone and server.');
};
$('files').onchange = event => upload(event.target.files);
$('browseFiles').onclick = () => $('files').click();
$('drop').ondragover = event => { event.preventDefault(); };
$('drop').ondrop = event => { event.preventDefault(); if (!key || writeDepth) { notice('Connect your library and finish the current change before uploading.'); return; } upload(event.dataTransfer.files); };
$('refresh').onclick = () => refresh();
$('search').oninput = render;
$('pairButton').onclick = openPair;
$('closePair').onclick = () => $('pair').close();
$('closeName').onclick = () => $('nameDialog').close();
$('closeDetails').onclick = () => $('itemDetails').close();
$('newPlaylist').onclick = () => openName('create');
$('themeToggle').onclick = () => setYomuTheme(document.documentElement.dataset.theme === 'light' ? 'dark' : 'light');
for (const id of ['pair','nameDialog']) $(id).addEventListener('cancel', event => { if (writeDepth) event.preventDefault(); });
$('library').addEventListener('focusout', () => queueMicrotask(render));
document.addEventListener('click', event => {
  document.querySelectorAll('.item-menu[open]').forEach(menu => { if (!menu.contains(event.target)) menu.open = false; });
});
window.addEventListener('storage', event => {
  if (event.key !== 'yomu-key') return;
  key = event.newValue || ''; snapshot = null; connectionError = ''; connectionEpoch++;
  render(); if (key) refresh();
});
function sourceIcon(item) {
  const mark = el('div', (item.sourceType || 'DOC').toUpperCase(), 'filemark');
  if (item.sourceType !== 'url') return mark;
  try {
    const url = new URL(item.sourceUrl);
    if (!['https:','http:'].includes(url.protocol)) throw Error();
    mark.classList.add('site-icon'); mark.textContent = url.hostname.replace(/^www\./,'').slice(0,1).toUpperCase();
    const img = el('img'); img.alt = ''; img.referrerPolicy = 'no-referrer'; img.loading = 'lazy';
    // Lazy images must be connected to the document to enter the loading viewport.
    // Keep the source letter visible until a real icon is available.
    const fallback = el('span', mark.textContent);
    img.className = 'source-image';
    img.onload = () => { fallback.remove(); mark.classList.add('icon-loaded'); };
    mark.replaceChildren(fallback, img);
    let triedDirect = false;
    img.onerror = () => { if (!triedDirect) { triedDirect = true; img.src = 'https://' + url.host + '/favicon.ico'; } else { img.onload = null; img.onerror = null; img.remove(); } };
    img.src = 'https://www.google.com/s2/favicons?domain=' + encodeURIComponent(url.hostname) + '&sz=64';
    mark.title = url.hostname;
  } catch { mark.textContent = 'WEB'; }
  return mark;
}
render();
window.addEventListener('pageshow', event => {
  if (event.persisted) {
    try { returnContext = JSON.parse(sessionStorage.getItem('yomu.libraryReturn') || 'null'); } catch {}
    render();
  }
});
if (new URLSearchParams(location.search).get('connect') === '1' || !key) openPair();
if (key) refresh({quiet:true});
setInterval(() => {
  if (key && !document.hidden && !document.querySelector('dialog[open], .item-menu[open]')) refresh({quiet:true});
},15000);
