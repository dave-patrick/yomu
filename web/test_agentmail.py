import base64
import hashlib
import hmac
import json
import os
import time
import unittest
import uuid
from unittest.mock import AsyncMock, patch

import httpx
import asyncio
import test_sync
import app as backend
from fastapi import HTTPException
import test_newsletters


class AgentMailTests(unittest.TestCase):
    setUp = test_newsletters.NewsletterTests.setUp
    record = test_newsletters.NewsletterTests.record
    route = test_newsletters.NewsletterTests.route

    def setUp(self):
        test_newsletters.NewsletterTests.setUp(self)
        self.secret_bytes = b'fixture-signing-key-with-32-bytes!'
        self.env = patch.dict(os.environ, {'AGENTMAIL_API_KEY': 'fixture-api-key',
            'AGENTMAIL_WEBHOOK_SECRET': 'whsec_' + base64.b64encode(self.secret_bytes).decode(),
            'AGENTMAIL_INBOX_ID': 'reader@agentmail.to'})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.public = test_sync.TestClient(backend.app)

    def event(self, ident=None):
        return dict(event_type='message.received', event_id=ident or str(uuid.uuid4()),
                    message=dict(inbox_id='reader@agentmail.to',message_id='<'+str(uuid.uuid4())+'>',
                    subject='Newsletter', **{'from':'news@example.test'}, text='First sentence. Another sentence. Issue '+str(uuid.uuid4()),
                    headers={'List-ID':'<daily.example.test>'}))

    def send(self, event, timestamp=None, tamper=False):
        raw = json.dumps(event)
        timestamp = str(timestamp or int(time.time()))
        svix_id = 'msg_fixture'
        signed = (svix_id+'.'+timestamp+'.'+raw).encode()
        signature = base64.b64encode(hmac.new(self.secret_bytes,signed,hashlib.sha256).digest()).decode()
        return self.public.post('/hooks/newsletters/agentmail',content=raw + (' ' if tamper else ''),
            headers={'svix-id':svix_id,'svix-timestamp':timestamp,'svix-signature':'v1,'+signature})

    def test_signature_rejects_missing_tampered_and_expired_requests(self):
        self.assertEqual(self.public.post('/hooks/newsletters/agentmail',json=self.event()).status_code,401)
        self.assertEqual(self.send(self.event(),tamper=True).status_code,401)
        self.assertEqual(self.send(self.event(),timestamp=int(time.time())-1000).status_code,401)
        with backend.connect() as db:
            self.assertEqual(db.execute('SELECT count(*) FROM newsletter_deliveries').fetchone()[0],0)

    def test_durable_receipt_duplicate_retry_and_playlist_routing(self):
        event=self.event()
        response=self.send(event)
        self.assertEqual(response.status_code,200)
        ident=response.json()['id']
        self.assertEqual(self.record(ident)['state'],'queued')
        self.assertEqual(self.send(event).json(),response.json())
        self.service.process()
        self.assertEqual(self.route(ident).status_code,200)
        time.sleep(.003)
        second=self.send(self.event()).json()['id']
        self.service.process()
        self.assertEqual(self.record(second)['state'],'imported')
        self.assertEqual(self.client.get('/api/newsletters').json()['receivingProvider'],'AgentMail')

    def test_only_selected_inbox_and_authenticated_received_events(self):
        event=self.event();event['message']['inbox_id']='other@agentmail.to'
        self.assertEqual(self.send(event).status_code,403)
        for kind in ('message.received.spam','message.received.blocked','message.received.unauthenticated','message.sent'):
            event=self.event();event['event_type']=kind
            self.assertEqual(self.send(event).json(),{'status':'ignored'})

    def test_missing_body_fetches_full_message_and_retries_provider_failure(self):
        event=self.event();full=dict(event['message']);del event['message']['text']
        with patch('agentmail_receiver.fetch_message',new=AsyncMock(return_value=full)) as fetch:
            response=self.send(event)
            self.assertEqual(response.status_code,200)
            fetch.assert_awaited_once_with(full['inbox_id'],full['message_id'])
        self.service.process()
        self.assertEqual(self.record(response.json()['id'])['state'],'needs_routing')
        event=self.event();del event['message']['text']
        with patch('agentmail_receiver.fetch_message',new=AsyncMock(side_effect=HTTPException(503,'Temporary failure'))):
            self.assertEqual(self.send(event).status_code,503)
        with backend.connect() as db:
            self.assertIsNone(db.execute('SELECT id FROM newsletter_deliveries WHERE event_key=?',('agentmail:'+event['event_id'],)).fetchone())

    def test_disabled_or_invalid_fields_do_not_import(self):
        with patch.dict(os.environ,{'AGENTMAIL_API_KEY':''}):
            self.assertEqual(self.send(self.event()).status_code,503)
        event=self.event();event['message']['text']=123
        self.assertEqual(self.send(event).status_code,400)
        event=self.event();del event['event_id']
        self.assertEqual(self.send(event).status_code,400)


class AgentMailRetrievalTests(unittest.TestCase):
    def fetch(self, response):
        import agentmail_receiver
        real_client = httpx.AsyncClient
        def factory(**kwargs):
            return real_client(transport=httpx.MockTransport(lambda request: response(request)), **kwargs)
        with patch.dict(os.environ, {'AGENTMAIL_API_KEY':'fixture-token'}), patch('agentmail_receiver.httpx.AsyncClient',side_effect=factory):
            return asyncio.run(agentmail_receiver.fetch_message('reader@agentmail.to','<issue/test>'))

    def test_fixed_provider_api_authorization_and_encoded_message_path(self):
        def response(request):
            self.assertEqual(request.url.host,'api.agentmail.to')
            self.assertIn('%2F',request.url.raw_path.decode())
            self.assertEqual(request.headers['authorization'],'Bearer fixture-token')
            return httpx.Response(200,json={'inbox_id':'reader@agentmail.to','message_id':'<issue/test>','text':'Full body'})
        self.assertEqual(self.fetch(response)['text'],'Full body')

    def test_redirect_errors_and_identity_mismatch_are_not_acknowledged(self):
        for response in (lambda request:httpx.Response(302,headers={'location':'https://other.test/'}),
                         lambda request:httpx.Response(429),
                         lambda request:httpx.Response(200,json={'inbox_id':'wrong','message_id':'wrong'})):
            with self.assertRaises(HTTPException) as error: self.fetch(response)
            self.assertEqual(error.exception.status_code,503)

    def test_oversized_provider_response_is_bounded(self):
        with self.assertRaises(HTTPException) as error:
            self.fetch(lambda request:httpx.Response(200,content=b'x'*(2*1024*1024+1)))
        self.assertEqual(error.exception.status_code,413)


if __name__ == '__main__': unittest.main()
