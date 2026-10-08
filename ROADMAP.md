# Yomu product roadmap

Updated 2026-10-07. Covers the Android app, website, Chrome extension, and shared
sync service. This is a prioritized plan, not an implementation or delivery-date
commitment. The original Android milestones remain in
[Pre-development package](YOMU-PRE-DEVELOPMENT-PACKAGE.md).

## Product direction

### Usability implementation — 2026-10-06

Implemented the first usability pass from R09–R13/R16: reachable mobile
connection and playlist controls; replacement-key validation before activation;
in-dialog errors; serialized website actions and stable keyed rows/navigation;
Read + More item controls; explicit import states and contextual empty views;
newest-first completion history; keyboard file browsing, dialog labels, progress
semantics, narrow-screen layouts, and matching light/dark tokens. The left-nav
theme toggle and Settings System/Light/Dark preference are implemented.

Android now separates the To read “Play now” menu from the hold-to-queue gesture,
shows gesture help and long-press accessibility labels, and observes sync attempts,
successes, failures, and busy state live. Permanent authentication/server-library
errors pause automatic retries; transient network errors keep retrying. The APK
is installed through alpha17 and unit-tested; broader device/TalkBack acceptance
remains pending. Reader transitions, screen-off preparation, HD buffering, and
live voice changes have subsequent fixes documented in their delivery notes.

Implemented 2026-10-07: R14 dedicated web reader with stable article/list URLs,
selectable paragraphs, source link, browser-local font/size/spacing/width/theme,
and return to the previous library view/search/scroll/focus. Opening and scrolling
do not publish listening progress or mark content completed. Unit and isolated
Chrome desktop/mobile checks passed; cross-device resume remains Wave 3.

Implemented 2026-10-07, alpha18 installed; pending device acceptance: separate
cross-device reading cursors, explicit Resume reading, offline browser position
retry, conflict choices, shared font/size/spacing defaults with device overrides,
and phone Follow narration. See [Resume delivery](docs/CROSS-DEVICE-RESUME-2026-10-07.md).

Remaining: phone Read quietly entry point and selectable text,
broader large-text/TalkBack coverage, and playlist feature
parity. The grouped checklists below remain open until their full criteria pass.

Validation and implementation details: [Usability pass](docs/USABILITY-PASS-2026-10-06.md).

Capture from a laptop or phone, organize once, and read or listen wherever it is
convenient. Preserve an offline-capable phone library and make cross-device state
understandable. Keep To read, Up next, playlists, archive, and Read history distinct.
Saving content never starts playback; normal listening is incremental playback,
while MP3 generation is an explicit export.

## Verified baseline and remaining checks

- Android library, importers, reader, listening, playlists, queue, and manual backup
  are implemented. Production audio lifecycle and Android Auto still need broader QA.
- Private Render web library is deployed with article/document saving, organization,
  a dedicated web reader, approved branding, and source website icons.
- Owner verified initial sync and library changes in both directions.
- Android sync direction/interval controls and matching settings styling are installed.
  One-way modes, concurrency, offline conflicts, and recovery need deeper device QA.
- Extension v0.2.0 implements page saving, existing-playlist selection, light/dark/system
  appearance, pairing, and duplicate handling. Automated mocked save-flow checks pass;
  real Chrome installation/permission/save/theme/phone-sync checks remain unconfirmed.
- Dedicated web reader and browser-local reading preferences are implemented.
  Web audio, shared global preferences, tags, and Raindrop integration are planned.
  New article/document extraction currently depends on the phone.

## Priority assessment

### Top five remaining priorities — 2026-10-07

1. **Reliability and recovery (P0/P1):** protect edits/deletes during import,
   coordinate restore with active work, and finish interrupted/large-file sync
   recovery and acceptance checks (R01–R08/R32). Recent playback generation fixes
   are delivered; the broader reliability group remains open.
2. **Actionable import/sync status (P1):** finish last-attempt/success, pending
   changes where tracked, per-item retry, and permanent-versus-transient errors
   across surfaces (R12). The first usability/status pass is implemented.
