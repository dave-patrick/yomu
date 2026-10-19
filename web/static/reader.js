'use strict';
const readerModel = YomuReader;
const sync = YomuSync;
const readerNode = id => document.getElementById(id);
const query = new URLSearchParams(location.search);
const itemId = query.get('id');
const returnUrl = readerModel.libraryUrl(query.get('from') || '/');
for (const id of ['backLibrary','endLibrary']) readerNode(id).href = returnUrl;
const client = localStorage.getItem('yomu-client') || crypto.randomUUID();
localStorage.setItem('yomu-client', client);
let libraryState = null;
let writing = false;
let prefs;
try { prefs = readerModel.preferences(JSON.parse(localStorage.getItem('yomu.reading') || '{}')); }
catch { prefs = readerModel.preferences(); }
function applyReading() {
  document.documentElement.style.setProperty('--reader-font', readerModel.fonts[prefs.font]);
  document.documentElement.style.setProperty('--reader-size', prefs.size + 'px');
  document.documentElement.style.setProperty('--reader-spacing', prefs.spacing);
  document.documentElement.style.setProperty('--reader-width', readerModel.widths[prefs.width]);
  for (const [id,key] of [['readerFont','font'],['readerSize','size'],['readerSpacing','spacing'],['readerWidth','width']]) readerNode(id).value = prefs[key];
}
for (const [id,key] of [['readerFont','font'],['readerSize','size'],['readerSpacing','spacing'],['readerWidth','width']]) {
  readerNode(id).onchange = event => {
    prefs = readerModel.preferences({...prefs, [key]:['size','spacing'].includes(key) ? Number(event.target.value) : event.target.value});
    localStorage.setItem('yomu.reading', JSON.stringify(prefs)); applyReading();
    localStorage.setItem('yomu.readingOverride','true'); readerNode('localReadingOverride').checked = true;
  };
}
readerNode('siteTheme').onchange = event => setYomuTheme(event.target.value);
readerNode('resetReading').onclick = () => {
  prefs = readerModel.preferences(); localStorage.removeItem('yomu.reading');
  localStorage.setItem('yomu.readingOverride','true'); readerNode('localReadingOverride').checked = true; applyReading();
};
readerNode('appearance').addEventListener('keydown', event => {
  if (event.key === 'Escape') { readerNode('appearance').open = false; readerNode('appearance').querySelector('summary').focus(); }
});
document.addEventListener('click', event => {
  if (!readerNode('appearance').contains(event.target)) readerNode('appearance').open = false;
  if (!readerNode('articleActions').contains(event.target)) readerNode('articleActions').open = false;
});
window.addEventListener('storage', event => {
  if (event.key === 'yomu-key') loadArticle();
  if (event.key === 'yomu.reading') { try { prefs = readerModel.preferences(JSON.parse(event.newValue || '{}')); } catch { prefs = readerModel.preferences(); } applyReading(); }
});
let loadingEpoch = 0;
async function loadArticle() {
  const epoch = ++loadingEpoch;
  readerNode('readerArticle').hidden = true;
  readerNode('readerRecovery').hidden = true;
  readerNode('readerNotice').textContent = 'Loading your article…';
  try {
    if (!itemId) throw Error('Choose an article from your library to start reading.');
    const key = localStorage.getItem('yomu-key');
    if (!key) throw Error('Connect this browser to your library in Settings, then return here.');
    const response = await fetch('/api/state', {headers:{Authorization:'Bearer ' + key}, signal:AbortSignal.timeout(20000)});
    if (epoch !== loadingEpoch) return;
    if (!response.ok) throw Error(response.status === 401 ? 'Your access key was rejected. Reconnect this browser in Settings.' : 'Your library is unavailable. Try again.');
    const state = await response.json();
    if (epoch !== loadingEpoch) return;
    const item = state.records.find(record => record.kind === 'reading_items' && record.id === itemId)?.data;
    if (!item) throw Error('This article is no longer in your library.');
    if (!item.normalizedText?.trim()) throw Error('Your phone is still preparing this item. Open Yomu on your phone and sync, then try again.');
    readerNode('readerTitle').textContent = item.title || 'Untitled reading';
    document.title = (item.title || 'Reader') + ' · Yomu';
    readerNode('readerMeta').textContent = [item.sourceName || 'Saved document', Math.max(1,Math.round((item.estimatedSecondsAtOneX || 0)/60)) + ' min'].join(' · ');
    const source = readerModel.sourceUrl(item.sourceUrl);
    readerNode('readerSource').hidden = !source;
    if (source) readerNode('readerSource').href = source; else readerNode('readerSource').removeAttribute('href');
    const fragment = document.createDocumentFragment();
    let offset = 0;
    for (const text of readerModel.paragraphs(item.normalizedText)) {
      const paragraph = document.createElement('p'); paragraph.textContent = text;
      const found = item.normalizedText.indexOf(text,offset); paragraph.dataset.offset = Math.max(0,found);
      offset = found + text.length; fragment.append(paragraph);
    }
    readerNode('readerText').replaceChildren(fragment);
    readerNode('readerArticle').hidden = false;
    readerNode('readerNotice').textContent = '';
    readerNode('readerArticle').focus({preventScroll:true});
    libraryState = state;
    renderActions();
    renderEndActions();
    await connectResume(state,item);
  } catch (error) {
    if (epoch !== loadingEpoch) return;
    readerNode('readerNotice').textContent = error.name === 'TimeoutError' ? 'Yomu took too long to respond. Try again.' : error instanceof TypeError ? 'Cannot reach your library. Check your connection and try again.' : error.message;
    readerNode('readerRecovery').hidden = false;
  }
}
function actionStatus(message, isError = false) {
  const node = readerNode('actionStatus');
  node.textContent = message;
  node.classList.toggle('is-error', isError);
}
function el(tag, text, cls) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (cls) node.className = cls;
  return node;
}
function actionButton(text, action, {unavailable = false, cls = 'menu-item'} = {}) {
  const node = el('button', text, cls);
  node.type = 'button';
  node.disabled = unavailable || writing;
  node.onclick = () => Promise.resolve().then(action).catch(error => actionStatus(error.message, true));
  return node;
}
async function runAction(action) {
  if (writing) { actionStatus('Wait for the current change to finish.', true); return; }
  writing = true;
  renderActions();
  try {
    const key = localStorage.getItem('yomu-key');
    const result = await action(key);
    if (result) {
      if (result.snapshot) libraryState = result.snapshot;
      const conflicts = result.snapshot?.conflicts?.length;
      actionStatus(conflicts ? 'Some changes conflicted with another device. The current server value was kept.' : (result.message || 'Saved.'));
    }
  } catch (error) {
    actionStatus(error.message, true);
  } finally {
    writing = false;
    renderActions();
    renderEndActions();
  }
}
function renderActions() {
  const menu = readerNode('articleActions');
  const panel = menu.querySelector('.menu-panel');
  if (!libraryState) { menu.hidden = true; return; }
  const item = sync.lookup(libraryState, 'reading_items', itemId);
  if (!item) { menu.hidden = true; return; }
  menu.hidden = false;
  const status = readerNode('actionStatus');
  const fragment = document.createDocumentFragment();
  fragment.append(status);
  const add = (text, action, unavailable = false, cls = 'menu-item') => {
    const node = actionButton(text, () => runAction(async key => {
      const outcome = action();
      const snapshot = await sync.mutate({snapshot:libraryState, client, changes:outcome.changes, accessKey:key});
      return {snapshot, message:outcome.message};
    }), {unavailable, cls});
    fragment.append(node);
  };
  const progress = sync.lookup(libraryState, 'reading_progress', itemId);
  add(progress?.completed ? 'Mark unread' : 'Mark read', () => sync.toggleRead(libraryState, itemId));
  const queued = sync.lookup(libraryState, 'up_next', itemId);
  add(queued ? 'Remove from Up next' : 'Add to Up next', () => sync.toggleQueue(libraryState, itemId));
  if (queued) {
    const ordered = sync.live(libraryState, 'up_next').sort((a,b) => a.position - b.position);
    const position = ordered.findIndex(q => q.itemId === itemId);
    for (const [label,delta] of [['Move up',-1], ['Move down',1]]) {
      add(label, () => sync.moveInQueue(libraryState, itemId, delta), position + delta < 0 || position + delta >= ordered.length);
    }
  }
  add(item.archived ? 'Restore to To read' : 'Archive', () => sync.toggleArchive(libraryState, itemId));
  fragment.append(actionButton('Rename', () => {
    const name = prompt('New title for this saved item', item.title || '');
    if (name === null) return;
    return runAction(async key => {
      const outcome = sync.renameItem(libraryState, itemId, name);
      const snapshot = await sync.mutate({snapshot:libraryState, client, changes:outcome.changes, accessKey:key});
      readerNode('readerTitle').textContent = name.trim();
      document.title = name.trim() + ' · Yomu';
      return {snapshot, message:outcome.message};
    });
  }, {cls:'menu-item'}));
  const playlists = sync.live(libraryState, 'playlists').sort((a,b) => a.sortOrder - b.sortOrder);
  const destination = el('label', 'Add to playlist', 'playlist-label');
  const select = el('select');
  select.setAttribute('aria-label', 'Playlist for this article');
  const first = el('option', playlists.length ? 'Choose a playlist' : 'No playlists yet');
  first.value = ''; select.append(first);
  for (const playlist of playlists) { const option = el('option', playlist.name); option.value = playlist.id; select.append(option); }
  destination.append(select);
  fragment.append(destination);
  const addPlaylist = actionButton('Add to playlist', () => runAction(async key => {
    const outcome = sync.addToPlaylist(libraryState, itemId, select.value);
    const snapshot = await sync.mutate({snapshot:libraryState, client, changes:outcome.changes, accessKey:key});
    return {snapshot, message:outcome.message};
  }), {unavailable:!playlists.length, cls:'menu-item'});
  select.onchange = () => { addPlaylist.disabled = !select.value || writing; };
  fragment.append(addPlaylist);
  const memberships = sync.live(libraryState, 'playlist_memberships').filter(m => m.itemId === itemId);
  for (const membership of memberships) {
    const playlist = playlists.find(p => p.id === membership.playlistId);
    add('Remove from “' + (playlist?.name || 'this playlist') + '”', () => sync.removeFromPlaylist(libraryState, itemId, membership.playlistId));
  }
  fragment.append(actionButton('Delete saved item', () => {
    if (!confirm('Permanently delete “' + item.title + '” from your synced library? This also removes its queue, playlist, progress, and history entries. Archive it instead if you want to keep it.')) return;
    return runAction(async key => {
      const outcome = sync.deleteItem(libraryState, itemId);
      const snapshot = await sync.mutate({snapshot:libraryState, client, changes:outcome.changes, accessKey:key});
      location.href = returnUrl;
      return {snapshot, message:outcome.message};
    });
  }, {cls:'menu-item destructive'}));
  panel.replaceChildren(fragment);
}
function endStatus(message, isError = false) {
  const node = readerNode('endActionStatus');
  node.textContent = message;
  node.classList.toggle('is-error', isError);
}
function renderEndActions() {
  const panel = readerNode('endActions');
  const item = libraryState ? sync.lookup(libraryState, 'reading_items', itemId) : null;
  if (!item) { panel.hidden = true; return; }
  panel.hidden = false;
  const archive = readerNode('endArchive');
  const remove = readerNode('endDelete');
  archive.textContent = item.archived ? 'Restore to To read' : 'Archive this article';
  archive.disabled = writing;
  remove.disabled = writing;
}
async function runEndAction(action) {
  if (writing) { endStatus('Wait for the current change to finish.', true); return; }
  writing = true;
  renderActions();
  renderEndActions();
  try {
    const key = localStorage.getItem('yomu-key');
    const outcome = await action(key);
    if (outcome && outcome.snapshot) libraryState = outcome.snapshot;
    endStatus(outcome?.message || 'Saved.');
  } catch (error) {
    endStatus(error.message, true);
  } finally {
    writing = false;
    renderActions();
    renderEndActions();
  }
}
readerNode('endArchive').onclick = () => runEndAction(async key => {
  const outcome = sync.toggleArchive(libraryState, itemId);
  const snapshot = await sync.mutate({snapshot:libraryState, client, changes:outcome.changes, accessKey:key});
  return {snapshot, message:outcome.message};
});
readerNode('endDelete').onclick = () => {
  const item = sync.lookup(libraryState, 'reading_items', itemId);
  if (!item) return;
  if (!confirm('Permanently delete “' + item.title + '” from your synced library? This also removes its queue, playlist, progress, and history entries. Archive it instead if you want to keep it.')) return;
  return runEndAction(async key => {
    const outcome = sync.deleteItem(libraryState, itemId);
    const snapshot = await sync.mutate({snapshot:libraryState, client, changes:outcome.changes, accessKey:key});
    location.href = returnUrl;
    return {snapshot, message:outcome.message};
  });
};
readerNode('articleActions').addEventListener('keydown', event => {
  if (event.key === 'Escape') { event.preventDefault(); readerNode('articleActions').open = false; readerNode('articleActions').querySelector('summary').focus(); }
});
readerNode('retryReader').onclick = loadArticle;
applyReading(); loadArticle();
