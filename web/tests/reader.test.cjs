const test = require('node:test'), assert = require('node:assert/strict');
const reader = require('../static/reader-model.js');
test('reading preferences reject corrupted or unsupported persisted values', () => {
  assert.deepEqual(reader.preferences({font:'bad',size:999,spacing:0,width:'huge'}), reader.defaults);
  assert.deepEqual(reader.preferences({font:'sans',size:28,spacing:2,width:'wide'}), {font:'sans',size:28,spacing:2,width:'wide'});
  assert.deepEqual(reader.preferences(null), reader.defaults);
});
test('source links reject executable URLs and embedded credentials', () => {
  assert.equal(reader.sourceUrl('javascript:alert(1)'), null);
  assert.equal(reader.sourceUrl('https://user:password@example.com'), null);
  assert.equal(reader.sourceUrl('https://example.com/article'), 'https://example.com/article');
});
test('return navigation stays within the library and preserves filters', () => {
  assert.equal(reader.libraryUrl('/?view=queue&search=hello'), '/?view=queue&search=hello');
  for (const url of ['//evil.example/', 'https://evil.example/', '/settings.html']) assert.equal(reader.libraryUrl(url), '/');
});
test('paragraphs keep text as content, including markup and long lines', () => {
  assert.deepEqual(reader.paragraphs('First paragraph.\n\n<script>test</script>\nLast line.'), ['First paragraph.','<script>test</script>','Last line.']);
  assert.deepEqual(reader.paragraphs('  '), []);
});