3. **Phone quiet reading and follow controls (P1):** Read quietly, selectable text,
   Follow narration, and clear controls when displayed and playing items differ
   (R15). Reader article-transition fixes are delivered; this feature remains open.
4. **Cross-device reading resume (P1):** canonical text anchors, separate reading
   and listening positions, offline recovery, and shared reader defaults (R22/Wave 3).
   Implementation is built and tested; alpha18 is installed. Physical
   bidirectional acceptance remain pending.
5. **Playlist management and capture (P1):** rename/delete/counts/reorder,
   visible membership, and faster/recoverable extension destinations (R17/R21).

The table below describes the original initiative sequence, including partly
delivered work. The dedicated website reader and initial library action cleanup
are implemented. Browser audio is deferred by the owner; Play is a disabled stub.

Priorities reflect daily usefulness, user friction, dependencies, and risk. Effort is
relative to this codebase: S = contained feature; M = several coordinated changes;
L = cross-platform or backend work; XL = substantial integration and verification.
These are scope estimates, not promised durations. P0 gates dependable operation;
P1 is the next product work; P2 follows the core experience; P3 needs separate scope.

| Rank | Priority | Initiative | Surfaces | Effort | Why this order |
|---|---|---|---|---|---|
| 1 | P0 | Sync/import/extension reliability and recovery | All | M | Protect existing content before expanding behavior |
| 2 | P1 | Clear import and sync status with retries | All | M | Removes uncertainty about whether saves are usable |
| 3 | P1 | Cleaner library rows and actions | Website | S | Immediate usability improvement with little migration risk |
| 4 | P1 | Proper reader and website settings | Website | M | Gives the laptop library its main missing daily capability |
| 5 | P1 | Cross-device resume and shared reader defaults | Website/app/backend | L | Completes the read-anywhere workflow; requires a stable reader |
| 6 | P1 | Faster playlist capture | Extension | M | Organize at save time without switching apps |
| 7 | P2 | Bulk library management | Website/app | M | Reduces repetitive work as the backlog grows |
| 8 | P2 | Tags and richer search/filtering | All/backend | L | Makes a larger library discoverable; requires shared schema work |
| 9 | P2 | Right-click link and selected-text capture | Extension/app/backend | M–L | Adds useful capture types after the basic save flow is reliable |
| 10 | P2 | Browser listening and player | Website | L | Builds on reader/progress work; browser capabilities need validation |
| 11 | P2 | Preferred cloud voices on the web | Website/backend/app | XL | High value, but introduces credentials, costs, and timing complexity |
| 12 | P2 | Phone-independent article/document extraction | Backend/website | L–XL | Removes a workflow bottleneck; security/OCR/runtime scope is substantial |
| 13 | P3 | Raindrop two-way integration | Backend/all | XL | Useful external capture source; conflict/auth/delete rules need separate design |
| 14 | P3 | Offline web/PWA and web MP3 export | Website/backend | L–XL | Storage/privacy/export decisions should follow stable reading and audio |
| 15 | P3 | Public distribution readiness | App/extension/backend | XL | Only if the owner chooses distribution beyond personal use |

## Newsletter capture — planned 2026-10-07

Owner-requested: support both forwarding newsletters to a private Yomu address and
connecting a mailbox eventually, with automatic routing to a selected playlist.
Start with forwarding, server-side email-body extraction, rule preview, durable
retry/deduplication, and source/error visibility. Connected mailboxes follow as a
separate increment. P2 / L, after core import/sync and playlist reliability, alongside
phone-independent extraction; no browser-audio dependency.
See [Newsletter capture design](docs/NEWSLETTER-CAPTURE-DESIGN.md) for mechanics,
privacy, rule behavior, dependencies, and acceptance gates.

## Review additions — 2026-10-06

A full source and bounded interface review is recorded in
[Product review and enhancement backlog](docs/PRODUCT-REVIEW-2026-10-06.md).
R identifiers refer to that review. These are planned refinements and additions;
the review did not deploy fixes or verify all phone/browser runtime paths.

### Priority changes and immediate gates

