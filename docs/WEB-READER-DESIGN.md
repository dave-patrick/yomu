# Web reader and settings design

Status: the dedicated web reader shipped on 2026-10-07 with stable URLs,
selectable paragraphs, source access, browser-local typography/theme preferences,
and library return restoration. Separate reading resume and shared defaults are
implemented in the website and alpha18, with installation/device acceptance
pending. Audio remains planned. See [Resume delivery](CROSS-DEVICE-RESUME-2026-10-07.md).
The architecture below describes later audio phases as well.

## Goal

Read and eventually listen to the same Yomu library on a laptop and phone,
resume across devices, and manage appropriate preferences from either interface.
Keep To read, Up next, playlists, archive, and Read history distinct.

## Current foundation

The Render website saves URLs/files, organizes the library, and opens extracted
text in a dedicated reader. The owner has verified initial sync and changes in both
directions. Android performs article/document extraction. Website Settings now
provides browser-local Light/Dark/System appearance and roadmap access. Web audio,
automatic reading-position capture, and full reading/sync/connection settings
remain planned.
Android has configurable sync direction and interval; the website currently
refreshes every 15 seconds while visible. Speech credentials remain on the phone.

## Reader experience

Open Read into a dedicated reader rather than the current text dialog. On desktop,
use a restrained toolbar, adjustable centered text column, and a persistent bottom
playback bar once audio is available. On narrow screens, collapse secondary
controls into menus. Use the approved book/headphones icon and satin wordmark,
charcoal/light themes, and the same blue accent as the app.

- Toolbar: back to library, title/source, reader appearance, and item actions.
- Text: selectable paragraphs, configurable font/size/line spacing/column width,
  keyboard navigation, and an explicit resume action when progress exists.
- Progress: show position and completion; scrolling saves the first substantially
  visible sentence after a short debounce. Mark read is a separate explicit action.
- Audio phase: play/pause, sentence skip/seek, speed, elapsed/remaining time,
  sleep timer, voice choice, and current-sentence highlighting.
- Up next advances only after audio completion. Opening or saving content never
  starts playback. Do not mark an article finished merely because it was opened.
- Pending extraction shows a useful waiting state. The first phase still needs
  the phone for new article/PDF/DOCX extraction; server extraction is separate work.

## Settings and ownership

Add Settings to web navigation with Reading, Listening, Sync, and Connection
sections. Start with Reading, Sync, and Connection; introduce Listening when
browser audio is implemented. Keep selected values visible in collapsed summaries.

| Preference/data | Ownership | Behavior |
|---|---|---|
| Library, playlists, queue, history | Shared library | Existing record sync |
| Per-item resume position/completion/speed | Shared library | Existing progress model extended as needed |
| Reader font, size, spacing, highlight style | Shared defaults, device override | Explicit “Use library defaults” control |
| Theme and column width | Device | Laptop and phone can differ |
| Sync direction, interval, automatic/manual mode | Device | Never change another device's sync policy |
| Selected online provider/voice and default speed | Shared defaults, optional device override | Validate availability on each device; no silent substitution |
| Offline voice/model, Android export folder, permissions | Phone | Show only where supported |
| Pairing key and speech API credentials | Secret, outside preference sync | Never included in generic records or backups of shared settings |

Changing a shared default updates devices using that default; overrides persist.
Offer an explicit reset to defaults. Invalid or unavailable voices produce a
clear selection prompt. Browser sync direction affects library/settings changes
from that browser, not phone scheduling. Polling remains device-local.

## Sync and progress architecture

Extend the existing authenticated FastAPI/SQLite API; retain one Render service
and disk for the initial scope. Add versioned shared-preference records using the
same field-delta and baseline approach, with matching Android storage/migration.
Older clients ignore unknown record kinds; test this before deployment.

Anchor progress to normalized-text character offset, sentence identity, and a
text revision/hash, not pixel scroll position. Map Android and browser positions
against the same extracted text. Text changes require a best-effort remap and a
visible reset/resume choice when remapping is ambiguous.

Keep reading and listening cursors distinguishable so scrolling does not move
active audio. The most recently acknowledged explicit seek or active session
provides cross-device resume. Server revisions order accepted updates; device
timestamps are informational, not authority. Surface concurrent-session conflicts
and let the user choose the intended resume point; do not silently apply a remote
seek during playback. Only one browser tab should publish active playback progress.

Persist unsent progress/preferences locally. Retry idempotently after reconnect;
respect tombstones and one-way modes. Upload-only does not apply remote changes;
download-only does not publish browser edits. Warn before download-only replaces
edits to matching records. Manual mode does not poll or upload automatically.

## Audio architecture

Deliver two independently testable stages:

1. Browser-native speech as an optional baseline. List voices available on that
   browser and disclose differences from phone voices. Sentence-sized utterances
   provide a fallback when boundary events are unreliable. Background playback
   and screen-off behavior must be tested; do not promise phone-level reliability.
2. Azure/ElevenLabs integration for higher-quality parity. Use authenticated,
   allowlisted server synthesis endpoints with sentence/chunk scheduling,
   prefetch, cancellation, short-lived private caching, and provider error handling.
   Synchronize text/timing to sentence highlights and browser media controls.

Paid-provider web use requires explicit opt-in. Introduce dedicated secret storage
encrypted at rest with a server-held encryption key; never reuse generic record
sync or send keys to browser local storage. Users enter web credentials separately
unless they explicitly authorize transferring phone credentials. Expose clear
remove/replace controls. No provider key values in responses, logs, or repository.
Apply concurrency limits, request/text limits, and a conservative usage budget;
confirm charging behavior before enabling a paid voice. Article text goes to the
selected provider only after the user initiates online listening.

Live listening remains incremental streaming/chunked playback. MP3 generation is
an explicit export feature, not the default audio path. Shared queue edits do not
automatically move an already active player to another item.

## Delivery and acceptance

See [Roadmap](../ROADMAP.md) for phases and gates. Validate against an isolated
test library before production changes: laptop-to-phone resume, phone-to-laptop
resume, offline edits, concurrent sessions, renamed/deleted items, text revision
changes, one-way modes, and old-client compatibility. Test keyboard access, font
scaling, light/dark contrast, and narrow layouts. Audio additionally needs real
provider, pause/resume, sentence seek, quota/error, tab/background, and queue tests.

## Deferred scope and decisions

- Multi-user accounts, collaboration, and public sharing remain outside this plan.
- Full offline/PWA library and downloaded audio need a separate storage/privacy plan.
- Raindrop two-way sync remains a separate integration; this design does not implement it.
- Server extraction/OCR, web MP3 export, and complete Android settings parity are later work.
- Before audio implementation, confirm browser-native speech is useful and which paid
  provider should be first. Before shared preferences, confirm the initial shared defaults.
- No delivery-date commitment or additional hosting spend is approved by this document.
