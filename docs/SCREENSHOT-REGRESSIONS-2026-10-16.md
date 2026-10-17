# Screenshot regressions

- The translucent root Scaffold inherited black LocalContentColor. It now explicitly supplies the theme's onSurface color, restoring text contrast for Settings sections and the reader player status.
- Offline-model storage now measures KokoroModelInstaller.modelDirectory rather than a nonexistent files/kokoro directory.
- Audited Appearance, Voices, Defaults, Advanced, health/recovery, sync, dialogs, and menus. Primary/secondary action text now derives a readable tone from the saved accent for each theme, including glass and selected-control backgrounds. Filled buttons and accent-selection marks choose a contrasting black/white foreground. The saved custom color remains unchanged. Regression tests cover the user's #256EDB plus eight other accent colors in both themes with a 4.5:1 text-contrast floor.
- Added matching Android/backend regressions for long Beehiiv tracking URLs, parenthesized URLs wrapped across email lines, and preservation of prose following URLs. Cleanup handles genuinely wrapped destinations without consuming adjacent paragraphs.
- The reported saved issue was confirmed locally to contain 37 URLs; the cleaner removes all 37 and retains the article. Its body predates deployed cleanup. Existing saved bodies require a separate, backed-up repair with reading/listening anchor remapping; this code change does not silently rewrite the saved library.
- A later Inbox screenshot exposed a separate legacy-preview path. GET delivery preview now cleans the cached body in the response without mutating storage; Android also cleans preview text before rendering. Regression fixtures retain the Substack link labels and article prose while removing redirect URLs and square wrappers.
- Settings section headers now use a single translucent Surface with no outer shadow and explicit transparent text backgrounds. The voice summary wraps all selected-provider details instead of truncating after four rendered lines.

Backend: 40 tests passed. Android unit tests and APK assembly pass. Installation and device checks are recorded in the chat. Render deployment still requires the pending workspace confirmation.

- Settings opened from Inbox now has the correct title and shares one back-navigation handler with system Back. Light mode requests dark system-bar icons and modulates the toolbar wordmark for readable contrast.
- Validation: 41 backend tests and 43 Android unit tests pass. Preview prevention fix deployed on Render; native dark/light header captures confirm transparent backgrounds and untruncated voice details.
- All Settings disclosure headers use a 10dp corner radius for a rounded-rectangle outline.

- Email transport line wraps now stay within paragraphs; HTML blocks retain separate paragraphs. Versioned previews avoid repeated legacy repairs. Older cached previews re-extract retained source when available, otherwise join only clear lowercase continuations of long unfinished prose. Tests cover preview and save paths.
- Light surfaces use a solid dark wordmark tint in the toolbar and About. Android tests/build pass; 45 backend tests pass.

- Failed import rows now offer Open link. Article Open source uses the same floating review panel, with webpage history on Back, an external browser action, and explicit Close. The panel does not retry/cancel imports or change library/playback state. Only HTTP(S) sources are loaded; WebView file/content access is disabled and the view is destroyed when closed.

- Failed/cancelled imports now show Remove in Settings and reader tools, using the existing confirmation and recoverable deletion. Active imports retain a separate Stop import action; failed rows no longer offer the ineffective repeated Cancel action.

- Inbox supports Select issues, Select all, Clear and Assign selected with an existing-playlist picker or To read only. Ready saved deliveries can also be selected under Show all deliveries. Atomic bulk routing preserves saved article identities and memberships on retry, rolls back changed/expired selections, and never restores deleted items. Validation: 49 backend tests and Android unit tests/build pass.

- Inbox single-issue and bulk assignment now offer Automatically assign future issues, off by default and requiring a playlist. Bulk automation creates one rule per newsletter identity atomically with assignment, reuses identical active rules, and rejects conflicts without overwriting. Inbox automations shows rules with pause/resume and destination editing. Future processing remains server-side; paused/existing deliveries require explicit assignment. Backend regression suite: 51 passing tests.