| Order | Priority | Work | Review IDs | Effort |
|---|---|---|---|---|
| 1 | P0 gate | Preserve edits/deletes during imports; coordinate backup/restore with active work and subsequent sync | R01–R02 | M |
| 2 | P1 | Isolate rejected/large originals; fix duplicate article identity for extension playlists; validate record types | R03–R05 | S–M |
| 3 | P1 | Recover interrupted file persistence; batch/checkpoint initial sync; cancel stale synthesis results | R06–R08 | M |
| 4 | P1 | Correct phone action labels; mobile pairing/playlist access; stable website refresh; observable sync failures | R09–R12 | S–M |
| 5 | P1 | Calmer library, proper web reader, quiet phone reading/follow control, accessibility/theme parity, playlist parity | R13–R17 | S–M |
| 6 | P1–P2 | Original access, bulk/undo, search/tags, durable extension capture, canonical cross-device resume | R18–R22 | M–L |
| 7 | P2 | Incremental data, icon privacy/cache, bounded storage, conflict/device recovery, independent document jobs | R23–R27 | S–L |
| 8 | P2 | Setup/health view, usable roadmap/docs, synced annotations, listening comfort | R28–R31 | S–L |

R32 is the focused release/regression gate across these groups. A P0 gate marks a
content-preservation risk to reproduce and resolve before release, not a claim
that the review demonstrated production data loss.

### Add to Wave 0 — Reliability and recovery

- [ ] Apply extraction updates to the current existing item, preserve concurrent
  edits, cancel obsolete import work, and prove deleted items stay deleted (R01).
- [ ] Coordinate backup/restore with sync/import/playback; validate before dropping
  rollback and define restore-versus-server reconciliation (R02).
- [ ] Sync healthy text/metadata despite a failed original; align .doc and size
  support and show per-file retry/availability (R03).
- [ ] Return the existing ID for duplicate saves and prove duplicate-to-playlist
  behavior using the actual backend contract (R04).
- [ ] Validate versioned record types/defaults/ranges/parents and recover from
  incompatible records without repeatedly poisoning a full download (R05).
- [ ] Recover file bytes/metadata after interrupted commits; batch and acknowledge
  initial sync within server limits (R06–R07).
- [ ] Cancel or ignore obsolete synthesis after seek/item changes; make phone
  gesture help, menu labels, and callbacks agree (R08–R09).
- [ ] Add isolated acceptance fixtures for these paths, one-way modes, popup
  closure, restore during work, and real device/browser verification (R32).

### Add to Waves 1–3 — Everyday use and reading

- [ ] Keep mobile connection, status, disconnect, and playlist creation reachable;
  validate replacement keys before activation and show errors in-dialog (R10).
- [ ] Preserve focus/open controls through polling; coordinate mutations and reject
  stale responses rather than rebuilding the whole interface mid-action (R11).
- [ ] Observe last attempt/success/failure live; distinguish permanent errors from
  network retries and show real per-item extraction state (R12–R13).
- [ ] Add stable reader/list URLs, Back/search restoration, accurate duplicate
  feedback, contextual empty views, and completion-date history (R13–R14).
- [ ] Add phone Read quietly, selectable text, Follow narration control, and a
  deliberate command target when displayed and playing items differ (R15).
- [ ] Verify keyboard upload, dialog naming, navigation selection, progress
  semantics, large text/TalkBack, touch targets, overflow, and theme parity (R16).
- [ ] Add source/original links and explicit original download availability across
  devices; preserve canonical text anchors for reading/listening resume (R18/R22).

### Add to Wave 4 and the scale track

- [ ] Add playlist rename/delete/counts/reorder and faster extension destinations;
  make duplicate membership feedback clear (R17).
- [ ] Design recoverable deletion/undo with retention and cross-device identity
  rules before implementation; keep it distinct from Archive (R19).
- [ ] Extend filters/search/sorting to phone and website, with optional saved views
  after shared tags/search are reliable (R20).
- [ ] Persist extension save intents/results and resume playlist completion after
  popup closure without duplicating captured articles (R21).
