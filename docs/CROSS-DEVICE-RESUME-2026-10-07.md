# Cross-device reading resume and reader defaults

Implementation: website and Android alpha18. Website release can ship alongside
older phone versions; alpha17 and earlier ignore the new record kinds. Alpha18
installation and physical device acceptance remain pending the owner's go-ahead.

## Experience

- Website: a saved reading position offers Resume reading. Go to listening
  position is a separate explicit action. Opening an item never changes either
  position. Scrolling saves a reading cursor after a debounce and queues it locally
  during a network outage. A later successful request acknowledges that cursor.
- Phone: Reading & resume options offers Resume reading and Follow narration.
  Resuming reading disables follow without seeking active speech. Browsing with
  Follow narration off stores the visible sentence's original text offset. An
  arriving remote position never scrolls the reader automatically.
- Conflicts: keep the local version or use the synced version. Android preserves
  rejected local records in sync_state until the choice; the website preserves
  its pending data and baseline in localStorage. Conflict choices do not seek audio.
- Shared defaults: font family (Serif/Sans/Monospace), size, and spacing. Both
  readers can publish their settings or disable their local override to follow
  library defaults. Existing local settings remain overrides initially. Exact
  Android font faces are mapped explicitly to the shared families. Browser width,
  theme, provider keys, voice selection, and sync policy remain device-local.

## Storage and compatibility

Room version 5 adds reading_cursors(itemId, anchor, updatedAt, deviceLabel) and
reader_defaults(id, font, size, spacing, schemaVersion). Migration 4→5 adds tables
without changing existing listening progress. Reader defaults use id=global and
schemaVersion=1. The same existing baseline-based sync carries both new kinds;
Android upload/download-only policies remain in effect.

An anchor is one JSON string: version=1, SHA-256 of the unchanged normalizedText,
UTF-16 character offset, an 80-unit quote, and 32-unit prefix. Kotlin and JavaScript
both use UTF-16 offsets and UTF-8 fingerprints. Remapping changed text requires
exactly one prefix+quote match. Otherwise readers ask for a new place instead of
inventing a pixel-based mapping. The atomic anchor field prevents merging a hash
from one cursor with an offset from another. Server revision/conflict handling,
not client timestamps, decides accepted changes.

Listening remains in reading_progress. New Android writes use original-text
sentence offsets instead of concatenated trimmed sentence lengths. Existing
legacy listening offsets may be approximate until that phone writes a fresh
position. Separate web listening/audio and offline article downloads are deferred.
An open article can continue while offline; offline first-load/PWA is not included.

## Validation

- Android: 33 unit tests including anchor offsets, Unicode, unique remapping,
  malformed/future anchors; alpha18 and instrumentation APK build.
- Web: eight unit tests; nine backend tests including atomic cursor conflicts,
  idempotent replay, separate listening state, and version validation.
- Real FastAPI/SQLite fixture with headless Chrome: phone-like cursor to website
  resume; no auto-jump; listening cursor unchanged; offline queue/retry; deliberate
  concurrent-position choice; library defaults across reload; mobile controls.
- Migration SQL verified against all exported Room v5 table schemas with existing
  listening values preserved. Instrumented migration test added; physical Android
  run, TalkBack, and bidirectional phone acceptance remain pending installation.

Manual acceptance after install: sync both devices; scroll the phone reader with
Follow narration off and resume on the website; scroll the website, sync the phone,
then Resume reading there. Repeat with audio playing and confirm audio does not
seek. Test offline scroll/reconnect, concurrent edits, text re-extraction, and
upload-only/download-only policies with a fixture library before broader release.
