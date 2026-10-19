'use strict';
const $ = id => document.getElementById(id);
const model = YomuLibrary;
const sync = YomuSync;
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
const lookup = (kind,id) => sync.lookup(snapshot, kind, id);
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
  return sync.api(path, options, accessKey);
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
const change = (kind,id,data) => sync.change(snapshot, kind, id, data);
async function mutate(changes, message, epoch) {
  if (!snapshot) throw Error('Load your library before making changes.');
  const result = await sync.mutate({snapshot, client, changes, accessKey:key});
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
  const query = $('search').value;
  $('openSearch').classList.toggle('has-query', !!query);
  $('clearSearch').hidden = !query;
  $('clearSearch').textContent = query ? 'Clear search: ' + query : '';
  const items = model.selectItems(snapshot, view, query);
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
  renderPlaylistManager();
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
  if (item.sourceType === 'newsletter' && item.importedAt) {
    const received = el('time', 'Received ' + new Intl.DateTimeFormat(undefined, {dateStyle:'medium', timeStyle:'short'}).format(item.importedAt), 'history-date');
    received.dateTime = new Date(item.importedAt).toISOString(); content.append(received);
  }
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
    const action = sync.toggleRead(snapshot, item.id);
    await mutate(action.changes, action.message, epoch);
  });
  const queued = lookup('up_next', item.id);
  add(queued ? 'Remove from Up next' : 'Add to Up next', async (fresh,epoch) => {
    const action = sync.toggleQueue(snapshot, item.id);
    await mutate(action.changes, action.message, epoch);
  }, !queued && !ready);
  if (view === 'queue') {
    const ordered = records('up_next').sort((a,b) => a.position - b.position), position = ordered.findIndex(q => q.itemId === item.id);
    for (const [label,delta] of [['Move up',-1], ['Move down',1]]) add(label, async (fresh,epoch) => {
      const moved = sync.moveInQueue(snapshot, item.id, delta);
      if (!moved) return;
      await mutate(moved.changes, moved.message, epoch);
    }, position + delta < 0 || position + delta >= ordered.length);
  }
  add(item.archived ? 'Restore to To read' : 'Archive', async (fresh,epoch) => {
    const action = sync.toggleArchive(snapshot, item.id);
    await mutate(action.changes, action.message, epoch);
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
    const action = sync.addToPlaylist(snapshot, item.id, select.value);
    await mutate(action.changes, action.message, epoch);
    more.open = false;
  }), {write:true, unavailable:true, cls:'menu-item'});
  select.onchange = () => { addPlaylist.dataset.unavailable = String(!select.value); updateControls(); };
  panel.append(addPlaylist);
  if (!model.builtInViews.includes(view)) add('Remove from this playlist', async (fresh,epoch) => {
    const action = sync.removeFromPlaylist(snapshot, item.id, view);
    await mutate(action.changes, action.message, epoch);
  });
  panel.append(actionButton('Import details', () => {
    const fresh = currentItem(item.id); $('detailsTitle').textContent = fresh.title || 'Import details';
    $('detailsText').textContent = model.importStatus(fresh).detail || 'Ready to read. Extracted text is available in your library.';
    more.open = false; $('itemDetails').showModal();
  }, {cls:'menu-item'}));
  add('Delete saved item', async (fresh,epoch) => {
    if (!confirm('Permanently delete “' + fresh.title + '” from your synced library? This also removes its queue, playlist, progress, and history entries. Archive it instead if you want to keep it.')) return;
    const action = sync.deleteItem(snapshot, item.id);
    await mutate(action.changes, action.message, epoch);
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
      const action = sync.renameItem(snapshot, nameAction.id, name);
      await mutate(action.changes, action.message, epoch);
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
function positionSearch() {
  const anchor = $('openSearch').getBoundingClientRect();
  const popup = $('searchPopup');
  popup.style.left = Math.max(16, Math.min(anchor.right - popup.offsetWidth, innerWidth - popup.offsetWidth - 16)) + 'px';
  popup.style.top = Math.max(16, Math.min(anchor.bottom + 8, innerHeight - popup.offsetHeight - 16)) + 'px';
}
$('searchPopup').addEventListener('toggle', event => {
  const open = event.newState === 'open';
  $('openSearch').setAttribute('aria-expanded', String(open));
  if (open) { positionSearch(); $('search').focus(); }
});
window.addEventListener('resize', () => { if ($('searchPopup').matches(':popover-open')) positionSearch(); });
function closeSearch() { $('searchPopup').hidePopover(); $('openSearch').focus(); }
$('closeSearch').onclick = closeSearch;
$('search').addEventListener('keydown', event => { if (event.key === 'Escape') { event.preventDefault(); closeSearch(); } });
$('searchForm').onsubmit = event => { event.preventDefault(); $('searchPopup').hidePopover(); $('openSearch').focus(); };
$('clearSearch').onclick = () => { $('search').value = ''; render(); $('openSearch').focus(); };

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

function renderPlaylistManager() {
  if ($('playlistManager').hidden) return;
  const rows = records('playlists').sort((a,b) => a.sortOrder-b.sortOrder || a.name.localeCompare(b.name));
  $('playlistManagerRows').replaceChildren();
  if (!rows.length) $('playlistManagerRows').append(el('p','No playlists yet.'));
  rows.forEach((p,index) => {
    const row = el('div',undefined,'actions'); row.append(el('strong',p.name));
    const move = direction => runAction(async epoch => {
      const ordered = records('playlists').sort((a,b) => a.sortOrder-b.sortOrder || a.name.localeCompare(b.name));
      const from = ordered.findIndex(x => x.id === p.id), to = from+direction;
      if (from < 0 || to < 0 || to >= ordered.length) return;
      ordered.splice(to,0,ordered.splice(from,1)[0]);
      await mutate(ordered.map((x,i) => change('playlists',x.id,{...x,sortOrder:i})), 'Playlist order saved.',epoch);
    });
    row.append(actionButton('Move up',()=>move(-1),{write:true,unavailable:index===0,label:'Move '+p.name+' up'}),
      actionButton('Move down',()=>move(1),{write:true,unavailable:index===rows.length-1,label:'Move '+p.name+' down'}),
      actionButton('Delete',()=>runAction(async epoch=>{
        if (!confirm('Delete playlist “'+p.name+'”? Its saved articles stay in your library.')) return;
        await mutate([change('playlists',p.id,null),...records('playlist_memberships').filter(m=>m.playlistId===p.id).map(m=>change('playlist_memberships',m.playlistId+':'+m.itemId,null))],'Playlist deleted. Articles kept.',epoch);
      }),{write:true,label:'Delete playlist '+p.name}));
    $('playlistManagerRows').append(row);
  });
}
$('managePlaylists').onclick = () => { $('playlistManager').hidden=false; renderPlaylistManager(); $('playlistManager').scrollIntoView({block:'start'}); };
$('closePlaylistManager').onclick = () => { $('playlistManager').hidden=true; $('managePlaylists').focus(); };
