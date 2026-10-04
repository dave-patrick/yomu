# Yomu library and reading surfaces

## Direction contract

**THESIS:** A reading backlog is a path through material, not a wall of interchangeable cards. Keep the standard Material 3 library easy to operate, and let a quiet progress trail make saved, queued, active, and completed reading states legible without turning them into game scores.

**OWN-WORLD:** Default to deep charcoal surfaces, white text, and crisp electric blue for active actions and the trail. System, light, dark, and user-selected accent colors remain available. Use Material 3 shapes, typography, touch targets, and navigation; the signature line is a thin blue-gray rail with small state marks that brighten only for real playback or reading progress.

**STORY:** On launch, the user sees To read first and understands which items are ready, how long each should take at the default speed, and how to move one to Up next. A short tap queues; a long press starts immediately. The compact player shows the current item and its real progress. Playlists are destinations for later, not a second playback queue.

**FIRST VIEWPORT:** A Material top app bar names Yomu and provides Add. Below it, To read is the selected library destination, followed by a compact sort/filter row and a vertically scrolling list. Each list row aligns a progress checkpoint rail at the leading edge, title and source in the main column, and estimated duration plus overflow actions at the trailing edge. The first viewport ends with the compact player when playback is active; otherwise the rows use that space. Up next and Playlists stay one tap away in the standard bottom navigation. No fabricated progress is shown for unread items.

**FORM:** Trail markers, selected by the user from the presented code-first directions. This was the grounded calmer-progress candidate; it borrows no transit-map or timetable costume. The concept-seed round key is `f3019123`. Keep the signature rail subordinate to readable text and standard Material controls. Risk: progress marks can imply an unwarranted completion score, so only display persisted sentence-level progress and use an empty checkpoint for unread items.

**FINISH:** unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Surface scope

- **Library:** To read, Up next, playlists, and Read history. The lists distinguish saved state, play order, and history; one item may belong to multiple playlists while sharing one reading position.
- **Reader:** Clean text, heading context, current sentence highlight, tappable sentence seek, previous/next, speed, sleep timer, and configurable elapsed/remaining display.
- **Settings:** Provider credentials and fallback order, separate online and offline voices, searchable/filtered voice catalog, voice audition, offline recovery policy, model/storage management, theme/accent, import cleanup, and backup/restore.
- **Exports:** One MP3 per item, chapter marks only from source headings, and Android's Storage Access Framework folder picker.
