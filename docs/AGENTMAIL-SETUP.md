# AgentMail newsletter setup

Selected 2026-10-07. Yomu receives newsletters and assigns ready text to an existing playlist; it never sends email, starts playback, or modifies Up next.

## Connect the existing free inbox

1. Sign in at https://console.agentmail.to. Keep the account on Free; no custom domain or paid upgrade is needed.
2. Create an API key for the Yomu receiver. Prefer a key scoped to the newsletter inbox with message-read permissions when the console supports it. Never put credentials in chat, Git, source, browser code, or documentation.
3. Create an inbox-scoped webhook to `https://yomu-fmze.onrender.com/hooks/newsletters/agentmail`, selecting only `message.received`. Copy its `whsec_` signing secret privately.
4. On the existing Render Yomu service, merge these environment variables without replacing the existing library token or data settings:
   - `AGENTMAIL_API_KEY`: provider API key, used only to read messages with omitted bodies/headers.
   - `AGENTMAIL_WEBHOOK_SECRET`: signing secret for this webhook.
   - `AGENTMAIL_INBOX_ID`: exact inbox_id from the console; typically the hosted email address, but verify the actual ID.
   - `YOMU_NEWSLETTER_ADDRESS`: receiving email address displayed privately in Settings > Newsletters.
5. Deploy the receiver with the environment variables. All three AgentMail variables are required; missing configuration returns 503. Invalid signatures return 401, unexpected inboxes return 403.
6. Forward a real newsletter, preferably inline or by subscribing directly with the inbox address. Review its safe-text preview and source before selecting a playlist and enabling the rule. Attached `.eml` forwarding is not parsed by the AgentMail adapter in this milestone; use the existing .eml sample upload if needed.
7. Receive a second, distinct issue. Verify automatic ready-text import, the playlist membership, and Android sync. Forwarding-provider confirmation emails require manual review; Yomu never follows their links.

## Delivery behavior and limits

Svix verifies the exact raw request with timestamp tolerance. Only signed `message.received` events for the configured inbox are accepted; spam, blocked, unauthenticated, and other event types are ignored. Event retries use the existing durable event ledger. Original Message-ID and content fingerprint checks protect against duplicate issues and preserve deletion tombstones.

If the notification omits its body or header map, Yomu retrieves the full message from the fixed HTTPS AgentMail API. Requests have a 10-second timeout, no redirects, and a 2 MB response limit. Retrieval happens outside SQLite locks. Upstream API/network/identity errors return 503 so AgentMail can retry; successful events are acknowledged only after durable local queue acceptance. No attachment URLs or email-body links are fetched. Intake stays bounded at 2 MB; normalized readable text at 500 KB; at most 100 unresolved deliveries. Provider retry windows are finite, so prolonged outages may require manually forwarding an issue again.

The private queue, retention, paused rules, missing-playlist handling, and manual retries are shared with sample/Postmark intake. Receiving configured in the UI describes environment setup, not proof of successful provider delivery. Live acceptance remains pending until the real-email checks above pass.

## Validation

`python -m unittest test_sync test_newsletters test_agentmail` from web uses an isolated temporary database. Tests cover missing/tampered/expired signatures, allowed inbox/event types, retries, automatic playlist routing, missing-body recovery, upstream failure, encoded fixed-host API access, redirects, identity mismatch, and oversized responses.

Official references:
- https://docs.agentmail.to/webhook-verification
- https://docs.agentmail.to/api-reference/webhooks/events/message-received
- https://docs.agentmail.to/api-reference/inboxes/messages/get
