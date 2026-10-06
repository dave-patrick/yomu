# First usability pass

Implemented the scoped R09–R13/R16 improvements, preserving approved branding.

Website connection controls and New playlist remain reachable on narrow screens.
Re-pairing validates the candidate key without overwriting a working connection;
errors appear in the dialog. Polling and writes run through a serialized queue;
keyed navigation and item rows remain in place, with focused/open item controls
deferred and stale connection responses rejected. Errors retain the loaded library.

Items expose Read and a More menu for organization. Unfinished imports show explicit
states and disable Read. Empty views explain their next step, and history uses the
latest completion date per item. File browsing works by keyboard, dialogs have
accessible names, navigation exposes selection, and progress uses native semantics.
Theme tokens apply consistently across library, Settings, dialogs, and roadmap;
the navigation toggle complements the Settings System/Light/Dark preference.

Android preserves tap-to-play and hold-to-queue in To read; its Play now menu now
starts playback directly. Gesture help and long-press labels match callbacks.
Sync Settings observes a shared StateFlow rather than a one-time preference read.
Successful and attempted timestamps are separate; failure keeps the last success.
Authentication, replacement-library, and validation failures stop automatic retries;
network/server temporary failures retry. Manual sync remains available after fixing
the issue. Cancellation clears busy state without reporting a successful sync.

Duplicate article capture now returns its existing ID so extension playlist
assignment can work with the real server contract.

## Validation

- Website helper tests: history order/deduplication, import/progress states,
  contextual empties, serialization and recovery after an error.
- Existing extension regression suite and seven server sync tests.
- Android JVM tests and debug APK build passed; no APK installed.
- Isolated browser fixture: desktop light, mobile dark at 390 px, light at 320 px,
  Settings, item More menu, playlist assignment, and rejected re-pairing followed
  by a successful check using the preserved connection. No page overflow at
  either tested mobile width.

Dedicated reader routing, quiet phone reading, full TalkBack/large-text acceptance,
playlist parity, and device validation remain on the roadmap.