- [ ] Use incremental revisions, list metadata, lazy text, pagination, and cached
  optimized static assets; retain no-store for private API responses (R23).
- [ ] Add lazy/cached resilient source icons and an explicit privacy choice (R24).
- [ ] Show storage usage; bound speech cache/orphan file cleanup and define safe
  binary/tombstone retention for devices that stay offline (R25).
- [ ] Add item/field conflict explanations, guided server-recovery/re-pair, device
  labels/last-seen, and later scoped device revocation (R26).
- [ ] Move original downloads/extraction to durable independent jobs with progress,
  cancel/retry from saved originals, and metered/Wi-Fi controls (R27).

### Further product additions

- [ ] Setup/health overview with voice/offline readiness, useful redacted
  diagnostics, and measured provider usage where supported (R28).
- [ ] Roadmap contents/anchors/filtering and working internal design links; keep
  source, installed behavior, and validation claims in documentation aligned (R29).
- [ ] Synced bookmarks/highlights/notes with text-revision anchors and export,
  after canonical progress/schema work (R30).
- [ ] Visible sleep-timer state, end-of-item stop, optional resume rewind, listening
  bookmarks, and explicit preparing/paused/failed player states (R31).

## Delivery waves and acceptance gates

### Website settings and roadmap access

- [x] Provide a persistent Settings entry on desktop and mobile navigation.
- [x] Open the complete roadmap inside Yomu, without GitHub authentication or external navigation.
- [x] Keep the website copy generated from this document so published plans do not drift.
- [ ] Group appearance, connection, sync, reading, and later listening controls in Settings.
- [ ] Verify direct links, Back navigation, light/dark appearance, and roadmap readability on narrow screens.

Priority: P1, alongside Wave 1. Basic appearance and roadmap access ship first;
reader, sync policy, and listening controls follow their respective waves.

### Website theme settings and navigation toggle

Priority: P1; effort: S. Ship independently of the full reader.

- [x] Offer Light, Dark, and System in website Settings and save the preference for this browser.
- [ ] Add a visible light/dark button in the left navigation for switching without opening Settings.
- [ ] Show the active theme and label the action clearly; keep the button keyboard-accessible.
- [ ] Keep the navigation button and Settings selector in sync. An explicit toggle selects
  Light or Dark; choosing System in Settings restores automatic OS theme following.
- [ ] Apply consistent theme colors to the library, reader, settings, roadmap, dialogs,
  menus, inputs, source-icon tiles, and focus indicators.
- [ ] Preserve the selected theme through reload/navigation without a wrong-theme flash.
- [ ] Provide the same quick toggle in the compact mobile navigation and verify contrast
  and branding readability in both themes.

Gate: switching from navigation updates the whole website and its Settings value;
System follows OS changes; preferences remain browser-local and do not change the
phone or extension theme. The left-navigation toggle is planned, not yet implemented.

### Wave 0 — Establish a dependable baseline

- [ ] Run a real Chrome extension walkthrough: install/reload, pairing permission,
  valid/invalid key, restricted page, light/dark/system, save, duplicate, playlist,
  partial playlist failure, and appearance on the phone.
- [ ] Exercise phone upload-only, download-only, two-way, and manual modes; confirm
  background scheduling does not defeat manual mode or selected intervals.
- [ ] Test offline rename/delete/re-add, concurrent fields, queue order, playlist
  membership, large/unsupported files, and document extraction failures.
- [ ] Prove recovery using an isolated library: SQLite-consistent server backup plus
  uploaded files, phone backup, restart/redeploy, and restore. Document retention.
- [ ] Verify provider/playback errors, interruption handling, and Android Auto on the
  target device where available. Carry remaining audio QA alongside later waves.

Gate: no data-loss, unintended overwrite, credential exposure, or duplicate-import
issue remains unresolved in the tested paths. Do not erase the production library
or use destructive real-data fixtures for verification.

### Wave 1 — Status and a calmer library

- [ ] Distinguish Saved, Waiting for phone, Extracting, Ready, and Failed for imports.
  Include a meaningful next action: open phone, sync, retry, or inspect the error.
