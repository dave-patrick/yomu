# HD sentence buffering and live voice changes

Android alpha17 prepares four sentences ahead for Azure Neural HD, with at most two concurrent synthesis requests. Other online voices prepare two ahead; offline mode prepares one. A shared HTTP client reuses connections. Prepared sentences enter the player in reading order, even when synthesis finishes out of order.

Changing voice or speech provider finishes the current sentence, removes queued old audio, cancels pending requests, and prepares subsequent sentences with the new selection. It also resets sticky offline fallback so a newly selected online voice can be attempted. Paused playback remains paused.

The existing foreground media session and bounded speech preparation wake leases remain in use. Sentence highlighting and article transitions retain their existing behavior.

Validation: unit regressions cover bounded concurrency, request reuse, out-of-order completion, cancellation on voice change, pruning played sentences, HTTP connection reuse, and the changed voice in Azure SSML. Full Android unit suite and debug APK build are required before installation. Audible gap length still depends on HD synthesis latency and network conditions; these changes do not guarantee gapless speech.

Connection reuse and prefetch follow [Microsoft's synthesis latency guidance](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/how-to-lower-speech-synthesis-latency).
