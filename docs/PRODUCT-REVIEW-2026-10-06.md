# Yomu product review — 2026-10-06

This review covers the Android app, website, Chrome extension, and shared sync
service. The strongest next investment is dependable capture, sync, and recovery,
followed by a calmer library and comfortable reading. The approved blue book and
headphone icon, satin gray wordmark, and light/charcoal themes remain the visual
direction.

## Scope and evidence

Reviewed the production source under `source/`, using Graft for code context and
the Impeccable audit workflow for interface/accessibility checks. A separate
reviewer inspected the website, extension, and sync implementation; Android
inspection was performed in the main review.

- Inspected the live website library at its desktop size and an emulated 390 × 844
  viewport, plus the live Settings page. Source icons loaded in the desktop view.
- At 390 CSS pixels, connection controls were hidden and document width was 461
  pixels. Row action buttons measured about 33 pixels high. This is browser
  emulation, not physical phone or touch-gesture testing.
- Reconnected wireless ADB and confirmed the installed phone app is
  `1.0.0-alpha14`, version code 14. No fresh phone UI, TalkBack, playback,
  interruption, or Android Auto walkthrough was performed.
- Six existing backend tests passed against a temporary database. The extension's
  existing mocked save-flow suite passed. These suites do not cover the protocol
  mismatch described below.
- Disposable API probes confirmed: duplicate article responses omit the existing
  ID; malformed field types are accepted; legacy `.doc` originals are rejected.
- The deterministic web markup scan returned no findings. It does not establish
  usability, accessibility, theme correctness, or reliable runtime behavior.
- No production records were changed and no app was installed or deployed.
  Android unit-test results are recorded in the validation appendix.

“Confirmed” below means observed in source, browser, or an isolated probe as
specified. “Risk” means a problematic code path exists but its timing/failure
scenario still needs controlled reproduction. Priorities are product sequencing,
not claims that every risk has already caused data loss.

## What is working well

- To read, Up next, playlists, Archive, and Read history are distinct concepts.
  Android has persistent storage, URL retry work, sentence playback/highlighting,
  offline speech, explicit MP3 export, and manual backup.
- The website supports capture, organization, extracted-text reading, approved
  branding, source icons, and Settings with browser-local appearance and roadmap
  access. System/light/dark preference ownership is correctly device-local.
- Sync has field baselines, safe replay behavior, deletion tombstones, server
  instance detection, and local-change preservation during a sync transaction.
  Existing tests exercise several of these protections.
- The extension requests access to the configured server and protects its stored
  key from untrusted extension contexts. It already distinguishes a successful
  article save from a failed playlist addition.
- Provider credentials remain outside shared records. Diagnostics deliberately
  avoid article text and credentials.

## Prioritized backlog

Effort: S = contained change; M = coordinated components; L = multiple surfaces
or protocol/storage work. Estimates are relative scope, not delivery dates.
R identifiers are stable references for the roadmap. Existing initiatives are
refined instead of being counted as new product capabilities.

### Reliability and behavior corrections

