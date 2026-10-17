import unittest

from newsletters import clean_text, extract, readable_preview


class ReadingTextTests(unittest.TestCase):
    def test_legacy_soft_paragraphs_only_join_clear_continuations(self):
        old = 'On that first day, Berry walked in looking like every farmer I’d ever\n\nseen standing around a tobacco warehouse telling lies.\n\nHis hair was gray on the sides, and his face was tan except for a\n\ncircle of white on his forehead.\n\nA new heading\n\nA real paragraph.'
        repaired = readable_preview(dict(text=old))
        self.assertIn('ever seen standing', repaired)
        self.assertIn('except for a circle', repaired)
        self.assertIn('lies.\n\nHis hair', repaired)
        self.assertIn('A new heading\n\nA real paragraph.', repaired)
        self.assertEqual(old, readable_preview(dict(text=old, textVersion=2)))

    def test_wrapped_email_lines_preserve_paragraphs(self):
        self.assertEqual('On that first day, Berry walked in looking like every farmer I’d ever seen standing around.\n\nHis hair was gray.', clean_text('On that first day, Berry walked in looking like every farmer I’d ever\nseen standing around.\n\nHis hair was gray.'))

    def test_html_source_wraps_are_not_paragraphs(self):
        result = extract(dict(sender='news@example.test', html='<p>He wore khakis,\nbrown lace-up shoes and a blue shirt.</p><p>His hair was gray.</p>'))
        self.assertEqual('He wore khakis, brown lace-up shoes and a blue shirt.\n\nHis hair was gray.', result['text'])

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
