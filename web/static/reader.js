'use strict';
const readerModel = YomuReader;
const readerNode = id => document.getElementById(id);
const query = new URLSearchParams(location.search);
const itemId = query.get('id');
const returnUrl = readerModel.libraryUrl(query.get('from') || '/');
for (const id of ['backLibrary','endLibrary']) readerNode(id).href = returnUrl;
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
  };
}
readerNode('siteTheme').onchange = event => setYomuTheme(event.target.value);
readerNode('resetReading').onclick = () => { prefs = readerModel.preferences(); localStorage.removeItem('yomu.reading'); applyReading(); };
readerNode('appearance').addEventListener('keydown', event => {
  if (event.key === 'Escape') { readerNode('appearance').open = false; readerNode('appearance').querySelector('summary').focus(); }
});
document.addEventListener('click', event => { if (!readerNode('appearance').contains(event.target)) readerNode('appearance').open = false; });
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
    for (const text of readerModel.paragraphs(item.normalizedText)) { const paragraph = document.createElement('p'); paragraph.textContent = text; fragment.append(paragraph); }
    readerNode('readerText').replaceChildren(fragment);
    readerNode('readerArticle').hidden = false;
    readerNode('readerNotice').textContent = '';
    readerNode('readerArticle').focus({preventScroll:true});
  } catch (error) {
    if (epoch !== loadingEpoch) return;
    readerNode('readerNotice').textContent = error.name === 'TimeoutError' ? 'Yomu took too long to respond. Try again.' : error instanceof TypeError ? 'Cannot reach your library. Check your connection and try again.' : error.message;
    readerNode('readerRecovery').hidden = false;
  }
}
readerNode('retryReader').onclick = loadArticle;
applyReading(); loadArticle();
