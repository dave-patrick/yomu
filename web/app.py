"""Private single-owner library. SQLite + files require a persistent Render disk."""
import hmac
import json
import os
import secrets
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

ROOT = Path(__file__).parent
DATA = Path(os.environ.get('YOMU_DATA_DIR', './data')).resolve()
DATA.mkdir(parents=True, exist_ok=True)
TOKEN = os.environ.get('YOMU_ACCESS_TOKEN', '')
if len(TOKEN) < 32:
    raise RuntimeError('Set YOMU_ACCESS_TOKEN to a random secret of at least 32 characters.')
MAX_FILE = 20 * 1024 * 1024
TABLES = {
    'reading_items': {'id', 'title', 'sourceType', 'sourceUrl', 'sourceName', 'normalizedText', 'importState', 'warning', 'inToRead', 'importedAt', 'updatedAt', 'estimatedSecondsAtOneX', 'contentVersion', 'manualOrder', 'archived', 'fileId', 'tags'},
    'playlists': {'id', 'name', 'createdAt', 'sortOrder'},
    'playlist_memberships': {'playlistId', 'itemId', 'sortOrder', 'addedAt'},
    'up_next': {'itemId', 'position', 'addedAt'},
    'reading_progress': {'itemId', 'sentenceIndex', 'characterOffset', 'playbackSpeed', 'completed', 'lastListenedAt'},
    'read_history': {'id', 'itemId', 'completedAt'},
    'reading_cursors': {'itemId', 'anchor', 'updatedAt', 'deviceLabel'},
    'annotations': {'id', 'itemId', 'anchor', 'kind', 'note', 'createdAt'},
    'reader_defaults': {'id', 'font', 'size', 'spacing', 'schemaVersion'},
}
LOCK = threading.RLock()

@contextmanager
def connect():
    db = sqlite3.connect(DATA / 'library.db', timeout=30)
    db.execute('PRAGMA journal_mode=WAL')
    db.row_factory = sqlite3.Row
    try:
        with db:
            yield db
    finally:
        db.close()

with connect() as db:
    db.executescript('''
    CREATE TABLE IF NOT EXISTS records (kind TEXT, id TEXT, payload TEXT, deleted INTEGER NOT NULL DEFAULT 0, revision INTEGER NOT NULL, PRIMARY KEY(kind,id));
    CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT);
    INSERT OR IGNORE INTO metadata VALUES ('revision','0');
    INSERT OR IGNORE INTO metadata VALUES ('instance',lower(hex(randomblob(16))));
    CREATE TABLE IF NOT EXISTS files (id TEXT PRIMARY KEY, name TEXT, mime TEXT, size INTEGER);
    CREATE TABLE IF NOT EXISTS clients (id TEXT PRIMARY KEY, seen INTEGER);
    ''')

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)

@app.middleware('http')
async def access(request: Request, call_next):
    if request.url.path.startswith('/api/'):
        supplied = request.headers.get('authorization', '').removeprefix('Bearer ')
        if not hmac.compare_digest(supplied.encode(), TOKEN.encode()):
            return JSONResponse({'detail': 'Pair this device with your Yomu access key.'}, status_code=401)
    response = await call_next(request)
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['Referrer-Policy'] = 'no-referrer'
    response.headers['X-Frame-Options'] = 'DENY'
    response.headers['Content-Security-Policy'] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'"
    response.headers['Cache-Control'] = 'no-store'
    return response

def state(db):
    records = [{'kind': r['kind'], 'id': r['id'], 'data': json.loads(r['payload']) if not r['deleted'] else None, 'revision': r['revision']} for r in db.execute('SELECT * FROM records')]
    return {'instance': db.execute("SELECT value FROM metadata WHERE key='instance'").fetchone()[0], 'revision': int(db.execute("SELECT value FROM metadata WHERE key='revision'").fetchone()[0]), 'records': records}

def write(db, kind, ident, payload):
    if kind == 'playlists' and payload is not None:
        name = payload.get('name')
        if not isinstance(name, str) or not name.strip():
            raise HTTPException(400, 'Enter a playlist name.')
        for row in db.execute("SELECT id,payload FROM records WHERE kind='playlists' AND deleted=0 AND id != ?", (ident,)):
            if json.loads(row['payload']).get('name') == name:
                raise HTTPException(409, 'A playlist with that name already exists. Rename it before syncing.')
    revision = int(db.execute("SELECT value FROM metadata WHERE key='revision'").fetchone()[0]) + 1
    db.execute("UPDATE metadata SET value=? WHERE key='revision'", (str(revision),))
    db.execute('INSERT INTO records VALUES (?,?,?,?,?) ON CONFLICT(kind,id) DO UPDATE SET payload=excluded.payload, deleted=excluded.deleted, revision=excluded.revision', (kind, ident, json.dumps(payload, sort_keys=True), int(payload is None), revision))

