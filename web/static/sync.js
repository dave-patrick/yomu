/*
 * Shared library-sync mechanics for the website.
 *
 * Both the library list and the reading view mutate the same records, so the
 * meaning of an action lives here once. The pages keep their own presentation
 * and confirmation flows, but they always send the same change set for a given
 * action, which keeps the two views from drifting apart.
 */
(function(root) {
  'use strict';
  const live = (snapshot, kind) => (snapshot?.records || []).filter(r => r.kind === kind && r.data).map(r => r.data);
  function lookup(snapshot, kind, id) {
    return snapshot?.records.find(r => r.kind === kind && r.id === id)?.data || null;
  }
  function change(snapshot, kind, id, data) {
    return {kind, id, data, base: lookup(snapshot, kind, id)};
  }
  async function request(path, options = {}, accessKey) {
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
      if (error.name === 'TimeoutError') throw Error('Yomu took too long to respond. Try again.');
      if (error instanceof TypeError) throw Error('Cannot reach Yomu. Check your connection and try again.');
      throw error;
    }
  }
  async function mutate({snapshot, client, changes, accessKey}) {
    if (!snapshot) throw Error('Load your library before making changes.');
    return request('sync', {method:'POST', headers:{'Content-Type':'application/json'},
      body:JSON.stringify({client:'web-' + client, instance:snapshot.instance, changes})}, accessKey);
  }
  function item(snapshot, itemId) {
    const found = lookup(snapshot, 'reading_items', itemId);
    if (!found) throw Error('This item was removed from your library. Check for updates.');
    return found;
  }
  function toggleRead(snapshot, itemId) {
    const fresh = item(snapshot, itemId);
    const state = lookup(snapshot, 'reading_progress', itemId);
    const completed = !state?.completed, now = Date.now();
    const changes = [
      change(snapshot, 'reading_items', itemId, {...fresh, inToRead:!completed, archived:false, updatedAt:now}),
      change(snapshot, 'reading_progress', itemId, {...(state || {itemId, sentenceIndex:0, characterOffset:0, playbackSpeed:1, lastListenedAt:0}), completed})
    ];
    if (completed) {
      if (lookup(snapshot, 'up_next', itemId)) changes.push(change(snapshot, 'up_next', itemId, null));
      const id = crypto.randomUUID();
      changes.push(change(snapshot, 'read_history', id, {id, itemId, completedAt:now}));
    } else live(snapshot, 'read_history').filter(h => h.itemId === itemId)
      .forEach(h => changes.push(change(snapshot, 'read_history', h.id, null)));
    return {changes, message: completed ? 'Marked as read.' : 'Returned to To read.'};
  }
  function toggleQueue(snapshot, itemId) {
    const entry = lookup(snapshot, 'up_next', itemId);
    return {changes: [change(snapshot, 'up_next', itemId, entry ? null :
      {itemId, position:Math.max(-1,...live(snapshot, 'up_next').map(q => q.position)) + 1, addedAt:Date.now()})],
      message: entry ? 'Removed from Up next.' : 'Added to Up next.'};
  }
  function moveInQueue(snapshot, itemId, delta) {
    const entries = live(snapshot, 'up_next').sort((a,b) => a.position - b.position);
    const index = entries.findIndex(q => q.itemId === itemId), destination = index + delta;
    if (index < 0 || destination < 0 || destination >= entries.length) return null;
    [entries[index],entries[destination]] = [entries[destination],entries[index]];
    return {changes: entries.map((entry,n) => change(snapshot, 'up_next', entry.itemId, {...entry, position:n})),
      message: 'Up next order updated.'};
  }
  function toggleArchive(snapshot, itemId) {
    const fresh = item(snapshot, itemId);
    const entry = lookup(snapshot, 'up_next', itemId);
    return {changes: [change(snapshot, 'reading_items', itemId,
        {...fresh, archived:!fresh.archived, inToRead:!!fresh.archived, updatedAt:Date.now()}),
      ...(entry ? [change(snapshot, 'up_next', itemId, null)] : [])],
      message: fresh.archived ? 'Restored to To read.' : 'Moved to Archive.'};
  }
  function addToPlaylist(snapshot, itemId, playlistId) {
    if (!playlistId) throw Error('Choose a playlist first.');
    if (!lookup(snapshot, 'playlists', playlistId)) throw Error('That playlist was removed. Choose another playlist.');
    const id = playlistId + ':' + itemId;
    if (lookup(snapshot, 'playlist_memberships', id)) throw Error('Already in that playlist.');
    item(snapshot, itemId);
    return {changes: [change(snapshot, 'playlist_memberships', id, {playlistId, itemId,
        sortOrder:Math.max(-1,...live(snapshot, 'playlist_memberships')
          .filter(m => m.playlistId === playlistId).map(m => m.sortOrder)) + 1, addedAt:Date.now()})],
      message: 'Added to playlist.'};
  }
  function removeFromPlaylist(snapshot, itemId, playlistId) {
    return {changes: [change(snapshot, 'playlist_memberships', playlistId + ':' + itemId, null)],
      message: 'Removed from this playlist. The saved item remains in your library.'};
  }
  function renameItem(snapshot, itemId, title) {
    const name = String(title || '').trim();
    if (!name) throw Error('Enter a title.');
    const fresh = item(snapshot, itemId);
    return {changes: [change(snapshot, 'reading_items', itemId, {...fresh, title:name, updatedAt:Date.now()})],
      message: 'Title updated.'};
  }
  function deleteItem(snapshot, itemId) {
    item(snapshot, itemId);
    return {changes: [change(snapshot, 'reading_items', itemId, null),
      ...['up_next','reading_progress','playlist_memberships','read_history'].flatMap(kind =>
        (snapshot.records || []).filter(r => r.kind === kind && r.data?.itemId === itemId)
          .map(r => change(snapshot, kind, r.id, null))),
      ...(lookup(snapshot, 'reading_cursors', itemId) ? [change(snapshot, 'reading_cursors', itemId, null)] : [])],
      message: 'Saved item deleted.'};
  }
  const api = {live, lookup, change, api:request, mutate, item, toggleRead, toggleQueue, moveInQueue,
    toggleArchive, addToPlaylist, removeFromPlaylist, renameItem, deleteItem};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.YomuSync = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
