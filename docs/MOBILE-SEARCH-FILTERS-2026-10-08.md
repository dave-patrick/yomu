# Mobile search and filter controls — Alpha23

2026-10-08: To read and Up next use a magnifying-glass action beside Settings instead of a permanent search field. The popup filters titles, extracted text, sources, and tags; dismissing it preserves the query, and Clear removes it.

The library filter toolbar now has one filter icon. It opens a Material bottom sheet with labeled Document type, Reading status, and Sort by choices. A summary shows active filters and sort order; Reset filters clears type/status while preserving the selected sort. This shared surface also applies to playlist items.

Validation: 33 JVM tests pass and the debug APK builds. Alpha23 installed in place on the Galaxy, versionCode 23/versionName 1.0.0-alpha23 verified. Device checks confirmed search filtering, the search icon beside Settings, labeled filter choices, PDF filtering from 7 results to 0, and reset. Library/settings/original documents were backed up privately before installation. Physical TalkBack checks remain pending.
