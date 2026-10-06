# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Delegated and accepted: native Kotlin Android app with Jetpack Compose and Material 3, persistent local storage, and Media3 playback. The current production app is a separate Gradle project under `android/`; the diagnostic Spike remains under `spike/`.

## Users

The primary user is the owner of the Yomu phone, who collects public web articles and personal documents on an Android phone and listens while they do not have time to read on screen.

## Product Purpose

Yomu saves shared URLs and documents, extracts readable text, and reads that content aloud with the current sentence highlighted. It lets the user collect material into To read, organize it for later, and keep a separate Up next queue for automatic playback.

## Positioning

Yomu turns the user's existing reading backlog into an on-device, resumable listening library. It supports high-quality user-configured online voices when available and a separately selected local voice when offline, without relying on Google or Samsung system TTS.

## Operating Context

- Native Android app for the user's Samsung Galaxy S26 Ultra running Android 17 / API 37; the installed development SDK currently builds against API 36.
- Receives public URLs, PDFs, and Word files through Android sharing; also provides URL entry and document picking.
- Main active reading language is English. Voice accent/style preferences vary; Australian, New Zealand, British, Scottish, and Irish accents are especially welcome, with American also acceptable. The user may prefer male or female voices and dislikes youthful, exuberant defaults.
- The user listens for under 30 minutes to well over 90 minutes on different days, so a finite online free quota cannot be the only speech path.
- Initial delivery is a downloadable APK for sideloading. Whether to prepare a public release as well is undecided.

## Capabilities and Constraints

- Shared items are saved to To read and never start playback automatically.
- To read, custom playlists, Up next (which auto-advances), and Read history are distinct. Items may belong to multiple playlists with one shared reading position. Short tap enqueues; long press plays immediately. Finished items move to history. Individual history entries and all history can be removed; deleting a saved item is a separate action.
- V1 supports public article extraction, selectable/scanned/mixed PDFs with local OCR where needed, password prompts for protected PDFs on Android 15+, and DOCX. Legacy binary `.doc` files are not supported. Repeated PDF page margins are skipped when reliably repeated; a preference controls article/Word footnotes and the PDF page-bottom footnote heuristic. Scanned text is read as extracted; text correction is deferred. No login or paywall bypass.
- Reading view has sentence highlighting and sentence-tap seeking; playback supports speed, previous/next, sleep timer, remaining/elapsed/duration display preferences, background playback, lock-screen and headset controls, audio focus/call handling, and persisted progress.
- Online provider choices and fallbacks are user-configurable. Provider voice catalogs should expose account-available voices and audition controls. Optional paid providers require user opt-in; free allowances and limits must be shown honestly.
- Local Kokoro/eSpeak-NG provides offline speech. eSpeak-NG is GPL-3.0-or-later; the user accepts GPL components. Online-to-offline transition uses a locally generated spoken notice. Recovery may remain offline through the item or return to the online provider at a sentence boundary.
- Items export individually as one MP3 to a user-selected folder, with sequential ID3 chapter marks at approximately 1,000-word boundaries. Pocket Casts manual local-file import is the initial integration.
- The offline library remains on the phone with manual backup/restore. Optional web sync is now being added at the owner's request: a private Render-hosted web library, document upload, and browser extension, with two-way library/progress synchronization. See `web/README.md` for pairing, scope, deployment cost, and validation limits. OPML is supplemental; private podcast hosting/folder automation and extracted-text editing remain outside this change.
- Theme offers system/light/dark and user-chosen accent colors. The user's starting accent preference is charcoal/black/white with bright blue; Material Design 3 governs native structure and interaction.
- Distribution posture remains open between personal sideload and public release. Do not claim public-release legal readiness before source, license notices, signing, and asset provenance are prepared.

## Evidence on Hand

- `YOMU-PRE-DEVELOPMENT-PACKAGE.md` contains the approved V1 scope, architecture, proposed libraries, and build milestones.
- `spike/SPIKE-RESULTS.md` records actual Galaxy testing of the Spike's Media3 lifecycle, call recovery, URL extraction fixtures, PDF OCR, DOCX, cloud speech, offline Kokoro, failover, MP3 chapters, and Pocket Casts. This is not a substitute for production-app device QA.
- `spike/LICENSE-BOM.md` and adjacent evidence files record the native/runtime dependency and license audit. Kokoro/eSpeak model data and a reduced English subset are hash-checked; all 11 Kokoro voices passed a device synthesis regression corpus with the subset.
- The production Compose app, Room library, Media3 service, importers, provider adapters, backup, and MP3 export are implemented under `android/`. The current output is an internal arm64 debug APK. Unit and instrumented tests cover library behavior and chapter tagging, but call/audio lifecycle and real-provider flows still need production-app device QA. Do not present diagnostic Spike evidence as proof the production app has the same behavior.

## Product Principles

- Preserve a useful reading item as soon as it is shared, even when online extraction must wait.
- Make playback, estimated time, and progress understandable at a glance.
- Prefer the best configured online speech available, but provide a practical local path without system TTS.
- Keep user content and reading history on-device unless the user deliberately sends text to a chosen provider.
- Distinguish source deletion, queue removal, playlist membership, and history cleanup so organization never destroys content unexpectedly.

## Accessibility & Inclusion

- Respect Android font scaling, system Back, touch target minimums, and Material 3 accessibility semantics.
- Let the user adjust reader text size, spacing, playback speed, highlight appearance, and duration display.
- Offer light, dark, and system theme modes with accessible contrast across custom accents.
