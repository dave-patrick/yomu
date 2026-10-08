const node = id => document.getElementById(id);
let inbox = null, selected = null, busy = false;
function announce(message, error = false) {
  node('newsletterNotice').textContent = message;
  node('newsletterNotice').dataset.error = String(error);
}
async function newsletterApi(path = '', options = {}) {
  const key = localStorage.getItem('yomu-key');
  if (!key) throw new Error('Connect this browser from the library before managing newsletters.');
  const response = await fetch('/api/newsletters' + path, {...options, headers: {...options.headers, Authorization: 'Bearer ' + key}});
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(response.status === 401 ? 'Reconnect this browser from the library; its access key was rejected.' : body.detail || 'That action failed. Try again.');
  }
  return response.json();
}
function element(tag, text, className) {
  const result = document.createElement(tag);
  if (text !== undefined) result.textContent = text;
  if (className) result.className = className;
  return result;
}
function playlists(select, current) {
  select.replaceChildren();
  const placeholder = element('option', 'Choose a playlist'); placeholder.value = ''; select.append(placeholder);
  for (const playlist of inbox.playlists) {
    const option = element('option', playlist.name); option.value = playlist.id; select.append(option);
  }
  select.value = current || '';
}
async function action(work) {
  if (busy) return;
  busy = true;
  document.querySelectorAll('button').forEach(button => button.disabled = true);
  try { const message = await work(); await refresh(); if (typeof message === 'string') announce(message); }
  catch (error) { announce(error.message, true); }
  finally { busy = false; document.querySelectorAll('button').forEach(button => button.disabled = false); }
}
function button(text, callback, className = 'secondary') {
  const result = element('button', text, className); result.type = 'button'; result.onclick = callback; return result;
}
function render() {
  node('receiveStatus').textContent = inbox.receivingEnabled && inbox.receivingAddress ? 'Email receiving is configured. Forward newsletters to the address below.' : 'Email receiving is not connected yet. You can upload a sample and set up routing now.';
  node('receivingAddress').hidden = !inbox.receivingEnabled || !inbox.receivingAddress;
  node('receivingAddress').textContent = inbox.receivingAddress;
  node('newsletterRules').replaceChildren();
  if (!inbox.rules.length) node('newsletterRules').append(element('p', 'No rules yet. Upload or receive a sample, preview it, then choose a playlist.', 'hint'));
  for (const rule of inbox.rules) {
    const row = element('div', undefined, 'newsletter-row');
    row.append(element('h3', rule.sender), element('p', (rule.enabled ? 'Active' : 'Paused') + (rule.list_id ? ' · List-ID: ' + rule.list_id : ' · Matches exact sender')));
    const controls = element('div', undefined, 'newsletter-controls');
    const select = element('select'); select.setAttribute('aria-label', 'Playlist for ' + rule.sender); playlists(select, rule.playlist_id);
    const save = enabled => action(async () => {
      if (!select.value) throw new Error('Choose a destination playlist.');
      await newsletterApi('/rules/' + rule.id, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({playlistId:select.value, enabled})});
      announce('Rule saved. Existing issues stay in their current playlists.');
    });
    controls.append(select, button('Save destination', () => save(!!rule.enabled)), button(rule.enabled ? 'Pause' : 'Enable', () => save(!rule.enabled)));
    row.append(controls); node('newsletterRules').append(row);
  }
  node('newsletterDeliveries').replaceChildren();
  if (!inbox.deliveries.length) node('newsletterDeliveries').append(element('p', 'No deliveries yet. Start with a sample above.', 'hint'));
  const labels = {needs_routing:'Choose a playlist', possible_duplicate:'Possible duplicate — review before saving', queued:'Preparing', paused:'Rule paused or received before activation', imported:'Saved to library', duplicate:'Duplicate delivery skipped', failed:'Import failed', ignored:'Dismissed', expired:'Preview expired'};
  for (const delivery of inbox.deliveries) {
    const row = element('div', undefined, 'newsletter-row');
    row.append(element('h3', delivery.title), element('p', [delivery.sender, labels[delivery.state] || delivery.state, new Date(delivery.receivedAt).toLocaleString()].filter(Boolean).join(' · ')));
    if (delivery.error) row.append(element('p', delivery.error));
    const controls = element('div', undefined, 'newsletter-controls');
    if (['needs_routing','paused','possible_duplicate'].includes(delivery.state)) {
      controls.append(button('Preview & choose playlist', () => action(() => preview(delivery))), button('Dismiss', () => action(async () => {await newsletterApi('/deliveries/' + delivery.id, {method:'DELETE'}); announce('Delivery dismissed.');})));
    }
    if (delivery.state === 'failed') controls.append(button('Retry', () => action(() => newsletterApi('/deliveries/' + delivery.id + '/retry', {method:'POST'}))), button('Dismiss', () => action(() => newsletterApi('/deliveries/' + delivery.id, {method:'DELETE'}))));
    if (delivery.itemId) {const link = element('a', 'Read issue'); link.href = '/reader.html?id=' + encodeURIComponent(delivery.itemId); controls.append(link);}
    row.append(controls); node('newsletterDeliveries').append(row);
  }
}
async function refresh() {
  inbox = await newsletterApi(); render();
}
async function preview(delivery) {
  const content = await newsletterApi('/deliveries/' + delivery.id);
  selected = delivery;
  node('previewHeading').textContent = content.title;
  node('previewSource').textContent = content.sender + (content.listId ? ' · ' + content.listId : '');
  node('previewWarning').textContent = delivery.state === 'possible_duplicate' ? 'Similar content already arrived. Save only if this is a separate issue.' : content.forwarded ? 'Original sender detected from a forwarded email. Review it carefully before creating an automatic rule; forwarded headers are not sender authentication.' : 'Confirm this is the newsletter’s sender. Inline forwards can show your own address; forwarding as an attachment preserves the original sender.';
  node('previewBody').textContent = content.text;
  playlists(node('destination'));
  node('createRule').checked = !inbox.rules.some(rule => content.listId ? rule.list_id === content.listId : !rule.list_id && rule.sender === content.sender);
  node('routeSubmit').textContent = node('createRule').checked ? 'Save issue & rule' : 'Save this issue';
  node('newsletterPreview').hidden = false;
  node('previewHeading').focus();
  node('newsletterPreview').scrollIntoView({block:'start'});
}
node('sampleForm').onsubmit = event => {
  event.preventDefault(); action(async () => {
    const file = node('sampleFile').files[0];
    if (!file || !file.name.toLowerCase().endsWith('.eml')) throw new Error('Choose an .eml email file.');
    if (file.size > 2 * 1024 * 1024) throw new Error('Choose an email file smaller than 2 MB.');
    const accepted = await newsletterApi('/sample', {method:'POST', headers:{'Content-Type':'message/rfc822'}, body:file});
    await refresh();
    const delivery = inbox.deliveries.find(row => row.id === accepted.id);
    if (delivery && ['needs_routing','paused','possible_duplicate'].includes(delivery.state)) await preview(delivery);
    announce(delivery?.state === 'failed' ? 'The sample could not be read. Check the delivery error below.' : 'Sample received. Preview it before saving.');
  });
};
node('routeForm').onsubmit = event => {
  event.preventDefault(); action(async () => {
    if (!selected || !node('destination').value) throw new Error('Choose a playlist.');
    await newsletterApi('/deliveries/' + selected.id + '/route', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({playlistId:node('destination').value, createRule:node('createRule').checked})});
    node('newsletterPreview').hidden = true; selected = null;
    announce('Issue saved. It is ready in To read and the selected playlist.');
  });
};
node('createRule').onchange = () => node('routeSubmit').textContent = node('createRule').checked ? 'Save issue & rule' : 'Save this issue';
node('closePreview').onclick = () => {node('newsletterPreview').hidden = true; selected = null; node('newsletterRefresh').focus();};
node('newsletterRefresh').onclick = () => action(async () => { announce('Checking deliveries…'); return 'Newsletter settings are up to date.'; });
refresh().then(() => announce('Newsletter settings are up to date.')).catch(error => announce(error.message, true));
window.addEventListener('storage', event => {
  if (event.key === 'yomu-key' && !event.newValue) {
    inbox = null; selected = null;
    node('newsletterRules').replaceChildren(); node('newsletterDeliveries').replaceChildren();
    node('previewBody').replaceChildren(); node('newsletterPreview').hidden = true;
    node('receivingAddress').hidden = true;
    announce('Browser disconnected. Reconnect from the library to view newsletters.');
  }
});
setInterval(() => {if (!document.hidden && !busy && node('newsletterPreview').hidden) refresh().catch(error => announce(error.message, true));}, 15000);