- [ ] Show last successful sync, pending changes when measurable, current activity,
  and failures on phone and website. Do not equate polling success with completed
  extraction or manufacture pending counts without tracked acknowledgments.
- [ ] Let the extension distinguish article saved from playlist addition completed;
  retain an actionable result after reopening where practical.
- [ ] Simplify website rows: source icon, title, source/time/status, progress, a clear
  Read action, and an accessible More menu for secondary actions.
- [ ] Keep queue removal, archive, history cleanup, and content deletion visibly distinct.

Gate: a user can identify where a saved article is, whether it is readable, what
failed, and how to retry. Verify narrow layouts, keyboard navigation, theme contrast,
and no accidental deletion from the simplified action menu.

### Wave 2 — Web reader and settings

Website audio placeholder added 2026-10-07: every reading list shows a disabled
“Play · Coming later” action beside Read. This is a visible stub only; browser
synthesis, player controls, queue playback, and audio/progress integration remain
planned under Browser listening and player. It does not send phone commands.

- [x] Replace the basic dialog with a dedicated responsive reader and stable navigation.
- [x] Add font, size, line spacing, column width, light/dark/system theme, and visible
  selected settings; persist device-local preferences through reload.
- [ ] Add website Settings for Reading, Sync, and Connection. Keep Listening hidden
  until it has functioning controls.
- [ ] Add browser-local automatic/manual interval and direction controls with honest
  behavior descriptions. Website configuration never changes phone scheduling.
- [ ] Preserve text selection, source links, loading/error states, and explicit Mark read.

Gate: comfortable reading at laptop and narrow-screen sizes; keyboard focus, font
scaling, and navigation work; opening content never marks it finished automatically.
Design reference: [Web reader and settings design](docs/WEB-READER-DESIGN.md).

### Wave 3 — Resume and portable reader defaults

Implementation delivered 2026-10-07: separate reading_cursors and reader_defaults,
original-text anchors with safe remapping, offline browser queue/retry, explicit
conflict decisions, phone Follow narration, and portable reading defaults with
local overrides. Alpha18 is installed in place over local Wi-Fi. Physical bidirectional,
one-way, TalkBack, and multi-tab acceptance remain open; grouped checks below
are gates rather than assertions that the code has not been implemented.

- [ ] Capture web reading position using sentence/character anchors and text revision,
  not pixels. Debounce writes and retain unsent state locally.
- [ ] Resume phone → website and website → phone; make ambiguous/concurrent resume
  choices visible without interrupting active playback.
- [ ] Add versioned shared reader defaults with per-device overrides and explicit reset.
- [ ] Keep theme/width, sync direction/interval, permissions, export paths, offline
  voice models, and credentials local to the appropriate device.
- [ ] Test schema migration, old-client behavior, offline edits, one-way modes, text
  changes, and multiple browser tabs. Keep reading and listening cursors distinguishable.

Gate: users resume predictably across devices; remote progress never seeks an active
player unexpectedly; old clients retain their libraries and settings.

### Wave 4 — Capture and organize faster

- [ ] Extension: create a playlist during save and remember recent/favorite destinations.
  To read remains the default; playlist membership does not remove it from To read.
- [ ] Website/app: bulk select and add to playlist, queue, archive, or delete. Show the
  selected count and clear confirmation for destructive actions; prefer recoverable undo.
- [ ] Add shared tags, then source/type/read-state/tag filters and improved text search.
  Keep tags as flexible labels and playlists as ordered collections.
- [ ] Extension: right-click Save link, with explicit user action and narrow permissions.
- [ ] Extension: save selected text as a separate reading item with source URL, title,
  and selection provenance; distinguish excerpt capture from full-article import.

Gate: playlist creation handles name collisions; bulk actions respect item identity;
tags/search synchronize; repeated captures are predictable; no passive browsing capture.
Dependencies: tags and excerpts require Android/backend schema support before rollout.
Playlist capture and library bulk actions can ship independently of those migrations.

### Wave 5 — Listen from the website

