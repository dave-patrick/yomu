"""AgentMail receive-only adapter. Fetches only from the fixed provider API."""
import json
import os
from urllib.parse import quote

import httpx
from fastapi import HTTPException
from svix.webhooks import Webhook, WebhookVerificationError

MAX_MESSAGE = 2 * 1024 * 1024


def configured():
    return all(os.environ.get(name, '').strip() for name in
               ('AGENTMAIL_API_KEY', 'AGENTMAIL_WEBHOOK_SECRET', 'AGENTMAIL_INBOX_ID'))


def identifier(value):
    if not isinstance(value, str) or not 1 <= len(value) <= 500:
        raise ValueError('Invalid identifier')
    return value


async def fetch_message(inbox_id, message_id):
    # No event-supplied URLs, redirects, or attachment downloads.
    url = 'https://api.agentmail.to/v0/inboxes/' + quote(inbox_id, safe='') + '/messages/' + quote(message_id, safe='')
    try:
        async with httpx.AsyncClient(timeout=10, follow_redirects=False) as client:
            async with client.stream('GET', url, headers={'Authorization': 'Bearer ' + os.environ['AGENTMAIL_API_KEY']}) as response:
                if response.status_code != 200:
                    raise HTTPException(503, 'Email retrieval unavailable. Provider should retry delivery.')
                body = bytearray()
                async for chunk in response.aiter_bytes():
                    body.extend(chunk)
                    if len(body) > MAX_MESSAGE:
                        raise HTTPException(413, 'Email exceeds the 2 MB intake limit.')
        result = json.loads(body)
        if not isinstance(result, dict) or result.get('inbox_id') != inbox_id or result.get('message_id') != message_id:
            raise ValueError('Message identity mismatch')
        return result
    except (httpx.HTTPError, ValueError):
        raise HTTPException(503, 'Email retrieval unavailable. Provider should retry delivery.')


async def receive(service, raw, headers):
    if not configured():
        raise HTTPException(503, 'AgentMail receiving is not configured.')
    try:
        Webhook(os.environ['AGENTMAIL_WEBHOOK_SECRET']).verify(raw, dict(headers))
        event = json.loads(raw)
    except (WebhookVerificationError, ValueError, TypeError):
        raise HTTPException(401, 'Invalid newsletter webhook signature.')
    if not isinstance(event, dict):
        raise HTTPException(400, 'Invalid AgentMail event.')
    # Never automatically import spam, blocked, or unauthenticated notifications.
    if event.get('event_type') != 'message.received':
        return dict(status='ignored')
    try:
        event_id = identifier(event['event_id'])
        message = event['message']
        inbox_id = identifier(message['inbox_id'])
        message_id = identifier(message['message_id'])
    except (KeyError, TypeError, ValueError):
        raise HTTPException(400, 'Invalid AgentMail message identity.')
    if inbox_id != os.environ['AGENTMAIL_INBOX_ID']:
        raise HTTPException(403, 'Unexpected newsletter inbox.')
    event_key = 'agentmail:' + event_id
    with service.lock, service.connect() as db:
        existing = db.execute('SELECT id,state FROM newsletter_deliveries WHERE event_key=?', (event_key,)).fetchone()
        if existing:
            return dict(existing)
    # Large webhooks omit bodies; fetch full headers when absent to retain List-ID.
    # Retrieval occurs outside the SQLite lock. Failed retrieval is not acknowledged.
    if not (message.get('text') or message.get('html')) or not isinstance(message.get('headers'), dict):
        message = await fetch_message(inbox_id, message_id)
    try:
        mail_headers = {name.lower(): value for name, value in (message.get('headers') or {}).items()}
        payload = dict(title=message.get('subject') or '', sender=message['from'],
                       text=message.get('text') or '', html=message.get('html') or '',
                       listId=mail_headers.get('list-id', ''), messageId=mail_headers.get('message-id') or message_id,
                       receivedAt=message.get('timestamp') or '')
        if not all(isinstance(value, str) for value in payload.values()):
            raise ValueError('Invalid message fields')
        if len(json.dumps(payload).encode('utf-8')) > MAX_MESSAGE:
            raise HTTPException(413, 'Email exceeds the 2 MB intake limit.')
    except (KeyError, TypeError, ValueError, AttributeError):
        raise HTTPException(400, 'Invalid AgentMail email payload.')
    # Return success only after the existing durable queue has accepted the email.
    return service.accept(payload, event_key)
