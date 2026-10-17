# Screenshot regressions

- The translucent root Scaffold inherited black LocalContentColor. It now explicitly supplies the theme's onSurface color, restoring text contrast for Settings sections and the reader player status.
- Offline-model storage now measures KokoroModelInstaller.modelDirectory rather than a nonexistent files/kokoro directory.
- Audited Appearance, Voices, Defaults, Advanced, health/recovery, sync, dialogs, and menus. Primary/secondary action text now derives a readable tone from the saved accent for each theme, including glass and selected-control backgrounds. Filled buttons and accent-selection marks choose a contrasting black/white foreground. The saved custom color remains unchanged. Regression tests cover the user's #256EDB plus eight other accent colors in both themes with a 4.5:1 text-contrast floor.
- Added matching Android/backend regressions for long Beehiiv tracking URLs, parenthesized URLs wrapped across email lines, and preservation of prose following URLs. Cleanup handles genuinely wrapped destinations without consuming adjacent paragraphs.
- The reported saved issue was confirmed locally to contain 37 URLs; the cleaner removes all 37 and retains the article. Its body predates deployed cleanup. Existing saved bodies require a separate, backed-up repair with reading/listening anchor remapping; this code change does not silently rewrite the saved library.

Backend: 40 tests passed. Android unit tests and APK assembly pass. Installation and device checks are recorded in the chat. Render deployment still requires the pending workspace confirmation.
