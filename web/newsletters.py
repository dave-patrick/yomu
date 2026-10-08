"""Private newsletter ingestion. Provider adapters never mutate mailboxes."""
import base64
import hashlib
import hmac
import json
import os
import re
import threading
import time
import uuid
from email import policy
from email.parser import BytesParser
from email.utils import parseaddr
from html.parser import HTMLParser
from urllib.parse import urlsplit

from fastapi import APIRouter, HTTPException, Request
import agentmail_receiver

MAX_EMAIL = 2 * 1024 * 1024


class ReadableHTML(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts, self.blocked, self.links = [], [], []

    def handle_starttag(self, tag, attrs):
        if self.blocked or tag in ('script', 'style', 'form', 'head', 'iframe', 'object', 'svg'):
            self.blocked.append(tag)
            return
        if tag in ('p', 'div', 'br', 'li', 'tr', 'h1', 'h2', 'h3', 'blockquote'):
            self.parts.append('\n')
        if tag == 'a':
            href = dict(attrs).get('href', '')
            self.links.append(href if urlsplit(href).scheme in ('https', 'http') else '')

    def handle_endtag(self, tag):
        if self.blocked:
            if tag in self.blocked:
                self.blocked = self.blocked[:self.blocked.index(tag)]
            return
        if tag == 'a' and self.links:
            href = self.links.pop()
            if href:
                self.parts.append(' (' + href + ')')
        if tag in ('p', 'div', 'li', 'tr', 'h1', 'h2', 'h3', 'blockquote'):
            self.parts.append('\n')

    def handle_data(self, text):
        if not self.blocked:
            self.parts.append(text)


def clean_text(text):
    return '\n\n'.join(line for line in (re.sub(r'[ \t\r]+', ' ', line).strip() for line in text.split('\n')) if line)


def extract(payload):
    html = payload.get('html', '')
    parser = ReadableHTML()
    parser.feed(html)
    text = clean_text(''.join(parser.parts)) if html else ''
    text = text or clean_text(payload.get('text', ''))
    if not text:
        raise ValueError('No readable email body. Attachments are not imported yet.')
    if len(text.encode('utf-8')) > 500_000:
        raise ValueError('Email body is too long.')
    sender = parseaddr(payload.get('sender', ''))[1].lower()
    title = (payload.get('title') or 'Newsletter')[:300]
    forwarded = bool(payload.get('forwarded'))
    message_id = payload.get('messageId', '')[:500]
    # Inline forwarding identity is advisory; the owner must confirm it in preview.
    envelope = payload.get('text', '')[:4000] or text[:4000]
    if re.match(r'^\s*(fwd?|fw)\s*:', title, re.I) or 'forwarded message' in envelope.lower():
        original_from = re.search(r'(?im)^\s*From:\s*([^\n]+)', envelope)
        original_subject = re.search(r'(?im)^\s*Subject:\s*([^\n]+)', envelope)
        if original_from and original_subject:
            candidate = parseaddr(original_from[1])[1].lower()
            if '@' in candidate:
                sender, title, forwarded, message_id = candidate, original_subject[1].strip()[:300], True, ''
                source_text = payload.get('text', '') or text
                tail = source_text[original_subject.end():].splitlines()
                while tail and (not tail[0].strip() or re.match(r'^\s*(To|Cc|Bcc|Date|Reply-To):', tail[0], re.I)):
                    tail.pop(0)
                text = clean_text('\n'.join(tail)) or text
    if not sender or '@' not in sender or len(sender) > 254:
        raise ValueError('Email sender could not be identified.')
    list_id = payload.get('listId', '').strip().lower()[:300]
    match = re.search(r'<([^>]+)>', list_id)
    return dict(title=title, sender=sender, listId=match[1] if match else list_id, text=text,
                messageId=message_id, forwarded=forwarded)


def from_eml(raw):
    message = BytesParser(policy=policy.default).parsebytes(raw)
    forwarded = False
    # Attached forwards retain the original envelope and message identity.
    for index, part in enumerate(message.walk()):
        if index > 200:
            raise ValueError('Too many email parts.')
        if part.get_content_type() == 'message/rfc822':
            nested = part.get_payload()
            if isinstance(nested, list) and nested:
                message, forwarded = nested[0], True
                break
    plain = message.get_body(preferencelist=('plain',))
    html = message.get_body(preferencelist=('html',))
    return dict(title=str(message.get('Subject', 'Newsletter')), sender=str(message.get('From', '')),
                listId=str(message.get('List-ID', '')), messageId=str(message.get('Message-ID', '')),
                html=html.get_content() if html else '', text=plain.get_content() if plain else '', forwarded=forwarded)


async def bounded_body(request):
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > MAX_EMAIL:
            raise HTTPException(413, 'Emails must be 2 MB or smaller; attachments are not supported yet.')
    return bytes(body)


class NewsletterService:
    def __init__(self, connect, write, lock):
        self.connect, self.write, self.lock = connect, write, lock
        self.stop = threading.Event()
        self.thread = None
        with connect() as db:
            db.executescript('''
            CREATE TABLE IF NOT EXISTS newsletter_rules (id TEXT PRIMARY KEY, sender TEXT NOT NULL, list_id TEXT NOT NULL, playlist_id TEXT NOT NULL, enabled INTEGER NOT NULL, created_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS newsletter_deliveries (id TEXT PRIMARY KEY, event_key TEXT UNIQUE NOT NULL, issue_key TEXT UNIQUE, fingerprint TEXT, received_at INTEGER NOT NULL, state TEXT NOT NULL, payload TEXT, preview TEXT, item_id TEXT, error TEXT, attempts INTEGER NOT NULL DEFAULT 0, next_attempt INTEGER NOT NULL DEFAULT 0);
            ''')

    def start(self):
        if self.thread and self.thread.is_alive():
            return
        self.stop.clear()
        def run():
            while not self.stop.is_set():
                try:
                    self.process()
                except Exception:
                    # Never log private message content, headers, or credentials.
                    pass
                self.stop.wait(5)
        self.thread = threading.Thread(target=run, daemon=True, name='newsletter-imports')
        self.thread.start()

    def shutdown(self):
        self.stop.set()
        if self.thread:
            self.thread.join(timeout=6)

    def accept(self, payload, event_key):
        now = int(time.time() * 1000)
        with self.lock, self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            old = db.execute('SELECT id,state FROM newsletter_deliveries WHERE event_key=?', (event_key,)).fetchone()
            if old:
                return dict(old)
            if db.execute("SELECT count(*) FROM newsletter_deliveries WHERE state NOT IN ('imported','duplicate','ignored','expired')").fetchone()[0] >= 100:
                raise HTTPException(503, 'Newsletter inbox is full. Resolve pending deliveries first.')
            ident = str(uuid.uuid4())
            db.execute('INSERT INTO newsletter_deliveries (id,event_key,received_at,state,payload) VALUES (?,?,?,?,?)',
                       (ident, event_key, now, 'queued', json.dumps(payload)))
            return dict(id=ident, state='queued')

    def playlist(self, db, ident):
        row = db.execute("SELECT payload FROM records WHERE kind='playlists' AND id=? AND deleted=0", (ident,)).fetchone()
        if not row:
            raise HTTPException(409, 'Choose an existing playlist. The destination was removed.')
        return json.loads(row['payload'])

    def import_issue(self, db, delivery, preview, playlist_id):
        self.playlist(db, playlist_id)
        if delivery['item_id']:
            raise HTTPException(409, 'This delivery has already been imported.')
        now, item_id = int(time.time() * 1000), str(uuid.uuid4())
        text = preview['text']
        item = dict(id=item_id, title=preview['title'], sourceType='newsletter', sourceName=preview['sender'],
                    sourceUrl=None, normalizedText=text, importState='ready', warning=None, inToRead=True,
                    importedAt=delivery['received_at'], updatedAt=now, estimatedSecondsAtOneX=max(1, len(text.split()) * 60 // 180),
                    contentVersion=1, manualOrder=now, archived=False, tags='')
        self.write(db, 'reading_items', item_id, item)
        orders = [json.loads(row[0]).get('sortOrder', 0) for row in db.execute("SELECT payload FROM records WHERE kind='playlist_memberships' AND deleted=0") if json.loads(row[0]).get('playlistId') == playlist_id]
        membership = dict(playlistId=playlist_id, itemId=item_id, sortOrder=max(orders, default=-1) + 1, addedAt=now)
        self.write(db, 'playlist_memberships', playlist_id + ':' + item_id, membership)
        db.execute("UPDATE newsletter_deliveries SET state='imported',item_id=?,payload=NULL,error=NULL WHERE id=?", (item_id, delivery['id']))
        return item_id

    def process(self):
        now = int(time.time() * 1000)
        with self.lock, self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            db.execute("UPDATE newsletter_deliveries SET payload=NULL,preview=NULL,issue_key=NULL,fingerprint=NULL,state='expired',error='Preview expired. Forward the issue again.' WHERE item_id IS NULL AND received_at < ? AND state NOT IN ('expired','ignored','duplicate')", (now - 7 * 86400000,))
            # Completed previews need not retain a second private copy of the body.
            for old in db.execute("SELECT id,preview FROM newsletter_deliveries WHERE item_id IS NOT NULL AND preview IS NOT NULL AND received_at < ?", (now - 7 * 86400000,)):
                metadata = json.loads(old['preview'])
                if metadata.get('text'):
                    metadata['text'] = ''
                    db.execute('UPDATE newsletter_deliveries SET preview=? WHERE id=?', (json.dumps(metadata), old['id']))
            jobs = list(db.execute("SELECT * FROM newsletter_deliveries WHERE state='queued' AND next_attempt<=? ORDER BY received_at LIMIT 20", (now,)))
            for job in jobs:
                db.execute('SAVEPOINT newsletter_job')
                try:
                    preview = extract(json.loads(job['payload']))
                    fingerprint = hashlib.sha256((preview['sender'] + '\n' + preview['title'] + '\n' + preview['text']).encode()).hexdigest()
                    issue_key = hashlib.sha256((preview['sender'] + '\n' + preview['messageId']).encode()).hexdigest() if preview['messageId'] else None
                    duplicate = db.execute('SELECT id FROM newsletter_deliveries WHERE issue_key=? AND id!=?', (issue_key, job['id'])).fetchone() if issue_key else None
                    possible = db.execute('SELECT id FROM newsletter_deliveries WHERE fingerprint=? AND id!=?', (fingerprint, job['id'])).fetchone()
                    state = 'duplicate' if duplicate else 'possible_duplicate' if possible else 'needs_routing'
                    db.execute('UPDATE newsletter_deliveries SET preview=?,payload=NULL,fingerprint=?,issue_key=?,state=?,error=NULL WHERE id=?',
                               (json.dumps(preview), fingerprint, None if duplicate else issue_key, state, job['id']))
                    if state != 'needs_routing':
                        db.execute('RELEASE newsletter_job')
                        continue
                    rules = list(db.execute('SELECT * FROM newsletter_rules WHERE (list_id != ? AND list_id=?) OR (list_id=? AND sender=?)',
                                            ('', preview['listId'], '', preview['sender'])))
                    if len(rules) != 1:
                        db.execute('RELEASE newsletter_job')
                        continue
                    rule = rules[0]
                    if not rule['enabled'] or job['received_at'] < rule['created_at']:
                        db.execute("UPDATE newsletter_deliveries SET state='paused' WHERE id=?", (job['id'],))
                        db.execute('RELEASE newsletter_job')
                        continue
                    try:
                        self.playlist(db, rule['playlist_id'])
                    except HTTPException:
                        db.execute('UPDATE newsletter_rules SET enabled=0 WHERE id=?', (rule['id'],))
                        db.execute("UPDATE newsletter_deliveries SET error='Destination playlist was removed. Choose another playlist.' WHERE id=?", (job['id'],))
                        db.execute('RELEASE newsletter_job')
                        continue
                    self.import_issue(db, job, preview, rule['playlist_id'])
                    db.execute('RELEASE newsletter_job')
                except ValueError as error:
                    db.execute('ROLLBACK TO newsletter_job')
                    db.execute('RELEASE newsletter_job')
                    # Only our bounded, content-free extraction errors are displayed.
                    db.execute("UPDATE newsletter_deliveries SET state='failed',error=?,attempts=attempts+1 WHERE id=?", (str(error)[:180], job['id']))
                except Exception:
                    db.execute('ROLLBACK TO newsletter_job')
                    db.execute('RELEASE newsletter_job')
                    attempts = job['attempts'] + 1
                    db.execute('UPDATE newsletter_deliveries SET state=?,error=?,attempts=?,next_attempt=? WHERE id=?',
                               ('failed' if attempts >= 3 else 'queued', 'Import interrupted. Retry this delivery.', attempts, now + 30000 * attempts, job['id']))

    def snapshot(self):
        with self.lock, self.connect() as db:
            rules = [dict(row) for row in db.execute('SELECT * FROM newsletter_rules ORDER BY created_at DESC')]
            deliveries = []
            for row in db.execute("SELECT * FROM newsletter_deliveries ORDER BY CASE WHEN state IN ('needs_routing','paused','possible_duplicate','failed') THEN 0 ELSE 1 END, received_at DESC LIMIT 100"):
                preview = json.loads(row['preview']) if row['preview'] else {}
                deliveries.append(dict(id=row['id'], state=row['state'], receivedAt=row['received_at'], error=row['error'],
                                       itemId=row['item_id'], title=preview.get('title', 'Newsletter delivery'),
                                       sender=preview.get('sender', ''), listId=preview.get('listId', ''), forwarded=preview.get('forwarded', False)))
            playlists = [json.loads(row[0]) for row in db.execute("SELECT payload FROM records WHERE kind='playlists' AND deleted=0")]
            return dict(rules=rules, deliveries=deliveries, playlists=playlists,
                        receivingAddress=os.environ.get('YOMU_NEWSLETTER_ADDRESS', ''),
                        receivingEnabled=agentmail_receiver.configured() or len(os.environ.get('YOMU_NEWSLETTER_WEBHOOK_SECRET', '')) >= 32,
                        receivingProvider='AgentMail' if agentmail_receiver.configured() else 'Postmark' if len(os.environ.get('YOMU_NEWSLETTER_WEBHOOK_SECRET', '')) >= 32 else None)


def create_router(service):
    router = APIRouter()

    @router.get('/api/newsletters')
    def status():
        return service.snapshot()

    @router.post('/api/newsletters/sample')
    async def sample(request: Request):
        raw = await bounded_body(request)
        try:
            payload = from_eml(raw)
        except Exception:
            raise HTTPException(400, 'Could not read this email. Upload an .eml email file.')
        result = service.accept(payload, 'sample:' + hashlib.sha256(raw).hexdigest())
        service.process()
        return result

    @router.get('/api/newsletters/deliveries/{ident}')
    def preview(ident: str):
        with service.lock, service.connect() as db:
            row = db.execute('SELECT preview FROM newsletter_deliveries WHERE id=?', (ident,)).fetchone()
            if not row or not row['preview']:
                raise HTTPException(404, 'Preview unavailable. It may have expired or failed to import.')
            return json.loads(row['preview'])

    @router.post('/api/newsletters/deliveries/{ident}/route')
    async def route(ident: str, request: Request):
        try:
            body = json.loads(await bounded_body(request))
            playlist_id = body['playlistId']
            assert isinstance(playlist_id, str) and type(body.get('createRule', False)) is bool
        except (ValueError, KeyError, TypeError, AssertionError):
            raise HTTPException(400, 'Choose a playlist.')
        with service.lock, service.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT * FROM newsletter_deliveries WHERE id=?', (ident,)).fetchone()
            if not row or row['state'] not in ('needs_routing', 'paused', 'possible_duplicate') or not row['preview']:
                raise HTTPException(409, 'This delivery cannot be routed again.')
            preview = json.loads(row['preview'])
            item_id = service.import_issue(db, row, preview, playlist_id)
            if body.get('createRule'):
                matches = db.execute('SELECT id FROM newsletter_rules WHERE list_id=? AND (list_id!=? OR sender=?)',
                                     (preview['listId'], '', preview['sender'])).fetchone()
                if matches:
                    raise HTTPException(409, 'A rule already exists for this newsletter. Edit that rule instead.')
                db.execute('INSERT INTO newsletter_rules VALUES (?,?,?,?,?,?)',
                           (str(uuid.uuid4()), preview['sender'], preview['listId'], playlist_id, 1, int(time.time() * 1000)))
            return dict(itemId=item_id)

    @router.put('/api/newsletters/rules/{ident}')
    async def edit(ident: str, request: Request):
        try:
            body = json.loads(await bounded_body(request))
            assert type(body['enabled']) is bool and isinstance(body['playlistId'], str)
        except (ValueError, KeyError, TypeError, AssertionError):
            raise HTTPException(400, 'Choose a playlist and enabled state.')
        with service.lock, service.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            if not db.execute('SELECT id FROM newsletter_rules WHERE id=?', (ident,)).fetchone():
                raise HTTPException(404, 'Newsletter rule no longer exists.')
            service.playlist(db, body['playlistId'])
            db.execute('UPDATE newsletter_rules SET playlist_id=?,enabled=?,created_at=? WHERE id=?',
                       (body['playlistId'], int(body['enabled']), int(time.time() * 1000), ident))
        return dict(status='saved')

    @router.post('/api/newsletters/deliveries/{ident}/retry')
    def retry(ident: str):
        with service.lock, service.connect() as db:
            row = db.execute('SELECT payload,state FROM newsletter_deliveries WHERE id=?', (ident,)).fetchone()
            if not row or row['state'] != 'failed' or not row['payload']:
                raise HTTPException(409, 'This delivery cannot be retried. Forward it again or choose a playlist.')
            db.execute("UPDATE newsletter_deliveries SET state='queued',attempts=0,next_attempt=0,error=NULL WHERE id=?", (ident,))
        service.process()
        return dict(status='retried')

    @router.delete('/api/newsletters/deliveries/{ident}')
    def dismiss(ident: str):
        with service.lock, service.connect() as db:
            result = db.execute("UPDATE newsletter_deliveries SET state='ignored',payload=NULL,preview=NULL,error=NULL WHERE id=? AND item_id IS NULL", (ident,))
            if not result.rowcount:
                raise HTTPException(409, 'Already imported. Remove that issue from the library instead.')
        return dict(status='ignored')

    @router.post('/hooks/newsletters/agentmail')
    async def receive_agentmail(request: Request):
        return await agentmail_receiver.receive(service, await bounded_body(request), request.headers)

    @router.post('/hooks/newsletters/postmark')
    async def receive(request: Request):
        secret = os.environ.get('YOMU_NEWSLETTER_WEBHOOK_SECRET', '')
        if len(secret) < 32:
            raise HTTPException(503, 'Newsletter receiving is not configured.')
        expected = 'Basic ' + base64.b64encode(('yomu:' + secret).encode()).decode()
        if not hmac.compare_digest(request.headers.get('authorization', '').encode(), expected.encode()):
            raise HTTPException(401, 'Invalid newsletter webhook credentials.')
        try:
            data = json.loads(await bounded_body(request))
            event = data['MessageID']
            assert isinstance(event, str) and 1 <= len(event) <= 500
            headers = {header['Name'].lower(): header['Value'] for header in data.get('Headers', [])}
            payload = dict(title=data.get('Subject', ''), sender=data.get('From', ''), text=data.get('TextBody', ''),
                           html=data.get('HtmlBody', ''), listId=headers.get('list-id', ''), messageId=headers.get('message-id', ''))
            assert all(isinstance(value, str) for value in payload.values())
            for attachment in data.get('Attachments', []):
                if attachment.get('ContentType', '').split(';')[0] == 'message/rfc822':
                    payload = from_eml(base64.b64decode(attachment['Content'], validate=True))
                    payload['forwarded'] = True
                    break
        except (ValueError, KeyError, TypeError, AssertionError, AttributeError):
            raise HTTPException(400, 'Invalid inbound email payload.')
        # Acknowledge only after SQLite has durably accepted the event. Worker handles restart/retry.
        return service.accept(payload, 'postmark:' + event)

    return router