| ID | Priority / effort | Finding and proposed work | Evidence / confidence |
|---|---|---|---|
| R01 | P0 gate / M | Preserve edits and deletion during an in-flight import. Apply extraction results to a current, still-existing item; cancel obsolete jobs and prevent resurrection. | Risk: `ContentImporter.kt:52–75,114–127`; `LibraryViewModel.kt:230–235`; Room upsert `LibraryDatabase.kt:130–131`. |
| R02 | P0 gate / M | Coordinate backup/restore with sync, import, and playback. Stage/validate, preserve rollback until reopen succeeds, and explicitly choose how a restored library reconciles with the server. | Risk: `backup/LibraryBackupManager.kt:28–36,95–119`; sync mutex `sync/LibrarySync.kt:243`. |
| R03 | P1 / M | Isolate original-file failures so healthy metadata/text still sync. Align legacy Word support and size limits across surfaces; expose “original not synced” with a retry. | Confirmed mismatch: Android supports `.doc`; API rejects it. Uploads precede all metadata exchange: `sync/LibrarySync.kt:108–134`; `ContentImporter.kt:100`; `web/app.py:207–208`. |
| R04 | P1 / S | Return the existing article ID on duplicate capture. Re-saving an existing article must successfully add it to a selected playlist; website feedback must say “Already saved.” | Confirmed API probe + source contract: `web/app.py:190–192`; `extension/popup.js:10`; `web/static/app.js:43`. |
| R05 | P1 / M | Add versioned typed record validation, sensible defaults/ranges, and recovery for incompatible records. Reject bad input before it can poison repeated client sync. | Confirmed malformed types accepted in disposable probe; `web/app.py:87–97`; direct Android writes `sync/LibrarySync.kt:226–241`. |
| R06 | P1 / M | Reconcile file bytes and metadata after interruption. A retry must register an already-written original and verify immutable content rather than leaving it undownloadable. | Crash-window risk: binary move precedes metadata commit, retry skips metadata when file exists: `web/app.py:219–222,228–233`. |
| R07 | P1 / M | Batch and checkpoint large initial syncs; acknowledge progress and resume after interruption. Separate list metadata from full text as the library grows. | Confirmed limits/architecture: single phone batch `sync/LibrarySync.kt:124–134`; 32 MB / 10,000-change limit `web/app.py:114–120`. |
| R08 | P1 / M | Make synthesis cancellation and seeking belong to a playback generation/item. Ignore stale audio results after a rapid seek, item switch, stop, or queue transition. | Race risk: shared `isGenerating` guard and unscoped synthesis completion `playback/YomuPlaybackService.kt:310–405`. Requires service/device reproduction. |
| R09 | P1 / S | Correct phone action labels and gesture help. In To read, the menu's “Play now” currently invokes the hold callback that enqueues; Up next help still describes the opposite tap behavior. | Confirmed source wiring: `MainActivity.kt:666–685,899`. Keep the owner's chosen tap/hold convention and make labels/callbacks agree. |
| R10 | P1 / S–M | Keep pairing, sync status, disconnect, and New playlist reachable on narrow screens. Validate a replacement key before changing the active connection; put errors inside the dialog. | Mobile hiding observed; pairing source: `web/static/style.css:1`; `web/static/app.js:47`; live Settings directs users back to hidden library controls. |
| R11 | P1 / M | Preserve focus, open menus, and interaction during refresh. Coordinate mutations and polling; prevent duplicate submissions and older responses replacing newer state. | Source risk: whole navigation/library rebuilt every 15 seconds, unused busy flag: `web/static/app.js:2,13–20,28–40,50`. |
| R12 | P1 / M | Make sync/import state observable: last attempt, last success, error, activity, and acknowledged pending changes. Distinguish permanent authentication/format errors from transient network retries. | Confirmed silent automatic errors and snapshot-only Settings status: `MainActivity.kt:174–189`; `sync/SyncWork.kt:7–13`; `sync/SyncSettings.kt:31`; `sync/LibrarySync.kt:209–211`. |

### Daily reading and library improvements

| ID | Priority / effort | Enhancement | Evidence / relationship to existing plan |
|---|---|---|---|
| R13 | P1 / S–M | Calmer website rows: one clear Read action, accessible More menu, explicit readiness, contextual empty states, and newest-first history with dates. | Refines Wave 1. Seven row controls visible; generic empty guidance and unsorted history: `web/static/app.js:25,29–39`. |
| R14 | P1 / M | Dedicated web reader with source link, selectable structured text, font/size/spacing/width, stable URLs, and return to the previous list/search position. | Already Wave 2; basic dialog only shows title/text: `web/static/app.js:32`; view is in-memory `app.js:2,20`. |
| R15 | P1 / M | Phone “Read quietly,” selectable text, and a Follow narration toggle. Suspend auto-follow while browsing; bind controls/highlights to the displayed item or clearly offer to switch to Now playing. | New Android detail: To read opens through playback `MainActivity.kt:666–670`; sentence surfaces seek on tap and auto-scroll `1825–1882`; reader is a snapshot `LibraryViewModel.kt:150–155,240–262`. Queue/Auto transitions need acceptance checks. |
| R16 | P1 / M | Accessibility and theme parity: keyboard file upload, named dialogs, selected-nav semantics, progress announcements, useful focus, larger touch targets, and consistent light-theme tokens. Test phone large text/TalkBack and reader controls. | Web hidden file input, unnamed dialogs, visual-only progress: `index.html:5,7–8`; `app.js:20,31`; light overrides `style.css:5–6`. Mobile overflow observed. Preserve approved artwork/materials. |
| R17 | P1–P2 / M | Playlist management parity: rename/delete, counts, membership visibility, reorder, duplicate feedback, and extension creation/recent destinations. | Refines Wave 4. Web creation/membership exists but management UI is incomplete: `app.js:19,27,37–38,49`; Android VM exposes creation/membership `LibraryViewModel.kt:329–332`. |
| R18 | P2 / M | Show source/original access and availability separately from extracted-text readiness. Offer private original download on the web and explicit/lazy original download on another phone. | Ready document originals are not fetched to a new phone: `sync/LibrarySync.kt:192–207`; authenticated download already exists `web/app.py:228–233`. |
| R19 | P2 / M–L | Bulk actions plus recoverable deletion/undo, distinct from Archive/history cleanup. Define retention and cross-device restore identities before enabling a recycle bin. | Refines Wave 4; permanent sync tombstones and immediate original deletion exist: `web/static/app.js:39`; `LibraryViewModel.kt:230–235`. |
| R20 | P2 / L | Shared tags, source/type/status/read-state filters, text search, duration/title/date sorting, and optional saved views. Make search available across phone and web. | Refines Wave 4; current web search is title/source only `app.js:23–28`; Android has sorting but no library search UI. |
| R21 | P1–P2 / M | Durable extension save results and resumable playlist addition after closing/reopening the popup. Then add new/recent playlists, right-click links, and explicit selected-text capture. | New recovery detail on existing capture track; popup-only request sequence `extension/popup.js:7,10`; no background worker in `manifest.json`. Avoid duplicate items on retry. |
| R22 | P1 / L | Versioned cross-device resume with text/sentence anchors and separate reading/listening positions. Retain offline browser edits and make concurrent resume choices deliberate. | Existing Wave 3. Phone offset sums trimmed sentences `YomuPlaybackService.kt:499`; reader and speech splitters differ `MainActivity.kt:2093` / `YomuPlaybackService.kt:856`. Define one canonical mapping before web capture. |

