# Deliberate library swipe actions — Alpha20

2026-10-07: ReadingItemCard uses a positional threshold of 55% of row width instead of the Material default. Action dispatch now observes settledValue rather than currentValue, so an intermediate drag state does not fire an action before the gesture has settled. The row resets after dispatch.

The shared component applies to To read, Up next, and playlists. Existing configured left/right actions, Off, and deletion confirmation remain supported. Material gesture velocity behavior remains in effect; this change does not implement a custom velocity policy.

Validation: Alpha20 debug build passed; installed in place over local Wi-Fi, versionCode 20/versionName 1.0.0-alpha20 confirmed, and launch succeeded. Short drag/cancel, deliberate swipe in both directions, fast flick, and vertical-scroll acceptance remain device checks.