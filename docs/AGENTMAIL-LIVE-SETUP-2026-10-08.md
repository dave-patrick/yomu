# AgentMail receiving setup — 2026-10-08

## Confirmed

- Existing account reconnected; Free tier retained.
- Permanent receiver key scoped to the one newsletter inbox with read-only access. No sending permission.
- Official inbox-scoped webhook API used to create an enabled endpoint for `/hooks/newsletters/agentmail`, subscribing only to `message.received`. Response confirmed exactly one `inbox_ids` subscription and no pod subscriptions.
- Temporary setup key limited to Inbox Read, Message Read, Webhook Read, and Webhook Create; revoked after setup. An unused key from the interrupted setup was also revoked. Existing onboarding key retained.
- Four receiving settings added to the existing Render service while preserving all existing environment entries. Credentials never committed. Deployment `dep-db3u5bmb7d7c739l6dng` confirmed live.
- Permanent key successfully listed the approved inbox (HTTP 200).
- Existing forwarded issue from before webhook creation was manually backfilled with a deterministic event identity through the deployed signed receiver (HTTP 200, durable queued result). Receiver retrieved its body through AgentMail.
- Preview detected the original sender. Owner-selected Newsletters playlist created; issue saved as ready text in To read and Newsletters; exact-sender rule active.
- `web/.venv/Scripts/python.exe -m unittest test_sync test_newsletters test_agentmail`: 29 tests passed.
- Reconciled roadmap served at `/roadmap.html`: HTTP 200; current status table and Alpha23 present.

## Acceptance still open

- Fresh AgentMail provider delivery. A manually signed backfill is not proof of the provider-to-webhook path.
- A distinct subsequent issue automatically assigned by the rule without duplicate import.
- Android sync and reading/listening verification. Device connected through adb but was locked during the check.
- Newsletter text polish: this real forwarded email contains hidden preheader padding and long tracking links. Extraction succeeds, but readable-text cleanup needs a separate focused pass.

No email was sent, no sender links were followed, and capture did not start playback. Other newsletter senders still require preview and routing.
