function yomuThemePreference() {
  const value = localStorage.getItem('yomu.theme') || 'system';
  return ['light','dark','system'].includes(value) ? value : 'system';
}
function updateYomuThemeControls() {
  const light = document.documentElement.dataset.theme === 'light';
  const toggle = document.getElementById('themeToggle');
  if (toggle) {
    toggle.setAttribute('aria-label', 'Switch to ' + (light ? 'dark' : 'light') + ' mode. Current theme: ' + (light ? 'Light' : 'Dark') + '.');
    toggle.title = 'Switch to ' + (light ? 'dark' : 'light') + ' mode';
    document.getElementById('themeAction').textContent = light ? 'Dark mode' : 'Light mode';
  }
  const selector = document.getElementById('siteTheme');
  if (selector) selector.value = yomuThemePreference();
}
function applyYomuTheme() {
  const value = yomuThemePreference();
  const light = value === 'light' || (value === 'system' && matchMedia('(prefers-color-scheme:light)').matches);
  document.documentElement.dataset.theme = light ? 'light' : 'dark';
  updateYomuThemeControls();
}
function setYomuTheme(value) {
  if (!['light','dark','system'].includes(value)) return;
  localStorage.setItem('yomu.theme', value);
  applyYomuTheme();
}
applyYomuTheme();
matchMedia('(prefers-color-scheme:light)').addEventListener('change', applyYomuTheme);
document.addEventListener('DOMContentLoaded', updateYomuThemeControls);
window.addEventListener('storage', event => { if (event.key === 'yomu.theme') applyYomuTheme(); });
