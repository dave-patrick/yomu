# Focused spike results

Updated October 2, 2026. **Device spike tests 1–5 are complete. The sherpa/eSpeak source-build closure is now the JNI library in the APK and passed Kokoro generation plus Media3 playback; the Azure WAV-route native-load trace is also complete. Release source/data notices and compatibility packaging remain open.** No full production UI/navigation was written.

## Status against requested tests

| Test | Executed evidence | Remaining |
|---|---|---|
| 1. Local TTS benchmark | Two 1/2/4-thread runs completed on Galaxy S26 Ultra with Kokoro-82M v0.19; RTF, CPU, sampled PSS/RSS, init time, and phonemizer data footprint recorded. The audited JNI candidate is now packaged, with the previous prebuilt C API/JNI files removed; the Galaxy generated and played a 2.05-second Kokoro clip successfully | No remaining short-run benchmark gate; prolonged low-battery and near-90-minute thermal behavior are outside these runs |
| 2. Media3 lifecycle | Background media buttons, lock-screen controls, real Buds4 Pro disconnect pause, automatic service resurrection with restored position, answered-call pause/resume, and 13-minute screen-off playback all passed. The long run paused at 13:22 when the earbuds disconnected, rather than routing audio to the phone speaker | No remaining Spike lifecycle gate; production implementation should retain the verified recovery behavior |
| 3. Extraction and documents | Desktop and Galaxy article baselines plus cleanup passed on the two fixtures. Selectable, scanned, and mixed PDFs passed on the Galaxy using platform text extraction plus Tesseract OCR. A DOCX fixture with headings, paragraphs, and a table preserved reading order in the on-device ZIP/XML prototype | Broader web extraction and real-world document fixtures remain necessary; no legacy `.doc`, malformed, or large-file acceptance is claimed. Password handling was checked on API 37 only |
| 4. Azure alignment and failover | Live East US synthesis through provider adapters: SDK returned 32 boundaries; REST listed 958 voices; sentence chunks aligned to ExoPlayer. Incremental REST playback completed all 14 sentences. Both injected failure and actual Wi-Fi+cellular loss switched to Kokoro with a spoken notice. A second actual outage run automatically resumed Azure at the next sentence boundary and completed all 14 sentences | Broader transport failures, timeout/quota errors, and service death during synthesis remain untested. REST has no word/sentence callbacks, so highlighting remains sentence-granular. F0 tier is user-configured, not independently verified |
| 5. MP3/chapter inspection | Desktop and Android LAME outputs encode for 15 minutes; ID3v2.3/v2.4 CHAP/CTOC tags round-trip; both Android-tagged files decode fully. Pocket Casts showed Chapters 1–3 for both variants and next/previous chapter navigation worked | None for this focused chapter-export test |
| 6. License BOM | 54 resolved runtime artifacts and all debug-APK native ELF dependencies inventoried; Android encoder-only LAME 3.100 has only Android system dynamic dependencies. The audited sherpa/eSpeak build now matches the JNI input in the APK; unnecessary sherpa C/C++ API libraries were removed and the Kokoro path played on-device. Kokoro v0.19 model/voice provenance and local hashes are recorded; its retained Apache-2.0 license and sherpa's deterministic voice-map script are inspected. The full 355-file eSpeak data tree hash-matches the upstream archive. An 838,291-byte English data subset (95.34% smaller, omitting eSpeak voice variants) successfully synthesized a 214-character edge-case corpus for all 11 Kokoro voices on the Galaxy. Tesseract4Android's exact native closure and notices are audited; English OCR model data is Apache-2.0. Production architecture uses REST and omits the proprietary Azure SDK | Expand pronunciation-quality review and turn the subset into a hash-pinned production asset; prepare GPL-3.0-or-later corresponding source and LGPL relinking package and notices; verify final release APK. Kokoro training-data claims remain upstream author statements. This is not release legal clearance |

For the Media3 process test, the paused position at 43,299 ms was checkpointed, the debug app process was killed without `am force-stop`, and opening Yomu recreated the service at 43,304 ms with the same URI and paused state. In a separate background-death test, Android restarted the service under a new process ID without reopening Yomu; it restored the URI and 114,593 ms position while paused. Background media-button events then resumed and paused that session. A lock-screen test showed Yomu's media card and transport controls; two taps toggled pause and resume. During the physical Buds4 Pro disconnect test, Android delivered `AUDIO_BECOMING_NOISY` at 98,519 ms and Yomu paused at 98,528 ms. Answered-call playback initially failed to resume, then passed after the service added bounded audio-focus retries; the user confirmed the final recovery test worked. See `results/call-recovery-device.jsonl`.

