# Reader advances with playback

Reported: finishing an article starts the next queued article's speech while the
reader continues displaying the previous text.

The reader previously held a one-time item loaded by openReader/startNow, with no
connection to playback item transitions. ReaderSelection now follows playback when
the displayed reader belongs to the previous playing item. The temporary null
snapshot at completion preserves that relationship. A closed reader remains closed,
and a separately opened article remains selected. The selected item is observed
through Room so its text stays current; switching targets cancels the old observer.
Opening requests use generation checks so a delayed lookup cannot undo Close.
Compose keys the reader by item ID, resetting sentence progress and scroll state
when the article changes.

Validation: all 23 JVM tests passed, including four reader transition regressions;
the debug APK built successfully. Release identifier: 1.0.0-alpha15 (15).
Device end-to-end narration acceptance remains a manual check: finish an article
with another queued and confirm title, text, highlight, and progress change together.
