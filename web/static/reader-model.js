(function(root) {
  'use strict';
  const defaults = {font:'serif', size:20, spacing:1.7, width:'comfortable'};
  const fonts = {serif:'Georgia, "Times New Roman", serif', sans:'"Segoe UI", sans-serif', mono:'Consolas, monospace'};
  const widths = {narrow:'34rem', comfortable:'44rem', wide:'56rem'};
  function preferences(raw) {
    const value = raw && typeof raw === 'object' ? raw : {};
    return {
      font:Object.hasOwn(fonts,value.font) ? value.font : defaults.font,
      size:[16,18,20,22,24,28,32].includes(value.size) ? value.size : defaults.size,
      spacing:[1.4,1.7,2].includes(value.spacing) ? value.spacing : defaults.spacing,
      width:Object.hasOwn(widths,value.width) ? value.width : defaults.width
    };
  }
  function sourceUrl(value) {
    try { const url = new URL(value); return ['http:','https:'].includes(url.protocol) && !url.username && !url.password ? url.href : null; }
    catch { return null; }
  }
  function libraryUrl(value) {
    try { const url = new URL(value,'https://yomu.invalid'); return url.origin === 'https://yomu.invalid' && ['/', '/index.html'].includes(url.pathname) ? url.pathname + url.search : '/'; }
    catch { return '/'; }
  }
  function paragraphs(text) { return String(text || '').trim().split(/\n\s*\n|\r?\n/).filter(part => part.trim()); }
  const api = {defaults, fonts, widths, preferences, sourceUrl, libraryUrl, paragraphs};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.YomuReader = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