An October 2 screen-off playback session ran for 802.3 seconds with the same Yomu process (PID 23235) alive through successive checkpoints. The user's Galaxy Buds4 Pro were put away; Android logged LE Audio and A2DP disconnect, and Media3 reported play-when-ready reason 3 (`AUDIO_BECOMING_NOISY`) as Yomu paused at 13:22. The session did not switch audio to the phone speaker. This was the expected disconnect pause, not an unexplained playback failure. Evidence: `results/media3-long-background-device.json`.

## Build verification

- Debug APK build passed with AGP 8.9.2, Gradle 8.11.1, Kotlin 2.1.20 and SDK 36.
- APK signature verification passed.
- ZIP native-library alignment verification passed.
- Both new LAME ELF binaries have 16-KB-aligned LOAD segments, and the rebuilt APK passes `zipalign -c -P 16 -v 4`.
- ELF inspection of the final SDK 1.51.0 build found all packaged LOAD segments aligned to at least 16 KB. Static alignment checks are not a runtime compatibility test.
- An earlier SDK 1.43.0 build contained a 4-KB-aligned keyword-runtime library. Upgrading to SDK 1.51.0 eliminated that static finding.
- Phone reports SM-S948U, Android 17 / API 37, and 4096-byte memory pages. Wireless ADB connected and the diagnostic activity launched.

## PDF text extraction and scanned-page OCR spike

On the Galaxy S26 Ultra (Android 17/API 37), Android `PdfRenderer.Page.getTextContents()` extracted the selectable-text fixture (271 characters) in 5 ms in the latest run. Tesseract4Android 4.9.0 (Tesseract 5.5.1) OCR'd the image-only fixture in 162 ms of OCR time and 238 ms total page time; it recovered 263 characters from a 1836x2376 render, including the test sentences and punctuation with minor line-break variation. A two-page mixed fixture correctly used PDF text for its selectable page and OCR for its scanned page. Total probe time for these three fixtures was **767 ms**. The protected fixture rejected blank and wrong passwords with `SecurityException`, then extracted its selectable text when given the correct password. The probe uses the Apache-2.0 `tessdata_fast` English model (4,113,088 bytes; SHA-256 `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`). This small clean fixture does not establish accuracy for skewed, low-resolution, handwritten, damaged, multi-column, or very large PDFs. Android's text-content and password-load APIs are available from API 35; older supported Android versions need another licensed PDF backend for password support and would need to OCR every page for selectable text. Evidence: `results/pdf-ocr-device.json`, source PDFs under `results/pdf-fixtures/`, model under `downloads/tesseract/eng.traineddata`, and runner `tools/run_pdf_ocr_probe.py`. The exact arm64 OCR native closure was audited: four packaged shared libraries hash-match the resolved AAR, are 16 KB load-aligned, and need only Android system libraries externally. The full native license matrix and source notice texts are in `results/tesseract-native-bom.json` and `results/licenses/tesseract/`; release notices must preserve all attribution terms, including IJG's requested acknowledgment.

## DOCX paragraph and table extraction spike

A realistic 36,639-byte OOXML DOCX created with python-docx was parsed on the Galaxy with Android's `XmlPullParser` and `java.util.zip.ZipFile` in **4 ms**. It recovered all five expected paragraphs in document order, including text in both table cells, and correctly excluded repeated header/footer content. The bounded probe caps the archive at 10 MB, expanded `word/document.xml` at 20 MB, and ZIP entries at 2,048; these are prototype safeguards, not final product limits. It only reads `word/document.xml`: legacy `.doc`, footnotes, comments, revision markup, embedded-object OCR, complex field handling, and compatibility across third-party Word producers remain untested. Evidence and repeatable fixture scripts: `results/docx-device.json`, `results/docx-fixture.docx`, `tools/generate_docx_fixture.py`, and `tools/run_docx_probe.py`.

The same PDF probe checked an encrypted fixture on API 37: blank and incorrect passwords were rejected and the correct password opened the selectable-text page. This does not test protected PDFs on older Android versions or establish compatibility with all PDF encryption variants.

## Local model footprint

