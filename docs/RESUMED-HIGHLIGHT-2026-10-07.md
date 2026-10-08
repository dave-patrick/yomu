# Resumed narration highlight — Alpha19

2026-10-07: ReaderScreen now initializes Follow narration for listening independently of the asynchronous playback snapshot. Entering quiet reading disables follow; returning to listening enables it. Manual follow-off remains effective until the reading mode changes.

The highlighted sentence comes from the matching playback service snapshot, rather than the asynchronously persisted database progress. This lets resumed audio and the highlight use the same sentence immediately. Snapshots for another article and out-of-range sentence indexes produce no highlight. Existing automatic scrolling brings that live sentence into view when following is enabled.

Validation: Missing downloadOriginal and cleanSpeechCache methods were implemented. Alpha19 built successfully; all 33 unit tests passed. All four on-device migration tests passed, including the current 5-to-6 migration preserving text and listening/reading positions. A private phone backup was verified before installation. Alpha19 was installed in place and launched successfully over local Wi-Fi. Resumed-playback visual acceptance remains pending. Git metadata is accessible again in the existing source repository. The Alpha19 source bundle is committed for publication with its existing GitHub remote and history preserved.

Original downloads use bounded authenticated requests, preserve current text/progress, and discard unattached files if the item disappears. Cache cleanup requires no playback snapshot, touches only speech files older than 24 hours, and leaves originals and model files intact. These controls passed compilation but their transfer/cleanup runtime scenarios are not yet separately verified.

Release scope also includes the existing mobile bundle present during Alpha19 compilation: quiet reading/selectable text, source and annotation tools, tags, recovery controls, resumable document work, and capability-gated sync support. Backend compatibility tests: nine passed. These broader controls have not all completed device acceptance.
