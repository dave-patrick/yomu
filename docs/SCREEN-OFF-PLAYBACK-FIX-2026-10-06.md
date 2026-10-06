# Screen-off playback — alpha16

The phone started playback through explicit service intents, without creating a
MediaController. The session was only returned from onGetSession; it was never
added to MediaLibraryService. Consequently Media3's notification manager did not
observe ordinary phone playback or promote it to a foreground media service.

The service now explicitly calls addSession after building the session. Media3
continues to own notification/foreground handling; no premature
startForegroundService deadline is introduced during speech synthesis.

ExoPlayer's CPU wake lock covers ready/buffering playback, but is released in
ended/idle states. Separate, bounded partial wake locks now cover item loading,
speech generation, prefetch awaits, and completion/next-item handoffs. Each lease
has a five-minute maximum and releases in finally on success, failure, or
cancellation. The display remains free to sleep. Cancellation is rethrown from
generation handlers rather than reported as a speech failure.

References: [Media3 session registration](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/MediaSessionService.java),
[ExoPlayer wake-lock lifecycle](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java).

Validation: 25 JVM tests passed and the debug APK built successfully. Alpha16 was
installed in place on the phone, preserving data. Live inspection confirmed
isForeground=true and a media transport notification. Screen-off sentence
progress is checked separately from screen-on playback. During a measured interval
the phone reported Dozing at both the start and the one-minute check; playback
remained foreground and advanced from sentence 62 of the original article to
sentence 7 of the following article. A later sample reached sentence 35, with
the display awake by that sample. The test was then paused and the display woken;
no authentication lock was bypassed. Inspection after pause found zero held
Yomu speech-preparation or ExoPlayer wake locks.