The initial fixture uses English Kokoro-82M **v0.19**, voice 0, rather than multilingual v1.0.

| Artifact | Bytes |
|---|---:|
| model.onnx | 345,555,491 |
| voices.bin | 5,755,904 |
| espeak-ng-data (uncompressed files) | 17,991,651 |
| libsherpa-onnx-jni.so | 4,771,760 |
| libonnxruntime.so | 22,249,560 |

Native shared-library sizes include code beyond phonemization. The APK excludes model assets and requires separate staging/download. These figures are measured artifact sizes, not CPU/RAM measurements.

The 319,625,534-byte model archive was copied to app-private storage and extracted on the phone. Host and device archive SHA-256 matched: `912804855a04745fa77a30be545b3f9a5d15c4d66db00b88cbcd4921df605ac7`.

## Galaxy local TTS benchmark

Captured October 1, 2026 on the SM-S948U (Android 17/API 37), while charging. Kokoro v0.19 voice 0 generated the same ~16.72 seconds of English audio per run. Each thread configuration had one warmup followed by three measured runs. RTF is generation wall time divided by generated audio duration; CPU is process CPU time as a percentage of one core. Memory maxima are sampled every 100 ms, so short peaks may be missed.

The provider-boundary smoke test ran October 2 with the same model and two threads. It generated a 2.06-second WAV at 24 kHz through `SherpaKokoroEspeakProvider`, confirming that the existing local route works through the new `OfflineSpeechProvider` contract. This is a wiring test, not a replacement benchmark.

| Threads | Init | Measured RTFs | Median RTF | Median CPU | Median sampled peak PSS / RSS |
|---:|---:|---|---:|---:|---:|
| 1 | 749 ms | 0.931, 1.144, 1.236 | 1.144 | 125% | 766 / 787 MiB |
| 2 | 1,077 ms | 0.688, 0.696, 0.708 | 0.696 | 229% | 766 / 782 MiB |
| 4 | 1,186 ms | 0.812, 0.809, 0.810 | 0.810 | 402% | 765 / 782 MiB |

The 2-thread configuration was fastest in this first sequential test. One thread crossed into slower-than-real-time generation on later runs; four threads consumed more CPU and were slower than two. This run proceeded in 1/2/4 order without cooling intervals, so the 1-thread slowdown may include accumulated heat. At the end, Android reported thermal status 1 and skin temperature around 40.2 C. Do not extrapolate these short measurements to long documents, low-battery performance, or an extended screen-off session. eSpeak NG phonemizer data measured 17,991,651 bytes (17.16 MiB); this is data size, not the complete native code-plus-data footprint.

### Source-built sherpa/eSpeak link-closure check

Built a fresh arm64 Android JNI candidate directly from sherpa-onnx commit `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf`, using NDK 28.2.13676358, Android API 21, TTS/JNI on, optional features off, and shared ONNX Runtime 1.28.2. The linker map records 15 static archives and 318 linked archive members with allocated sections. The stripped JNI library is 5,990,224 bytes; dynamic dependencies are Android system libraries plus `libonnxruntime.so`. The statically included eSpeak and UCD objects are GPL-3.0-or-later; other linked sources and their license texts/classifications are captured in `results/sherpa-source-link-closure.json` and `results/licenses/sherpa-source-build/`.

The candidate generated and saved a 2.05-second, 24 kHz Kokoro WAV on the Galaxy. The candidate then replaced the packaged prebuilt JNI library; the APK's merged JNI input matches the audited source build SHA-256. The APK has the unneeded C API and C++ API libraries removed. A repeat on the installed replacement APK generated the test WAV and played it through Media3: the observed player state moved from `PLAYING` at position 0 to `STOPPED` at 2,054 ms. The APK's symbol-stripped JNI is 4,631,632 bytes; the merged source input is 5,990,224 bytes. `results/sherpa-source-apk-activation.json` records the exact hashes, library inventory, dependencies, and synthesis evidence; `results/sherpa-source-device-playback.log` records playback. The original prebuilt libraries are preserved under `results/baseline-prebuilt-jniLibs-arm64-v8a/`. The source build still pulls 210 members of sherpa's broad core archive, so it is not minimized specifically for Kokoro.

### Galaxy repeat after idle cooldown

A repeat ran October 2 after an idle interval, starting at thermal status 0. It again used one warmup plus three measured runs for each thread count, in 1/2/4 order. Thermal status reached 1 during the sequential run, so this is a cool-start repeat rather than a fully cool sustained-performance test.

