const theme = document.getElementById('siteTheme');
theme.value = yomuThemePreference();
theme.onchange = () => setYomuTheme(theme.value);
function showBrowserConnection() {
  const paired = !!localStorage.getItem('yomu-key');
  const last = Number(localStorage.getItem('yomu.lastCheck') || 0);
  document.getElementById('settingsConnection').textContent = paired ? 'A library key is saved in this browser.' : 'This browser is not connected.';
  document.getElementById('settingsPair').textContent = paired ? 'Reconnect or check the access key' : 'Connect this browser';
  document.getElementById('settingsDisconnect').hidden = !paired;
  document.getElementById('settingsLastCheck').textContent = paired && last ?
    'Last successful library check: ' + new Intl.DateTimeFormat(undefined, {dateStyle:'medium',timeStyle:'short'}).format(last) :
    'Connect from the library to check your access key.';
}
document.getElementById('settingsDisconnect').onclick = () => {
  localStorage.removeItem('yomu-key'); localStorage.removeItem('yomu.lastCheck');
  showBrowserConnection();
  document.getElementById('settingsConnection').textContent = 'Browser disconnected. Your saved items stay on your phone and server.';
};
window.addEventListener('storage', event => {
  if (event.key === 'yomu-key' || event.key === 'yomu.lastCheck') showBrowserConnection();
});
showBrowserConnection();
