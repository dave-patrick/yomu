# Yomu web library and two-way sync

Single-owner web library, Chrome/Opera extension, and optional Android library sync.
The proposed Render target is project `prj-db22nau7bikc73c7rkn0`, Production
environment `evm-db22nau7bikc73c7rkng` in My Workspace.

## Deployment proposal

The root `render.yaml` proposes one Starter Python service with a 1 GB persistent
disk at `/var/data/yomu`. SQLite and uploaded files live on that disk. Expected
base cost is $7.25/month before bandwidth overages. Paid resource creation needs
the owner's approval; no service has been provisioned by this implementation.
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

Phone checks every 30 seconds while the activity is started and through
WorkManager approximately every 15 minutes in the background (Android may delay
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