- [ ] Validate optional browser-native speech on the actual target browser first.
  Confirm it is useful before investing in full native-voice playback parity.
- [ ] Add play/pause, sentence seeking/skips, speed, sleep timer, elapsed/remaining time,
  current-sentence highlight, Up next advancement, and supported media controls.
- [ ] Ensure only one browser tab controls active playback and publishes its progress.
- [ ] Add the first approved cloud provider through authenticated server synthesis,
  incremental chunks, prefetch/cancel, and timing aligned with the shared text.
- [ ] Add explicit provider setup/paid-use opt-in, encrypted server secrets outside
  preference sync, credential removal, concurrency limits, and usage/cost controls.

Gate: real voice/provider tests cover resume/seek, quota/auth/network failure,
background/tab behavior, queue changes, and interruption. Disclose browser limits;
never claim Android-equivalent background reliability without evidence. Provider keys
never enter shared records, logs, source control, or browser-readable API responses.

### Wave 6 — Remove dependencies and add integrations

- [ ] Newsletter capture: private forwarding address, server email-body extraction,
  explicit newsletter-to-playlist rules, durable retry/deduplication, and Needs routing.
  Imports remain in To read; capture never starts playback. Validate phone compatibility.
- [ ] Connected mailbox capture: selected newsletters/labels, delegated authorization,
  incremental checkpoints, bounded backfill, pause/revoke, and cross-method duplicates.
  Preserve mailbox read/archive/delete state. See [design](docs/NEWSLETTER-CAPTURE-DESIGN.md).

- [ ] Evaluate server public-article extraction first, then PDF/DOCX/OCR separately.
  Preserve provenance, normalized-text versions, and existing content on failure.
  Bound public network fetches against SSRF, redirects, oversized inputs, and timeouts.
- [ ] Design Raindrop two-way sync: account connection, stable IDs, collection/playlist
  mapping, tags, rate limits, duplicates, offline edits, conflicts, and unlink behavior.
  Define whether deletes archive, unlink, or remove content before enabling propagation.
- [ ] Evaluate PWA/offline reading with explicit downloads, storage quotas, clearing
  cached private content, and reconnect reconciliation.
- [ ] Evaluate explicit web MP3 export with job/progress/cancellation, retention,
  private download access, and provider costs. Keep it separate from live listening.

Gate: each integration receives its own design and isolated tests before production.
Any extra hosting resources, external-account access, or provider spend requires a
concrete review. Do not silently replace working phone extraction or sync behavior.

### Optional distribution track

- [ ] Decide personal use versus public release; do not treat this roadmap as approval.
- [ ] If public: release signing/update strategy, asset provenance, license/source
  obligations, accessibility, privacy documentation, support/recovery, and extension
  store packaging/review. Multi-user authentication requires a separate architecture;
  a shared personal pairing key is not a public account system.

## Dependency summary

Reliability → status/action cleanup → reader → cross-device progress → web audio.
Cloud audio depends on secure credential and usage controls. Tags/excerpts depend
on shared schema support. Server extraction and Raindrop are separate integration
tracks and can be reprioritized after their designs establish risks and benefits.
Small extension playlist improvements can proceed alongside web reader work once
real Chrome validation passes.

## Delivery rules and decisions to revisit

- Start with Wave 0 and Wave 1. Next, prioritize the reader/resume path and quick
  extension playlist improvements rather than opening all integration tracks at once.
- Each release has a reviewable result, focused acceptance checks, and rollback/data
  preservation plan. Back up before migrations and in-place phone installs.
- Accessibility, light/dark consistency, clear errors, and privacy are part of each
  feature's completion gate, not a final cleanup phase.
- Track implemented, deployed/installed, and device-verified separately. Update this
  roadmap with evidence; unchecked items remain planned even if prototypes exist.
- Revisit browser-native voice value, shared preference defaults, Raindrop mapping,
  retention policy, and public distribution before their respective implementations.
- No dates or new recurring costs are committed. Refine effort after each wave and
  reprioritize when actual usage or reliability findings justify it.