### Scale, ownership, and further enhancements

| ID | Priority / effort | Enhancement | Evidence / dependency |
|---|---|---|---|
| R23 | P2 / M–L | Revision-aware incremental sync, lightweight list responses, lazy article text, pagination, and stable rendering. Optimize/correctly cache versioned static artwork. | Full state/text sent every poll `web/app.py:71–73,105–109,177–179`; all responses uncached `app.py:68`; current icon/wordmark roughly 851/665 KB. |
| R24 | P2 / S–M | Resilient source icons: lazy load, stable fallback, hostname cache, optional same-origin cache/privacy mode. | Existing Google/direct-site requests `web/static/app.js:54–68`. This exposes hostname requests, not article text; treat choice transparently. |
| R25 | P2 / M–L | Storage usage and bounded cleanup for originals, abandoned uploads, speech cache, and tombstones. Keep active media safe; purge only under a retention policy that accounts for offline devices. | Server retains binaries/tombstones; remote phone deletion leaves originals/mappings `LibrarySync.kt:215–219`; timestamped speech files have no explicit eviction `YomuPlaybackService.kt:377,414`. OS cache reclamation is not a retention policy. |
| R26 | P2 / M–L | Conflict details with item names and field choices, guided re-pair/server-restore recovery, device labels/last seen, and later per-device revocation. | Server conflict strings `web/app.py:139–162`; phone shows a count `LibrarySync.kt:209–211`; shared key authenticates all devices. Device list is bookkeeping, not revocable sessions. |
| R27 | P2 / M | Independent durable document work with page/download progress, cancellation, retry from the saved original, Wi-Fi-only/metered options, and optional charging constraints for large processing. | Current imports run within UI scope or sync pass; PDF loops `ContentImporter.kt:88–112,193–213`; network-only sync constraint `SyncWork.kt:24`. Failed Word/text items currently get a URL retry path or require re-sharing. |
| R28 | P2 / M | A compact setup/health screen: connected library, active provider/voice, offline-model readiness, actionable error category, local redacted diagnostics, and provider usage only when measurable. | Existing voice summaries/diagnostic ring are foundations `AppDiagnostics.kt:5–30`; many actions share one transient status `LibraryViewModel.kt:502–514`. Build on this instead of logging content or keys. |
| R29 | P2 / S–M | Roadmap contents/anchors, status/priority filtering, usable narrow tables, and working internal design links. Keep implemented/installed/verified documentation consistent with current source. | Generator strips Markdown links `web/build_roadmap.py:7–9`; design doc said Settings did not exist. Product/build notes also need freshness checks for gesture conventions, .doc support, and speed range. |
| R30 | P2 / L | Synced bookmarks/highlights and personal notes, anchored to text revision, with quick “return here” and export. | New enhancement; no annotation schema/UI in reviewed source. Depends on canonical text/progress anchors (R22), with explicit remapping behavior after re-extraction. |
| R31 | P2 / M | Listening comfort: visible sleep-timer countdown, stop at end of item, optional short rewind on resume, and an accessible listening bookmark. Make preparing/playing/paused/failed states distinct. | Timer currently pauses after a duration but publishes no timer state `YomuPlaybackService.kt:292–296`; snapshot only carries item/title/playing/sentence `PlaybackEvents.kt:10–17`. Validate interruption and queue semantics. |
| R32 | P1 gate / M | A focused cross-surface regression/release matrix covering the real API/extension contract, one-way modes, failed transfers, import races, playback races, restore, keyboard/mobile themes, and protocol migrations. | Current tests are narrow and mocks invent a duplicate ID `extension/tests/popup.test.cjs:6`. Avoid equating unit tests with installed/real-browser verification. |