| Threads | Measured RTFs | Median RTF | Median CPU | Median sampled peak PSS / RSS |
|---:|---|---:|---:|---:|
| 1 | 0.958, 1.035, 1.187 | 1.035 | 125% | 765 / 848 MiB |
| 2 | 0.808, 0.824, 0.835 | 0.824 | 232% | 764 / 843 MiB |
| 4 | 0.804, 0.806, 0.856 | 0.806 | 405% | 763 / 839 MiB |

Four threads were about 2% faster than two in this repeat but used about 74% more CPU. Two threads remain the better default candidate for power efficiency unless lower generation latency is preferred. The selected three-run order still allows thermal accumulation to influence later configurations. Fresh device rows and summary: `results/kokoro-repeat-device.json`; capture utility: `tools/capture_kokoro_repeat.py`.

## Article extraction

Same saved HTML snapshots. Desktop host baseline: 10 measured runs after warmup. Phone: 5 measured runs after warmup. Android browser-side uses Mozilla Readability 0.6.0 in WebView; JVM side uses Readability4J 1.0.8. Mozilla timing encloses synchronous parsing in an already-running WebView and excludes cross-thread/UI/DOM setup; Readability4J timing encloses construction and parse. Treat time as indicative; the comparison does not isolate identical setup costs.

| Fixture | Mozilla median / characters | Readability4J median / characters |
|---|---:|---:|
| TWZ | 23.78 ms / 9,358 | 23.97 ms / 9,369 |
| 9to5Google | 10.83 ms / 2,015 | 10.79 ms / 1,961 |

| Phone fixture | Mozilla WebView median / characters | Readability4J median / characters |
|---|---:|---:|
| TWZ | 3.60 ms / 9,358 | 59.10 ms / 9,369 |
| 9to5Google | 1.50 ms / 2,015 | 21.96 ms / 1,961 |

Both outputs begin with the main article text. In the raw text, Mozilla omitted a visible “Advertisement” marker from the 9to5Google fixture, while both parsers retained author social links and the FTC affiliate disclosure near the end. The Spike now tests a conservative post-extraction cleanup pass below. This remains a spot check, not complete extraction-quality validation. Device text snapshots are in `results/device-*-*.txt`. Detailed timings are in `results/device-results.json`, with raw events in `results/device-evidence.jsonl`. Measure matching end-to-end setup costs before making a final choice.

## Article cleanup fixture check

Added a conservative Kotlin post-extraction cleaner and ran the Android fixture action again on the Galaxy. It handles both Mozilla WebView and Readability4J text output, including Readability4J's collapsed TWZ newsletter prefix. It removes the captured related-story section, author/contact and social-follow tails, FTC disclosure, ad marker, and the copied Boeing social post. It preserves the main article opening, tested article quotations, and the F-47 image caption. The 9to5Google headings and body remain. The cleaner leaves general image captions untouched and removes boilerplate only for patterns it recognizes.

| Engine / fixture | Raw chars | Clean chars | Result |
|---|---:|---:|---|
| Mozilla WebView / TWZ | 9,358 | 8,885 | Passed retained-body/caption and removed-social/contact checks |
| Readability4J / TWZ | 9,369 | 8,858 | Passed retained-body/caption and removed-newsletter/social/contact checks |
| Mozilla WebView / 9to5Google | 2,015 | 1,611 | Passed body/headings retained; related stories, follow links, ad and affiliate footer removed |
| Readability4J / 9to5Google | 1,961 | 1,601 | Passed body/headings retained; related stories, follow links, ad and affiliate footer removed |

This is a marker-based acceptance check on two saved article snapshots, not a claim that every publisher's boilerplate will be detected. The assertion output is `results/extraction-cleanup-verification.json`; cleaned Galaxy text is in `results/clean-*-clean.txt`.

## Azure boundary alignment

Live synthesis ran on the Galaxy S26 Ultra using Azure Speech SDK 1.51.0, the East US region, and `en-GB-SoniaNeural`. The probe captured 32 word, punctuation, and sentence boundary callbacks for a 10.6-second WAV. The configured tier was labeled F0 by the supplied local credential metadata; the SDK does not independently verify subscription tier.

