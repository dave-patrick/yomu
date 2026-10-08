# Yomu web library and two-way sync

Single-owner web library, Chrome/Opera extension, and optional Android library sync.
The deployed Render target is project `prj-db22nau7bikc73c7rkn0`, Production
environment `evm-db22nau7bikc73c7rkng` in My Workspace.

## Deployment

The root `render.yaml` configures one Starter Python service with a 1 GB persistent
disk at `/var/data/yomu`. SQLite and uploaded files live on that disk. Expected
base cost is $7.25/month before bandwidth overages. Paid resource creation needs
the owner's approval, which was given for this service. The live site is https://yomu-fmze.onrender.com/.
Disk-backed deploys briefly interrupt requests and cannot scale to multiple
instances. This is appropriate for a private personal library.

Generate `YOMU_ACCESS_TOKEN` through Render's `generateValue` configuration.
Do not commit, print, or place the key in a URL. Pair the webpage, phone, and
extension using that same key. Phone keys are encrypted with Android Keystore.
Browser/extension keys use device-local browser storage: disconnect shared devices.
This is single-owner pairing, not a public multi-user account system.

## User workflow

* Webpage: paste an article URL or drop PDF, DOCX, and TXT files up to 20 MB each.
* Articles and uploaded documents appear in To read. Android performs article,
  PDF/OCR, and Word extraction using its existing importer; uploads wait while
  the phone is offline. The webpage can read extracted text after the next sync.
* Phone: Settings → Web library & sync → website and private access key →
  Connect & sync. This explicitly uploads the existing library and documents.
* Extension: load `extension/` as an unpacked Chromium extension for personal
  testing. Set the HTTPS server and access key; grant access only to that host.
  Click Save article on the current webpage. No browsing-history permission.

## Sync mechanics

Phone sends differences from transactionally stored baselines, not whole-library
replacement. Server compares changed fields to their baseline and merges edits
to different fields. Concurrent edits to the same field keep the current server
value and report a conflict. Deletes leave tombstones so an offline device cannot
resurrect deleted saved readings. Queue entries and playlist memberships can be
explicitly added again. Stable item IDs prevent repeated sync imports.

Phone automatic sync uses the selected interval while the activity is started and through
WorkManager at a minimum of 15 minutes in the background (Android may delay
jobs). A manual Sync now button is available. Browser checks every 15 seconds
while visible. File uploads and document extraction may require another sync
cycle before the webpage shows the extracted text.

Synced: reading items and extracted text, supported originals, To read/archive
status and order, Up next, playlists/memberships, history, sentence/character
progress and per-item playback speed. Provider keys, provider choices, global
appearance preferences, offline voice models, and MP3 exports remain local.
The webpage displays phone reading progress and supports mark read/unread;
browser speech playback and automatic browser reading-position capture are not
implemented. Raindrop is not connected in this version.

## Validation and remaining device checks

Run `python -m unittest test_sync -v` in `web/`. Android uses the ordinary Gradle
build/unit-test commands. A migration instrumentation test covers 3 → 4 while
preserving library content. Before enabling real sync, export a phone backup and
test a laptop URL, a text/PDF upload, phone rename, offline edit conflict, progress,
and queue/playlist changes in both directions on the actual deployed endpoint.
Do not claim deployment or live phone sync until those checks pass.

## Local preview

Create `web/.venv`, install requirements, set a random test-only
`YOMU_ACCESS_TOKEN` and `YOMU_DATA_DIR`, then run
`uvicorn app:app --host 127.0.0.1 --port 9184` from `web/`.
Local preview is HTTP on loopback; production phone/extension pairing requires HTTPS.

## Storage and recovery

Keep manual Android backups. Render disk snapshots alone are not a database
backup strategy; take SQLite-consistent backups and copy uploaded files off-site.
Removed library records retain their tombstones and uploaded binaries on the
server in this first version; a future explicit retention/purge workflow can
reclaim storage. File replacements should use a new file ID. Requests are
authenticated and content is never exposed by a public file URL.

## Planned web reader and settings

See [Web reader design](../docs/WEB-READER-DESIGN.md) and [Roadmap](../ROADMAP.md).
The dedicated reader is available at `/reader.html?id=<item-id>`. Read opens it
from the library; returning restores the view, search, scroll position, and focused
item in the same tab. Font, size, spacing, and width are saved for this browser;
theme uses the existing System/Light/Dark preference. Text is selectable, source
links are available, and opening or scrolling does not mark an item read or change
the phone's listening position. Separate reading cursors, offline position retry,
conflict choices, and shared reader defaults are implemented for alpha18 pairing.
See [Resume delivery](../docs/CROSS-DEVICE-RESUME-2026-10-07.md) for installation
and device acceptance status. Browser audio remains planned.
# Newsletter capture

Settings > Newsletters supports .eml samples, safe text preview, one-playlist routing rules, and delivery review/retry. Email-body imports produce ready synced items without phone extraction. The durable queue runs inside this service using its existing SQLite disk; no separate worker or Redis is provisioned.

The optional Postmark receiving hook stays disabled until `YOMU_NEWSLETTER_WEBHOOK_SECRET` (a separate random secret, minimum 32 characters) is configured. `YOMU_NEWSLETTER_ADDRESS` displays the actual inbound address privately in newsletter settings. The hook uses HTTPS Basic authentication with username `yomu`; do not reuse the library access key. Postmark remains available as an alternative provider. See [newsletter delivery/setup](../docs/NEWSLETTER-DELIVERY-2026-10-07.md).

Validation: `python -m unittest test_sync test_newsletters test_agentmail` from this folder uses an isolated temporary database.

AgentMail is the selected provider. Its signed receiver is `/hooks/newsletters/agentmail`; set `AGENTMAIL_API_KEY`, `AGENTMAIL_WEBHOOK_SECRET`, `AGENTMAIL_INBOX_ID`, and `YOMU_NEWSLETTER_ADDRESS` privately on Render. See [AgentMail setup](../docs/AGENTMAIL-SETUP.md). Receiving stays disabled until all three AgentMail credentials/identity variables are present.

Library search opens from the magnifying-glass button beside the Settings gear. The popup focuses the search field, filters the selected list live, preserves the active query when dismissed, and offers a visible clear action. Escape, Close, Enter, and outside clicks dismiss it; browser-native popover behavior is used.
