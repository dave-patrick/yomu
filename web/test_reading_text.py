import unittest

from newsletters import clean_text, extract


class ReadingTextTests(unittest.TestCase):
    def test_long_newsletter_tracking_url_and_wrapped_variant(self):
        token = 'NDEKvrcAp36_oNtoPadwALD92hsUMpM' * 30
        expected = 'Maria G\n\nThe article begins here.'
        self.assertEqual(expected, clean_text('Maria G (https://link.mail.beehiiv.com/ss/c/u001.' + token + ')\n\nThe article begins here.'))
        self.assertEqual(expected, clean_text('Maria G (https://link.mail.beehiiv.com/ss/c/u001.\n\nNDEKvrcAp36_oNtoPadwALD92hsUMpM\nVhONPx5PZv603_qnn4BpGj4lZvVyicKekX)\n\nThe article begins here.'))

    def test_url_does_not_consume_following_prose(self):
        self.assertEqual('Visit.\n\nKeep this paragraph.', clean_text('Visit https://example.test/path.\n\nKeep this paragraph.'))
    def test_links_symbols_and_unicode(self):
        self.assertEqual("Read this and café 日本語.\n\nDon't lose 42!", clean_text(
            "Read [this](https://example.test/a) and café 日本語.\n\nDon't ★ 😀 lose 42!"))

    def test_urls_and_empty_wrappers(self):
        self.assertEqual('Visit. Next chapter.', clean_text(
            'Visit https://example.test/a?q=1. Next chapter. (www.example.test)'))
        self.assertEqual('', clean_text('https://example.test ★ 😀'))
        self.assertEqual('', clean_text('--- *** !!!'))

    def test_html_keeps_link_label_without_appending_destination(self):
        result = extract(dict(sender='news@example.test', html=
            '<p>Read <a href="https://example.test/a">the story</a> today ★.</p>'))
        self.assertEqual('Read the story today.', result['text'])

    def test_idempotent(self):
        text = "Café, don't stop—read 3.14!"
        self.assertEqual(text, clean_text(clean_text(text)))