Azure emitted sentence-boundary callbacks after later word callbacks, so the first playback trace incorrectly processed callback-arrival order as playback order. The spike now sorts by `audio_ms` before comparing with ExoPlayer. On the corrected uninterrupted 1x run, all 32 boundaries were observed in order; polling lateness was 11 ms median and 19.5 ms at the 95th percentile. This measures ExoPlayer's content clock at a 20 ms polling interval, not speaker/acoustic latency. A follow-up covers pause/resume and variable-speed alignment below. Raw callbacks are in `results/azure-boundaries-device.json`; the clean corrected playback trace is `results/azure-playback-trace.jsonl`. The credential file was removed from app-private device storage after the test.

A follow-up trace paused at 592 ms for about 2.15 seconds, resumed at 594 ms, then changed to 1.5x and back to 1x. During the 1.5x interval, 22 boundaries had 19 ms median and 26.5 ms 95th-percentile poll lateness (28.5 ms maximum). Nine boundaries at 1x after the speed transition included one 51 ms poll delay. The trace is one short fixture run, not a long-duration stability test. See `results/azure-playback-pause-speed-trace.jsonl` and `results/azure-pause-speed-device.jsonl`.

## Azure REST comparison

October 2, 2026 on the same Galaxy and East US resource. The provider adapters passed on-device. The Azure composite's REST voice catalog returned 958 voices and confirmed `en-GB-SoniaNeural`; SDK synthesis returned 32 boundaries in a 510,476-byte WAV. A separate REST synthesis call produced a 324,644-byte WAV, taking 760.69 ms for voice listing and 568.37 ms for synthesis on that repeat. REST exposed no word/sentence boundary callback or usable alignment metadata. Its adapter advertises catalog and synthesis; the SDK adapter advertises synthesis and word/sentence timing. REST cannot drive synchronized sentence highlighting by itself. Credential-free evidence is in `results/azure-composite-device.json` and `results/adapter-smoke-device.json`; the temporary key file was removed from the phone.

### REST-only sentence-chunk alignment prototype

To test a REST-only path without SDK boundary callbacks, the Spike split a fixed three-sentence fixture with English sentence segmentation, synthesized each sentence separately through `AzureRestSpeechProvider`, and concatenated the returned 24 kHz mono PCM into one playable WAV. Sentence start/end positions come from exact PCM frame counts. A single-request WAV of the same text was generated for comparison. The account's Azure key was staged through ADB into app-private storage only for this run and removed automatically afterward.

| Route | REST requests | Synthesis wall time | Audio duration |
|---|---:|---:|---:|
| Whole text in one request | 1 | 1,606 ms | 12,325 ms |
| Sentence chunks | 3 | 1,357 ms total | 12,425 ms |

The combined WAV was valid PCM, and its 12,425 ms measured duration exactly matched the final sentence-offset map. On-device ExoPlayer playback advanced the diagnostic sentence highlight at all three chunk boundaries; lateness against the player content clock was 12 ms median and 13.5 ms maximum at a 20 ms poll interval. This establishes that sentence-level timing can be derived without the SDK for chunked synthesis. It does **not** establish natural prosody across independently synthesized sentences, user-perceived highlight quality, request latency for long articles, or pause/speed-change behavior. The probe pre-generates and joins the audio before playback; it does not yet stream chunks as they arrive. Audio samples and full trace: `results/azure-rest-single.wav`, `results/azure-rest-chunks.wav`, `results/azure-rest-sentence-chunks-device.json`. Reproduce with `tools/run_azure_rest_chunk_probe.py` and `tools/capture_azure_rest_chunk_trace.py`.

### Longer article sentence-chunk run

Ran the cleaned 9to5Google Facer article through the same Galaxy / East US REST-only path using `en-GB-SoniaNeural`. The 1,611-character fixture became 14 sentence requests. The combined 24 kHz mono PCM WAV is 5,056,004 bytes and 105,332.5 ms (1:45.3); synthesis took 9,575.5 ms total. The measured WAV duration matches the final sentence offset, with no gaps or overlaps in the offset map. On-device ExoPlayer playback visited all 14 sentence boundaries. At a 20 ms polling interval, highlight lateness was 8.5 ms median and 19.5 ms maximum against the player clock.