def validate(kind, ident, data):
    if not isinstance(kind, str) or kind not in TABLES or not isinstance(ident, str) or not 1 <= len(ident) <= 180:
        raise HTTPException(400, 'Invalid library record.')
    if data is not None:
        if not isinstance(data, dict) or set(data) - TABLES[kind]:
            raise HTTPException(400, 'Unsupported library fields.')
        if len(json.dumps(data)) > 4 * 1024 * 1024:
            raise HTTPException(413, 'Reading item is too large.')
        expected = f"{data.get('playlistId')}:{data.get('itemId')}" if kind == 'playlist_memberships' else str(data.get('id') if kind in ('reading_items','playlists','read_history','reader_defaults','annotations') else data.get('itemId'))
        if expected != ident:
            raise HTTPException(400, 'Record identity does not match its content.')
        if kind == 'annotations' and (data.get('kind') not in ('bookmark','highlight','note','listening bookmark') or not isinstance(data.get('note'), str) or len(data['note']) > 10000 or not isinstance(data.get('anchor'), str) or len(data['anchor']) > 4096):
            raise HTTPException(400, 'Invalid annotation.')
        if kind == 'reading_items' and 'tags' in data and (not isinstance(data['tags'], str) or len(data['tags']) > 2000):
            raise HTTPException(400, 'Invalid tags.')
        if kind == 'reading_cursors':
            try:
                anchor = json.loads(data['anchor'])
                assert type(anchor['version']) is int and anchor['version'] == 1 and isinstance(anchor['offset'], int) and not isinstance(anchor['offset'], bool) and 0 <= anchor['offset'] <= 4_000_000
                assert isinstance(anchor['textHash'], str) and len(anchor['textHash']) == 64 and all(c in '0123456789abcdef' for c in anchor['textHash'])
                assert isinstance(anchor['quote'], str) and len(anchor['quote']) <= 80
                assert isinstance(anchor['prefix'], str) and len(anchor['prefix']) <= 32
                assert isinstance(data['updatedAt'], int) and not isinstance(data['updatedAt'], bool) and data['updatedAt'] >= 0
                assert isinstance(data['deviceLabel'], str) and len(data['deviceLabel']) <= 80
            except (ValueError, TypeError, KeyError, AssertionError):
                raise HTTPException(400, 'Invalid reading anchor.')
        if kind == 'reader_defaults' and (ident != 'global' or type(data.get('schemaVersion')) is not int or data.get('schemaVersion') != 1 or data.get('font') not in ('serif','sans','mono') or data.get('size') not in (16,18,20,22,24,28,32) or data.get('spacing') not in (1.4,1.7,2)):
            raise HTTPException(400, 'Unsupported reader defaults.')

@app.get('/health')
def health():
    with connect() as db:
        db.execute('SELECT 1')
    return {'status': 'ok'}

@app.get('/api/state')
def get_state():
    with LOCK, connect() as db:
        result = state(db)
        result['capabilities'] = ['mobile_v2']
        result['clients'] = [dict(r) for r in db.execute('SELECT * FROM clients ORDER BY seen DESC')]
        return result

@app.post('/api/sync')
async def sync(request: Request):
    raw = await request.body()
    if len(raw) > 32 * 1024 * 1024:
        raise HTTPException(413, 'Sync batch is too large.')
    try:
        body = json.loads(raw)
        changes = body['changes']
        assert isinstance(changes, list) and len(changes) <= 10000
    except (ValueError, KeyError, AssertionError, TypeError):
        raise HTTPException(400, 'Invalid sync request.')
    conflicts = []
    with LOCK, connect() as db:
        db.execute('BEGIN IMMEDIATE')
        instance = db.execute("SELECT value FROM metadata WHERE key='instance'").fetchone()[0]
        if body.get('instance') not in (None, '', instance):
            raise HTTPException(409, 'Server library changed. Re-pair before syncing.')
        for change in changes:
            if not isinstance(change, dict):
                raise HTTPException(400, 'Invalid library change.')
            kind, ident, new, base = change.get('kind'), change.get('id'), change.get('data'), change.get('base')
            validate(kind, ident, new)
            validate(kind, ident, base)
            row = db.execute('SELECT * FROM records WHERE kind=? AND id=?', (kind, ident)).fetchone()
            current = json.loads(row['payload']) if row and not row['deleted'] else None
            if current == new:
                continue  # Safe replay after an interrupted acknowledgement.
            if row and row['deleted'] and new is not None and (base is not None or kind in ('reading_items','playlists','read_history')):
                conflicts.append(f'{kind}/{ident}: deleted on another device')
                continue  # Tombstones prevent an offline phone resurrecting deleted items.
            if new is None:
                if current == base:
                    write(db, kind, ident, None)
                else:
                    conflicts.append(f'{kind}/{ident}: changed before deletion')
                continue
            if current is None:
                if base is None:
                    write(db, kind, ident, new)
                else:
                    conflicts.append(f'{kind}/{ident}: no longer exists')
                continue
            merged = dict(current)
            for field, value in new.items():
                before = (base or {}).get(field)
                if before == value:
                    continue
                if current.get(field) in (before, value):
                    merged[field] = value
                else:
                    conflicts.append(f'{kind}/{ident}/{field}')
            if merged != current:
                write(db, kind, ident, merged)
        # Remove dependent rows after their parent was deleted, including offline replays.
        for row in list(db.execute('SELECT * FROM records WHERE deleted=0')):
            data = json.loads(row['payload'])
            for parent_kind, parent_id in [('reading_items', data.get('itemId')), ('playlists', data.get('playlistId'))]:
                if not parent_id:
                    continue
                parent = db.execute('SELECT deleted FROM records WHERE kind=? AND id=?', (parent_kind,parent_id)).fetchone()
                if parent and parent['deleted']:
                    write(db, row['kind'], row['id'], None)
                    break
        client = str(body.get('client', 'browser'))[:100]
        db.execute('INSERT INTO clients VALUES (?,?) ON CONFLICT(id) DO UPDATE SET seen=excluded.seen', (client, int(time.time()*1000)))
        result = state(db)
        result['conflicts'] = conflicts
        return result

