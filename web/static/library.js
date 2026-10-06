(function(root) {
  'use strict';
  const builtInViews = ['toRead', 'queue', 'history', 'archive'];
  const live = (snapshot, kind) => (snapshot?.records || []).filter(r => r.kind === kind && r.data).map(r => r.data);
  function historyDates(snapshot) {
    const dates = new Map();
    for (const entry of live(snapshot, 'read_history')) dates.set(entry.itemId, Math.max(dates.get(entry.itemId) || 0, Number(entry.completedAt) || 0));
    return dates;
  }
  function selectItems(snapshot, view, query = '') {
    let items = live(snapshot, 'reading_items');
    const byId = new Map(items.map(item => [item.id, item]));
    if (view === 'toRead') items = items.filter(i => i.inToRead && !i.archived).sort((a,b) => (b.manualOrder || 0) - (a.manualOrder || 0));
    else if (view === 'queue') items = live(snapshot, 'up_next').sort((a,b) => a.position - b.position).map(q => byId.get(q.itemId)).filter(Boolean);
    else if (view === 'history') {
      const dates = historyDates(snapshot);
      items = items.filter(i => dates.has(i.id)).sort((a,b) => dates.get(b.id) - dates.get(a.id));
    } else if (view === 'archive') items = items.filter(i => i.archived).sort((a,b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    else items = live(snapshot, 'playlist_memberships').filter(m => m.playlistId === view).sort((a,b) => a.sortOrder - b.sortOrder).map(m => byId.get(m.itemId)).filter(Boolean);
    const needle = query.trim().toLocaleLowerCase();
    return items.filter(i => (String(i.title || '') + ' ' + String(i.sourceName || '')).toLocaleLowerCase().includes(needle));
  }
  function importStatus(item) {
    if (item.importState === 'failed') return {label:'Import failed', detail:item.warning || 'Open Yomu on your phone to retry this import.', tone:'error'};
    if (item.importState === 'fetching') return {label:'Fetching article', detail:item.warning || 'Your phone is fetching this article.', tone:'pending'};
    if (item.importState === 'extracting') return {label:'Extracting text', detail:item.warning || 'Your phone is preparing readable text.', tone:'pending'};
    if (typeof item.normalizedText === 'string' && item.normalizedText.trim()) return {label:'Ready', detail:'', tone:'ready'};
    return {label:'Waiting for phone', detail:item.warning || 'Open Yomu on your phone and sync to prepare this item.', tone:'pending'};
  }
  function canRead(item) { return typeof item.normalizedText === 'string' && !!item.normalizedText.trim(); }
  function emptyState(view, query, hasItems) {
    if (query.trim()) return {title:'No matches', detail:'Try a different title or source, or clear your search.'};
    if (view === 'queue') return {title:'Up next is clear', detail:'Use an item’s More menu to add it to your listening queue.'};
    if (view === 'history') return {title:'No reading history yet', detail:'Finished or explicitly marked-read items appear here.'};
    if (view === 'archive') return {title:'Nothing archived', detail:'Archive items from their More menu when you want to keep them out of To read.'};
    if (!builtInViews.includes(view)) return {title:'This playlist is empty', detail:'Add saved items using their More menu. They can belong to more than one playlist.'};
    return hasItems ? {title:'You’re caught up', detail:'Your other saved items are in playlists, Archive, or Read history.'} : {title:'Start your library', detail:'Save an article link or browse for a PDF, Word document, or text file.'};
  }
  function progressPercent(item, progress) {
    if (!canRead(item) || !progress) return 0;
    return Math.max(0, Math.min(100, progress.completed ? 100 : 100 * (Number(progress.characterOffset) || 0) / item.normalizedText.length));
  }
  function createSerialQueue() {
    let tail = Promise.resolve(), pending = 0;
    return {
      get pending() { return pending; },
      run(action) {
        pending++;
        const result = tail.then(action);
        tail = result.catch(() => {});
        return result.finally(() => { pending--; });
      }
    };
  }
  const api = {builtInViews, live, historyDates, selectItems, importStatus, canRead, emptyState, progressPercent, createSerialQueue};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.YomuLibrary = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);