This confirms the offset approach remains aligned across a realistic article-length sample. It also makes the tradeoff visible: one REST request per sentence, independent prosody at chunk joins, and a complete WAV generated before playback. The extracted headings produce a couple of short text chunks (for example, “Facer explains:”); extraction/cleanup quality and speech naturalness still need separate listening review. This run does not measure acoustic output latency. Evidence/audio: `results/azure-rest-long-article-device.json` and `results/azure-rest-long-article.wav`. The report includes each text chunk, start/end offsets, and the complete player trace. Collection uses `tools/collect_azure_rest_long_article.py`; the diagnostic action is **Generate longer article REST chunks**, then **Play longer article + sentence timing**.

### Incremental REST playback prototype

The Galaxy generated and queued the first two sentence WAVs, started Media3, then synthesized and appended the remaining 12 sentences while playback continued. The first chunk was ready after 1.66 seconds; the two-sentence playable head was queued after **2.82 seconds**. Full article synthesis took **20.05 seconds**, while the generated speech lasts **105.33 seconds**. The app used temporary files in its private cache for Media3 playback and removes each prior chunk after advancing, then clears the chunk cache when playback ends. All 14 item transitions were observed and playback ended normally. Comparing wall-clock transition intervals with each previous WAV duration gives 0.5 ms median estimated extra gap and 354 ms maximum; this is an approximate scheduling metric, not an acoustic silence measurement. The user listened to the full incremental run and reported that it sounded good. Evidence: `results/azure-rest-incremental-device.json`, `results/azure-rest-incremental-playback.jsonl`.

### Automatic cloud recovery after real network loss

With the policy set to **switch back at a sentence boundary**, the phone lost DNS while synthesizing sentence 5. Kokoro generated that sentence; after connectivity returned, the app probed Azure at the next boundary (index 6), successfully synthesized with Azure, and used Azure for the remaining sentences. All 14 sentence transitions completed and Media3 reported normal end. The article had a 1.59-second two-sentence playable head. Estimated scheduling deviation at sentence boundaries ranged from -6.5 to 332 ms (median -0.125 ms); this is transition-callback timing minus audio duration, not acoustic silence. Actual provider event sequence and complete playback trace are in `results/azure-rest-auto-recovery-device.json` and `results/azure-rest-auto-recovery-playback.jsonl`; run with `tools/run_azure_rest_incremental_probe.py --auto-switch-back` and collect with `tools/collect_azure_rest_incremental_trace.py --auto-recovery --serial <device serial>`. This proves one actual DNS outage/recovery path on the target phone, not repeated radio conditions or timeout/quota recovery. The per-item remain-offline policy is separately tested.

### Injected provider failover during incremental playback

A diagnostic control injected an `IOException` immediately before sentence index 3, after three Azure REST sentences had been synthesized. Media3 had a two-sentence head and began playback in **1.87 seconds**. Yomu synthesized a local spoken notice (“Online speech is unavailable. Continuing with the offline voice.”), switched the rest of this item to `sherpa-onnx-kokoro-v0.19`, and did not switch back during the item. All 14 sentence transitions completed and playback ended normally. Article speech generation took **46.75 seconds** for **96.36 seconds** of sentence audio (the announcement is additional); the user's feedback was “that worked great!” Announcement audio duration is included when estimating the failover boundary, giving -4.7 ms minimum, -0.5 ms median, and 345 ms maximum estimated extra scheduling gap. This is callback timing minus WAV duration, not an acoustic silence measurement. The failure was injected in-app. Evidence: `results/azure-rest-failover-device.json`, `results/azure-rest-failover-playback.jsonl`; run with `tools/run_azure_rest_incremental_probe.py --simulate-outage` and collect using `tools/collect_azure_rest_incremental_trace.py --failover`.

### Actual network loss during incremental playback

On the Galaxy S26 Ultra, Wi-Fi and mobile data were both disabled over the USB ADB connection while sentence index 2 was ready and further REST chunks were being synthesized. Five sentences completed through Azure REST; the next REST request failed with `UnknownHostException`. Yomu generated and inserted the same local Kokoro voice-transition notice, used Kokoro for the remaining nine sentences, and completed all 14 transitions. Connectivity was restored as soon as the current-run failover event appeared; under the remain-offline policy, the item stayed on Kokoro through completion. Two-sentence prebuffer reached playback in **1.34 seconds**; generation took **48.13 seconds** for **97.53 seconds** of sentence audio (announcement is additional). Estimated boundary timing, including the announcement, was -3.8 ms minimum, 0.3 ms median, and 300 ms maximum extra scheduling time; this is callback timing minus WAV duration, not acoustic silence measurement. This verifies an actual no-network DNS failure, local fallback, and completion. Device Wi-Fi and mobile data were restored to their original enabled state. Evidence: `results/azure-rest-real-outage-device.json`, `results/azure-rest-real-outage-playback.jsonl`, and runner log `results/azure-rest-real-outage-runner.log`.