def article_url_key(url):
    """Compare article URLs without fragments or known tracking parameters."""
    from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode
    try:
        parsed = urlsplit(url.strip())
        host = (parsed.hostname or '').lower()
        if parsed.scheme.lower() not in ('http', 'https') or not host:
            return url
        if ':' in host:
            host = '[' + host + ']'
        port = parsed.port
        if port and (parsed.scheme.lower(), port) not in (('http', 80), ('https', 443)):
            host += ':' + str(port)
        # Keep content-bearing parameters and their order; remove only known trackers.
        query = [(key, value) for key, value in parse_qsl(parsed.query, keep_blank_values=True)
                 if not key.lower().startswith('utm_')
                 and key.lower() not in ('fbclid', 'gclid', 'dclid', 'msclkid', 'mc_cid', 'mc_eid')]
        return urlunsplit((parsed.scheme.lower(), host, parsed.path or '/', urlencode(query), ''))
    except (ValueError, AttributeError):
        return url


@app.post('/api/articles')
async def article(request: Request):
    body = await request.json()
    url = str(body.get('url', '')).strip()
    from urllib.parse import urlsplit
    parsed = urlsplit(url)
    if parsed.scheme not in ('http','https') or not parsed.hostname or len(url) > 8192:
        raise HTTPException(400, 'Enter a complete article URL.')
    now = int(time.time()*1000)
    with LOCK, connect() as db:
        for row in db.execute("SELECT id, payload FROM records WHERE kind='reading_items' AND deleted=0"):
            existing_url = json.loads(row['payload']).get('sourceUrl')
            if existing_url and article_url_key(existing_url) == article_url_key(url):
                return {'status': 'already_saved', 'id': row['id']}
        ident = str(uuid.uuid4())
        write(db, 'reading_items', ident, dict(id=ident, title=str(body.get('title') or url)[:1000], sourceType='url', sourceUrl=url, sourceName=parsed.hostname, normalizedText='', importState='pending', warning='Waiting for phone to extract the article.', inToRead=True, importedAt=now, updatedAt=now, estimatedSecondsAtOneX=0, contentVersion=1, manualOrder=now, archived=False))
    return {'status': 'saved', 'id': ident}

@app.put('/api/files/{ident}')
async def upload(ident: str, request: Request):
    try:
        uuid.UUID(ident)
    except ValueError:
        raise HTTPException(400, 'Invalid file identifier.')
    name = request.headers.get('x-file-name', 'document.txt')
    from urllib.parse import unquote
    name = Path(unquote(name).replace('\\','/')).name[:180]
    if Path(name).suffix.lower() not in ('.pdf','.docx','.txt'):
        raise HTTPException(400, 'Upload a PDF, DOCX, or text file.')
    path = DATA / ident
    temp = DATA / f'{ident}.{secrets.token_hex(8)}.part'
    size = 0
    try:
        with temp.open('wb') as target:
            async for chunk in request.stream():
                size += len(chunk)
                if size > MAX_FILE:
                    raise HTTPException(413, 'Files must be 20 MB or smaller.')
                target.write(chunk)
        with LOCK, connect() as db:
            if not path.exists():
                temp.replace(path)
                db.execute('INSERT OR IGNORE INTO files VALUES (?,?,?,?)', (ident, name, request.headers.get('content-type','application/octet-stream'), size))
    finally:
        temp.unlink(missing_ok=True)
    return {'fileId': ident, 'name': name, 'size': size}

@app.get('/api/files/{ident}')
def download(ident: str):
    with connect() as db:
        row = db.execute('SELECT * FROM files WHERE id=?', (ident,)).fetchone()
    if not row or not (DATA / row['id']).is_file():
        raise HTTPException(404, 'File not found.')
    return FileResponse(DATA / row['id'], filename=row['name'], media_type='application/octet-stream')

from newsletters import NewsletterService, create_router
newsletter_service = NewsletterService(connect, write, LOCK)
app.include_router(create_router(newsletter_service))
app.add_event_handler('startup', newsletter_service.start)
app.add_event_handler('shutdown', newsletter_service.shutdown)

app.mount('/', StaticFiles(directory=ROOT / 'static', html=True), name='web')
