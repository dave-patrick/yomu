# Newsletter inbox and playlist navigation

Web commit 1cee8b6 deployed live as dep-db3uppk9v7es738g327g. Android Alpha24 built and installed in place on the Galaxy; 358826496-byte app-data backup preserved privately under ignored output/.

- Web library navigation includes Inbox and Manage playlists.
- Inbox shows awaiting-review issues first, with an All deliveries view.
- Both clients preview incoming text, save an issue into an existing playlist or To read only, dismiss, and retry failed imports.
- Web automatic routing remains opt-in and requires a playlist. Android saves single issues and uses the existing encrypted sync connection; automatic rules are managed on web.
- Web playlist deletion removes memberships while retaining saved articles. Android retains its existing confirmed Delete playlist flow.
- Move up/down persists playlist sortOrder through existing shared sync records on both clients.

Validation: 30 isolated Python backend tests, 8 web model tests, JavaScript syntax checks, 33 Android JVM tests, successful debug APK build; installed versionCode 24 verified.

Live visual and interaction acceptance remains pending: phone locked and Opera browser connector disconnected during verification. No real playlists were deleted or reordered for testing. No duplicate newsletter was imported.