### Azure SDK native-library route trace

Captured unique `.so` mappings from `/proc/self/maps` in the app process before SDK initialization, after `SpeechSynthesizer` creation, and after speech synthesis while the synthesizer remained open. `en-GB-SoniaNeural` produced a 285,836-byte PCM WAV. During the live synthesis phase, no mapped path referenced the Speech SDK native core/bindings, codec extension, or GStreamer. This resolves the specific `libgstreamer_android.so` missing-dependency concern for the tested WAV-output route. It does not prove the libraries remain unused for microphone, compressed-audio, embedded, or other SDK features, and does not resolve the proprietary AAR license/GPL distribution compatibility. Evidence and the repeatable secure runner are `results/azure-sdk-native-load-device.json` and `tools/run_azure_sdk_native_trace.py`; temporary app-private credentials were removed after the run.

## Call-interruption follow-up

The first incoming-call test showed One UI silencing audio while ExoPlayer continued advancing. The **Enable call-pause detection** fallback now pauses on RINGING/OFFHOOK and resumes at the saved point after IDLE. On the Galaxy, the first audio-focus retry failed but the second resumed audible playback and remained stable for at least 1.5 seconds; the user confirmed it worked. The opt-in listener reads call state only. Filtered device trace: `results/call-recovery-device.jsonl`.

## MP3 evidence

- Actual Kokoro speech seed generated on Windows, repeated to form a 15-minute compatibility fixture.
- Encoded through FFmpeg's **libmp3lame**, 96 kbps mono.
- ID3v2.3 and v2.4 variants contain three CHAP entries at 0, 300, and 600 seconds, ending at 900 seconds, plus ordered/top-level CTOC.
- Independent tag parse verified versions, chapter times/titles and table-of-contents links.
- Each complete MP3 decoded without errors; measured duration is 900.048 seconds due to encoded framing.
- Files: `results/chapters-v23.mp3`, `results/chapters-v24.mp3`.
- Detailed inspection and SHA-256: `results/mp3-inspection.json`.
- Pocket Casts imported both Android-generated ID3v2.3 and v2.4 files, displayed Chapters 1, 2, and 3 for each, and next/previous chapter navigation worked. They play the same spoken content as expected.

### Android native encoder follow-up

- Cross-built upstream LAME **3.100** from the checksum-verified 1,524,133-byte source archive (`ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e`) with NDK 28.2.13676358; this is the Spike test source, not a final release-version selection.
- The Android CMake target compiles the encoder-only `libmp3lame.so` plus a JNI wrapper. `HAVE_MPGLIB` is disabled; no mpglib decoder implementation is built. `libmp3lame.so` depends dynamically only on Android `libm`, `libdl`, and `libc`; the JNI wrapper also loads Android logging. The packaged APK launched and loaded the library on the Galaxy.
- Phone test: SM-S948U / Android 17 / API 37; mono 24 kHz PCM, 96 kbps CBR, 900 seconds; encode time 1,460.8 ms; base output 10,800,864 bytes (10.30 MiB); measured duration 900.072 seconds. This speed result is a single run.
- `results/lame-android-inspection.json` records the Android-base SHA-256, v2.3/v2.4 hashes, parsed CHAP/CTOC metadata, and successful full FFmpeg decode. Both tagged files contain three five-minute chapters; Pocket Casts displayed all three chapters for both and next/previous chapter navigation worked. Copies remain staged in device Downloads as `yomu-android-chapters-v23.mp3` and `yomu-android-chapters-v24.mp3`.
- Encoder source is LGPL-2.0-or-later; retain source/build instructions and notices and meet the library's relinking/replacement requirements. This is a Spike implementation, not a release compliance opinion.

## Required next inputs and gates

The opt-in phone-state monitor paused an answered call and resumed after it ended. Android immediately revoked the first resume request; the service retried and playback stayed active for at least 1.5 seconds. The user confirmed audible resumption. Azure credentials were removed from app-private storage after testing. Pocket Casts displayed all three chapters for both Android-generated variants and next/previous navigation worked.

Full UI/navigation work remains gated until the requested device results and license decisions are resolved. No desktop measurement is substituted for those results.
