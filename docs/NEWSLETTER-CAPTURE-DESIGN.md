# Newsletter capture and automatic playlist routing

Planned 2026-10-07. Owner wants both email forwarding and connected-mailbox capture eventually. The first sample/routing/backend milestone is now implemented; see [delivery and setup](NEWSLETTER-DELIVERY-2026-10-07.md). No external receiving account or mailbox connection has been enabled. Remaining design requirements below are acceptance goals, not assertions that every capability has shipped.

## Product experience

Settings > Newsletters lists each subscription, its destination playlist, capture method, enabled state, last received issue, and any actionable error. Add newsletter offers Forward emails first; Connect mailbox follows in phase two.

Example: choose Tech News, identify the newsletter by sender and (where available) List-ID, preview a sample, then enable its rule. Subsequent issues become ready-to-read items in To read and Tech News. Saving never starts playback or adds to Up next. Existing read history remains independent.

Website settings owns connection setup and rule editing initially. Phone displays imported issues in ordinary playlists, with newsletter source information and the normal reader/player. Phone rule management can follow after the first release.

## Phase one: forward to Yomu

1. Provision an inbound email address for the owner's library, using an inbound-email provider and a private, rotatable recipient token. The exact provider, domain, cost, and retention policy remain implementation decisions.
2. The owner forwards a sample newsletter or configures their mail provider's forwarding filter. Yomu previews the extracted title/body and detected original sender/List-ID, including whether forwarded headers are reliable.
3. Choose an existing playlist or create one, confirm a rule, and enable it. Rule creation is explicit; a single sample does not authorize importing other mail.
4. Receive future matching messages through a signed provider webhook, enqueue durable imports, extract the email body on the server, then atomically create the library item and playlist membership through the existing revision mechanism.
5. Unmatched messages go to a private Needs routing view rather than disappearing or being assigned arbitrarily. Forwarding failures outside Yomu remain the mail provider's responsibility; show setup diagnostics and a test-delivery action.

If the owner subscribes directly with the Yomu address, verification links must be surfaced for manual opening. Yomu never automatically follows subscription, unsubscribe, or confirmation links.

## Phase two: connect a mailbox

Use provider-approved delegated authorization with the narrowest practical read access. The owner explicitly selects senders/lists or a dedicated mail label/folder, approves a preview, and chooses a destination. Start from activation time by default; backfill requires a chosen date range and item limit.

Prefer incremental provider change tracking, with a durable checkpoint and reconciliation after a missed notification. Support polling where necessary; provider-specific schedules and quotas are decisions for implementation. Never mark emails read, archive them, or delete them as a side effect. Unlink stops capture and removes connection credentials; already imported Yomu content stays.

Gmail and other provider support are separate increments, not promised together. Provider choice and current OAuth verification/access constraints need research before implementation.

## Rule semantics

- One active routing rule per identified newsletter; one destination playlist in the first release. Use stable playlist IDs so renaming does not break routing.
- Match configured List-ID when available, otherwise exact original sender; optional subject criteria refine a rule. Display names alone are insufficient. Forwarded original-header detection is advisory and must be previewed; it is not authentication.
- Overlapping matches require a routing choice. Do not guess a destination or silently fan out to every matching playlist.
- Future issues use the current rule. Changing its destination does not move old issues unless the owner explicitly requests a bounded bulk action.
- Pausing stops new imports; re-enabling starts with new deliveries. Reprocessing or backfill is explicit.
- Deleting a destination playlist disables that rule and raises Needs attention. Imports awaiting routing remain reviewable; no replacement playlist is created silently.
- Append each successful import to the playlist in receipt order using an atomic sort-order allocation. Email publication time is separate metadata and can inform a later sort option.

## Content and identity

Prefer the readable HTML email body, falling back to plain text. Remove scripts, forms, tracking pixels, and email navigation where safely identifiable; preserve headings, paragraphs, links, author/source, and publication date. Render sanitized content or escaped text only. Never load remote images automatically during ingestion.

