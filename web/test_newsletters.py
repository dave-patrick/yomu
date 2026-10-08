import base64
import json
import os
import time
import unittest
import uuid
from unittest.mock import patch

import test_sync  # Reuses its isolated temporary database, never production data.
import app as backend
from newsletters import NewsletterService, extract, from_eml


class NewsletterTests(unittest.TestCase):
    def setUp(self):
        self.client = test_sync.TestClient(backend.app)
        self.client.headers['Authorization'] = 'Bearer ' + os.environ['YOMU_ACCESS_TOKEN']
        self.service = backend.newsletter_service
        with backend.connect() as db:
            db.execute('DELETE FROM newsletter_deliveries')
            db.execute('DELETE FROM newsletter_rules')
        self.playlist_id = str(uuid.uuid4())
        self.client.post('/api/sync', json={'changes':[{'kind':'playlists','id':self.playlist_id,'base':None,
            'data':dict(id=self.playlist_id,name=self.playlist_id,createdAt=1,sortOrder=0)}]})

    def payload(self, ident=None):
        return dict(title='Daily edition ' + str(uuid.uuid4()), sender='News <news@example.test>',
                    listId='<daily.example.test>', messageId=ident or '<' + str(uuid.uuid4()) + '>',
                    html='<h1>Useful reading</h1><p>A complete newsletter sentence.</p><script>do not keep me</script><img src="https://tracker.test/pixel"><a href="https://example.test/story">Read more</a>',text='fallback')

    def accept(self, payload=None):
        accepted = self.service.accept(payload or self.payload(), str(uuid.uuid4()))
        self.service.process()
        return accepted['id']

    def route(self, ident, rule=True, playlist=None):
        return self.client.post('/api/newsletters/deliveries/' + ident + '/route',
            json={'playlistId':playlist or self.playlist_id,'createRule':rule})

    def record(self, ident):
        with backend.connect() as db:
            return dict(db.execute('SELECT * FROM newsletter_deliveries WHERE id=?', (ident,)).fetchone())

    def test_preview_and_rule_then_automatic_ready_item(self):
        ident = self.accept()
        preview = self.client.get('/api/newsletters/deliveries/' + ident).json()
        self.assertIn('https://example.test/story',preview['text'])
        self.assertNotIn('do not keep me',preview['text'])
        self.assertNotIn('tracker.test',preview['text'])
        self.assertEqual(self.route(ident).status_code,200)
        time.sleep(.003)
        next_id = self.accept()
        delivery = self.record(next_id)
        self.assertEqual(delivery['state'],'imported')
        records = self.client.get('/api/state').json()['records']
        item = next(row['data'] for row in records if row['id']==delivery['item_id'])
        self.assertEqual(item['importState'],'ready')
        self.assertEqual(item['sourceType'],'newsletter')
        self.assertTrue(item['inToRead'])
        self.assertTrue(any(row['kind']=='playlist_memberships' and row['data'] and row['data']['itemId']==item['id'] for row in records))
        self.assertFalse(any(row['kind'] in ('up_next','reading_progress') and row['id']==item['id'] for row in records))

    def test_provider_replay_and_message_identity_preserve_deleted_item(self):
        payload = self.payload()
        first = self.service.accept(payload,'event-one')
        self.assertEqual(self.service.accept(payload,'event-one'),first)
        self.service.process()
        item_id = self.route(first['id']).json()['itemId']
        with backend.connect() as db:
            backend.write(db,'reading_items',item_id,None)
        second = self.accept(payload)
        self.assertEqual(self.record(second)['state'],'duplicate')
        self.assertEqual(self.route(second).status_code,409)

    def test_same_subject_different_content_is_not_collapsed(self):
        first = self.payload(); first['title']='Daily news'
        ident = self.accept(first); self.route(ident)
        second = self.payload(); second['title']='Daily news'; second['html']='<p>A different edition.</p>'
        time.sleep(.003)
        self.assertEqual(self.record(self.accept(second))['state'],'imported')

    def test_similar_content_requires_explicit_decision(self):
        first = self.payload(); ident = self.accept(first); self.route(ident)
        second = dict(first,messageId='<another-id>')
        ident = self.accept(second)
        self.assertEqual(self.record(ident)['state'],'possible_duplicate')
        self.assertEqual(self.route(ident,False).status_code,200)

    def test_pause_and_deleted_destination(self):
        ident = self.accept(); self.route(ident)
        rule = self.client.get('/api/newsletters').json()['rules'][0]
        self.client.put('/api/newsletters/rules/' + rule['id'],json={'playlistId':self.playlist_id,'enabled':False})
        self.assertEqual(self.record(self.accept())['state'],'paused')
        self.client.put('/api/newsletters/rules/' + rule['id'],json={'playlistId':self.playlist_id,'enabled':True})
        with backend.connect() as db:
            backend.write(db,'playlists',self.playlist_id,None)
        time.sleep(.003)
        ident = self.accept()
        self.assertEqual(self.record(ident)['state'],'needs_routing')
        self.assertIn('removed',self.record(ident)['error'])
        self.assertFalse(self.client.get('/api/newsletters').json()['rules'][0]['enabled'])

    def test_rule_conflict_rolls_back_entire_manual_import(self):
        self.route(self.accept())
        ident = self.accept(dict(self.payload(),listId=''))
        # Exact existing identity with a pending sample produced before rule activation.
        with backend.connect() as db:
            preview = extract(self.payload())
            db.execute("UPDATE newsletter_deliveries SET preview=?,state='needs_routing' WHERE id=?",(json.dumps(preview),ident))
        response = self.route(ident)
        self.assertEqual(response.status_code,409)
        self.assertIsNone(self.record(ident)['item_id'])

    def test_interrupted_write_rolls_back_and_recovers_after_restart(self):
        self.route(self.accept())
        time.sleep(.003)
        ident = self.service.accept(self.payload(),'restart-event')['id']
        original = self.service.write
        def fail_membership(db,kind,key,payload):
            if kind=='playlist_memberships': raise RuntimeError('simulated failure')
            original(db,kind,key,payload)
        self.service.write=fail_membership
        try: self.service.process()
        finally: self.service.write=original
        row=self.record(ident)
        self.assertEqual(row['state'],'queued')
        self.assertIsNotNone(row['payload'])
        self.assertIsNone(row['item_id'])
        with backend.connect() as db: db.execute('UPDATE newsletter_deliveries SET next_attempt=0 WHERE id=?',(ident,))
        restarted=NewsletterService(backend.connect,backend.write,backend.LOCK)
        restarted.process()
        self.assertEqual(self.record(ident)['state'],'imported')

    def test_eml_plain_and_attached_forward_preserve_original_sender(self):
        raw=b'From: news@example.test\r\nSubject: Newsletter\r\nMessage-ID: <plain>\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nReadable body.'
        self.assertEqual(extract(from_eml(raw))['text'],'Readable body.')
        forwarded=b'From: owner@example.test\r\nContent-Type: multipart/mixed; boundary=X\r\n\r\n--X\r\nContent-Type: message/rfc822\r\n\r\n'+raw+b'\r\n--X--\r\n'
        parsed=extract(from_eml(forwarded))
        self.assertEqual(parsed['sender'],'news@example.test')
        self.assertTrue(parsed['forwarded'])

    def test_auth_size_and_empty_email(self):
        self.assertEqual(test_sync.TestClient(backend.app).get('/api/newsletters').status_code,401)
        self.assertEqual(self.client.post('/api/newsletters/sample',content=b'x'*(2*1024*1024+1)).status_code,413)
        ident=self.accept(dict(self.payload(),html='',text=''))
        self.assertEqual(self.record(ident)['state'],'failed')

    def test_inline_forward_identity_is_advisory_and_list_id_is_normalized(self):
        forwarded = dict(sender='owner@example.test',title='Fwd: Daily',html='',
                         text='---------- Forwarded message ---------\nFrom: News <news@example.test>\nSubject: Daily\nTo: owner@example.test\n\nActual issue.',messageId='<forward-wrapper>')
        preview = extract(forwarded)
        self.assertEqual(preview['sender'],'news@example.test')
        self.assertEqual(preview['messageId'],'')
        self.assertTrue(preview['forwarded'])
        self.assertEqual(preview['text'],'Actual issue.')
        self.assertEqual(extract(self.payload())['listId'],'daily.example.test')

    def test_webhook_uses_separate_credentials_and_durable_acceptance(self):
        secret='fixture-secret-'+'s'*40
        with patch.dict(os.environ,{'YOMU_NEWSLETTER_WEBHOOK_SECRET':secret}):
            payload=dict(MessageID='event-id',From='news@example.test',Subject='Fresh issue',TextBody='Ready to read.',Headers=[])
            self.assertEqual(self.client.post('/hooks/newsletters/postmark',json=payload).status_code,401)
            client=test_sync.TestClient(backend.app)
            headers={'Authorization':'Basic '+base64.b64encode(('yomu:'+secret).encode()).decode()}
            response=client.post('/hooks/newsletters/postmark',json=payload,headers=headers)
            self.assertEqual(response.status_code,200)
            self.assertEqual(self.record(response.json()['id'])['state'],'queued')
            self.assertEqual(client.post('/hooks/newsletters/postmark',json=payload,headers=headers).json(),response.json())

    def test_preview_retention_and_dismissal_preserve_identity_ledger(self):
        ident=self.accept()
        self.assertEqual(self.client.delete('/api/newsletters/deliveries/'+ident).status_code,200)
        self.assertIsNone(self.record(ident)['preview'])
        expired=self.accept()
        with backend.connect() as db: db.execute('UPDATE newsletter_deliveries SET received_at=0 WHERE id=?',(expired,))
        self.service.process()
        self.assertEqual(self.record(expired)['state'],'expired')
        self.assertIsNone(self.record(expired)['payload'])


if __name__=='__main__': unittest.main()
