# Playlist, newsletter, and archive changes

Implemented locally on 2026-10-16; installation and deployment are separate gates.

- Playlist toolbar More opens rename/delete/back actions instead of closing the playlist.
- Article row menus and the open reader offer confirmed Delete article using the existing recoverable deletion path. Remove from playlist remains separate.
- Android newsletter inbox, library cards, and reader show a localized Received date and time. Website cards also show it. New AgentMail intake preserves the provider timestamp when valid, falling back to intake time; existing items retain their saved timestamp.
- Newsletter save re-applies reading-text cleanup to older prepared previews. Cleanup preserves Unicode words, visible link labels, and sentence punctuation while removing URLs and symbols. Existing saved bodies are not rewritten by this change.
- Cards use translucent surfaces with a light rim and highlight over a softly tinted library background.
- Settings → Library health & recovery → Auto-archive at 98% read is off by default. It applies to subsequent phone reading/listening progress. Archive retains playlist membership, and active narration finishes normally. Completion also archives when enabled. The preference is local to this phone.

Validation: newsletter regression suite passes (27 tests); Android JVM tests and debug APK assembly pass. JavaScript syntax, whitespace checks, Herald validation, and Graft refresh completed. Device acceptance remains pending: open article from playlist; open/dismiss menu; remove membership; cancel/confirm deletion on a disposable item and restore; inspect glass in light/dark; read/listen across the threshold with setting off/on and verify completion/queue continuation. Do not use real-library deletions as test fixtures.