A newsletter is content, not just its website URL: link-only editions remain useful short items and may offer an explicit Save linked article action later. Attachments, paywalled-page extraction, OCR, and automatic link crawling are outside phase one.

Use durable inbound-event IDs for webhook replay and provider message IDs for mailbox replay. Cross-method duplicate detection uses reliable original Message-ID plus newsletter identity when preserved; a normalized-content fingerprint can flag possible duplicates when forwarding changes identifiers. Do not collapse distinct issues solely because their subjects or URLs match. Ambiguous matches remain visible for review.

Deletion tombstones prevent delivery retries or mailbox replay from resurrecting deleted issues. Intentional re-import needs an explicit action. A deleted-identity ledger must survive job cleanup for the supported replay window.

## Architecture in this codebase

The current /api/articles path saves a URL with pending text and waits for phone extraction. Newsletter ingestion instead produces normalized text on the server so it works while the phone is offline.

Reuse reading_items and playlist_memberships, existing server revisions, and normal device sync. Validate the source representation against Android importer dispatch before choosing an additive newsletter source type or metadata schema. Deploy compatible Android parsing first if shared schema changes are required.

Proposed server-private tables:

- newsletter_connections: capture method, enabled state, checkpoint, secret references.
- newsletter_rules: connection, sender/List-ID criteria, destination playlist ID, activation time.
- newsletter_deliveries: external identity, issue identity, provenance, receive/publication times, import state, and dedupe/tombstone information.
- newsletter_jobs: durable payload reference, attempts, next retry time, bounded error code, completion state.

Connections, tokens, raw email, and OAuth credentials do not enter general library sync. Only finished reading content and approved display metadata synchronize. Credential storage needs encryption with a separately managed key and revocation support.

The signed inbound webhook needs its own authentication path rather than the browser access key. Authenticate, bound message size and payload parsing, persist idempotently, and acknowledge only after durable acceptance. A durable worker processes jobs with bounded retries and a visible failed state. Worker placement must fit the existing SQLite/persistent-disk topology: do not assume a separate Render worker can share the web service's mounted disk. Evaluate a same-service durable consumer versus a database/queue migration before choosing hosting resources.

Raw email has a short, explicit retention window for retry/debugging; retain extracted content as normal Yomu data. Avoid private message bodies and credentials in logs. Rotation disables the old inbound address; sender allowlists reduce unwanted intake but are not proof of sender identity.

## Delivery and priority

P2, L overall; connected mailboxes add a separate L integration. Prioritize after current import/sync recovery and playlist reliability, alongside the server-independent extraction track. Basic email-body extraction can ship independently of PDF/OCR and browser audio.

1. Validate representative newsletter samples and choose ingestion/provider architecture.
2. Build forwarding, durable jobs, body extraction, rule preview, and one-playlist routing.
3. Add error/retry visibility, deduplication, pause/unlink, and device compatibility acceptance.
4. Add one mailbox provider with incremental checkpoints and bounded backfill.
5. Consider phone rule editing, multiple destination playlists, attachments, and richer filters separately.

## Acceptance checklist

- [ ] A forwarded HTML and plain-text issue is readable and listenable without the phone doing extraction.
- [ ] The issue appears once in To read and once in the selected playlist after sync; playback does not start.
- [ ] Replay, timeout-after-commit, worker restart, repeated forwarding, and dual-method capture do not silently duplicate content.
- [ ] Distinct editions with the same subject remain distinct; ambiguous duplicates are reviewable.
- [ ] Paused rules, deleted playlists, unmatched mail, parse failure, and rejected authentication produce clear states.
- [ ] Phone offline/reconnect and one-way sync obey existing direction settings.
- [ ] A deleted issue is not resurrected by a retry or mailbox reconciliation.
- [ ] Rule edits affect future deliveries; moving historical issues requires explicit confirmation of scope.
- [ ] Malicious/oversized HTML, attachment payloads, tracking images, and links cannot execute or trigger unbounded fetches.
- [ ] Disconnect/revoke works; email account state remains unchanged; secrets and raw mail stay outside shared sync.
- [ ] Mailbox checkpoints recover missed changes and bounded backfill respects the selected source/date range.