## Recommended order

1. **Reliability release:** R01–R12 plus the corresponding R32 acceptance checks.
   Start with the reproduced R03/R04/R05 compatibility problems and the P0
   preservation/restore gates. Preserve the live library while testing on fixtures.
2. **Daily usability release:** R13–R17, the navigation theme toggle already on the
   roadmap, and the existing extension playlist-capture work. R15 adds a real
   quiet-reading path to the phone.
3. **Read-anywhere release:** R14/R22, original access (R18), and durable capture
   recovery (R21), followed by the existing browser listening plan.
4. **Larger-library release:** R19/R20/R23/R25/R27. Move batching (R07) earlier
   because its current size limit can block initial sync.
5. **Further value:** R24/R26/R28–R31; then planned server extraction, cloud web
   audio, Raindrop, offline web/PWA, and public distribution only after their own
   designs. No new hosting resource or recurring cost is required by this review.

Some work can overlap, but the same-data reliability gate should precede new
record types, annotations, external integrations, or a public account model.

## Acceptance checks for the first release

- Start URL/PDF extraction, rename/archive/delete the item, then finish extraction.
  Existing edits survive, deletion stays deleted, and cancelled work creates no
  extra row or orphan original.
- Save a legacy Word original and an oversized original beside a healthy rename
  and article. Healthy changes sync; failed original transfer has a clear local
  state and does not retry the whole library forever.
- Save the same article twice through the website/extension, including selecting
  a different playlist on the second save. One identity remains, correct
  membership is added, and feedback is accurate.
- Reject invalid field types, unsupported schema versions, impossible progress,
  and missing parents predictably. Existing sparse records migrate with defaults
  instead of becoming a new sync blocker.
- Simulate interruption between binary move and database commit; retry registers
  a downloadable original. Large batches resume from acknowledged chunks.
- Seek and switch items while synthesis is deliberately delayed. Only audio and
  progress belonging to the latest requested playback generation are applied.
- Open a reader, auto-advance the queue/change playback in Android Auto, and test
  its controls. The displayed item and command target remain understandable.
- Restore from an older backup with foreground and background work active in an
  isolated library. Restore is coordinated and reversible until validation
  completes; the subsequent sync choice is explicit.
- On a narrow website, pair/re-pair/disconnect/create a playlist using keyboard and
  touch-size controls. Rejected keys preserve the prior connection. Refresh
  leaves focus and open menus stable. Errors are visible in their dialogs.
- Exercise upload-only/download-only/two-way/manual sync, permanent auth failure,
  offline recovery, concurrent edits, and popup closure between save requests.
  Report current failure rather than a stale “Synced just now.”

## Validation appendix

- Backend: `python -B -m unittest discover -s web -p test_sync.py -v` — **6 passed**.
- Extension: `node --test extension/tests/popup.test.cjs` — **passed**; mocked
  scenarios cover new/duplicate save and partial failure but return an ID in the
  duplicate mock that the actual backend omits.
- API probes: disposable local database only; duplicate response **200 without
  ID**, wrong record field types **200**, legacy Word original upload **400**.
- Interface scan: `impeccable detect --json web/static` — **no deterministic
  findings**; manual inspection identified functional/interaction gaps it misses.
- Phone: ADB connection and installed version confirmed; physical UI and audio
  scenarios remain unverified. No install, new APK, or production sync fixture.
- Android: :app:testDebugUnitTest --offline — **17 passed**, 7 suites, no failures or skips. SDK XML/tool-version and Kotlin/deprecation warnings remain maintenance work; these checks do not exercise physical UI/audio.

Source evidence is relative to the repository root. Unqualified Android filenames
above are under `android/app/src/main/java/dev/yomu/app/`. Line references
describe this review snapshot and can move with later changes.
