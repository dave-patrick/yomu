# Yomu focused spike

This is a diagnostics app, not production UI/navigation. Current device results, limits, and release-license gates are in [SPIKE-RESULTS.md](SPIKE-RESULTS.md) and [LICENSE-BOM.md](LICENSE-BOM.md). The proposed product architecture uses Azure REST through OkHttp; the proprietary Speech SDK is a Spike comparator, not a production dependency candidate.

## Current outputs

- Android project: `android/`
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Test status and findings: `SPIKE-RESULTS.md`
- License findings: `LICENSE-BOM.md`
- Evidence: `results/` (JSON, TSV, source-license snapshots, extracted text, MP3 fixtures)
- Call recovery verification: `results/call-recovery-device.jsonl`
- Repeat Kokoro benchmark and capture tool: `results/kokoro-repeat-device.json`, `tools/capture_kokoro_repeat.py`
- Exact upstream eSpeak data tree and tested 838 KB English subset (all 11 Kokoro voices): `results/espeak-data-asset-audit.json`, `results/english-subset-voice-regression.json`; Kokoro v0.19 source provenance and asset hashes: `results/kokoro-asset-audit.json`
- Long screen-off Media3 playback and headset-disconnect pause: `results/media3-long-background-device.json`
- ELF dynamic dependency inventory: `results/elf-needed.json` (does not disclose statically linked code)
- Android encoder output and chapter inspection: `results/lame-android-inspection.json`
- Article-cleanup fixture assertions: `results/extraction-cleanup-verification.json`
- Azure SDK native/redistribution audit: `results/azure-sdk-native-audit.json`
- Active source-built sherpa APK/device verification: `results/sherpa-source-apk-activation.json`; preserved old native libraries: `results/baseline-prebuilt-jniLibs-arm64-v8a/`
- REST-only sentence-chunk alignment prototype: `results/azure-rest-sentence-chunks-device.json`, `results/azure-rest-single.wav`, `results/azure-rest-chunks.wav`

Target-device results are recorded separately in `SPIKE-RESULTS.md`; keep host baselines distinct from Galaxy measurements.

## Pinned inputs

- sherpa-onnx v1.13.8, commit `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf`
- Kokoro-82M English **v0.19** archive, not the newer multilingual v1.0
- Mozilla Readability 0.6.0; Android WebView execution path (QuickJS not selected)
- Readability4J 1.0.8
- Media3 1.6.1
- Azure Speech SDK 1.51.0
- OkHttp 4.12.0 REST comparison client (Apache 2.0)
- `speech/` provider contracts and adapters: cloud capabilities, Azure REST, Azure SDK timing, and sherpa-onnx Kokoro/eSpeak offline generation
- REST sentence-chunk prototype: creates exact sentence offsets from concatenated PCM without the Azure Speech SDK callbacks
- Kotlin 2.1.20 / AGP 8.9.2 / Gradle 8.11.1 / compile and target SDK 36
- Host fixture tools: sherpa-onnx 1.13.8, mutagen 1.48.1, imageio-ffmpeg 0.6.0

The attached Galaxy S26 Ultra reports SM-S948U, Android 17 / API 37, with 4096-byte pages. The spike is compiled against SDK 36; a successful install confirms this build launches on that handset, not general Android 17 compatibility.

## Reproduce host preparation

From the repository root in PowerShell:

```powershell
git clone --depth 1 --branch v1.13.8 https://github.com/k2-fsa/sherpa-onnx.git spike/vendor/sherpa-onnx
# If the checkout already exists, verify its commit rather than clone over it.
Invoke-WebRequest https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-android.tar.bz2 -OutFile spike/downloads/sherpa-android.tar.bz2
Invoke-WebRequest https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2 -OutFile spike/downloads/kokoro.tar.bz2
tar -xjf spike/downloads/sherpa-android.tar.bz2 -C spike/downloads
tar -xjf spike/downloads/kokoro.tar.bz2 -C spike/downloads
Copy-Item spike/downloads/jniLibs/arm64-v8a spike/android/app/src/main/jniLibs/ -Recurse -Force
Copy-Item spike/vendor/sherpa-onnx/sherpa-onnx/kotlin-api/Tts.kt spike/android/app/src/main/java/com/k2fsa/sherpa/onnx/
Invoke-WebRequest https://raw.githubusercontent.com/mozilla/readability/0.6.0/Readability.js -OutFile spike/android/app/src/main/assets/Readability.js
Invoke-WebRequest https://www.twz.com/air/full-f-47-production-line-not-too-far-away-usaf-general -OutFile spike/android/app/src/main/assets/twz.html
Invoke-WebRequest https://9to5google.com/2026/09/30/facer-wear-os-library-update-timex/ -OutFile spike/android/app/src/main/assets/9to5google.html
spike/android/gradlew.bat -p spike/android assembleDebug
spike/android/gradlew.bat -p spike/android :app:writeSpikeBom
```

Create directories first when using a fresh checkout. Configure `android/local.properties` with the actual Android SDK path. The Gradle wrapper and Kotlin JNI binding are retained with the diagnostic project; upstream copyright remains intact. Web fixture downloads can change: retain the recorded HTML hashes to identify runs on different snapshots.

Host scripts use the bundled Codex Python runtime in this session; otherwise use a Python 3.12 environment. Install dependencies into `spike/tools/python-deps`:

```powershell
python -m pip install --target spike/tools/python-deps sherpa-onnx==1.13.8 mutagen==1.48.1 imageio-ffmpeg==0.6.0
npm.cmd ci --prefix spike/tools/extraction
spike/tools/extraction/run-host.ps1
python spike/tools/create_mp3_fixture.py
python spike/tools/audit_native.py
python spike/tools/enrich_bom.py
python spike/tools/summarize.py
```

These Python tools also require NumPy, available in the session's bundled runtime. The host FFmpeg executable is a GPL-enabled test tool; it is not packaged in Android. The Spike also builds an encoder-only LAME 3.100 shared library for arm64 and has generated a 15-minute MP3 on the Galaxy. This is a prototype using a pinned older source release; see `LICENSE-BOM.md` before treating it as a production dependency.

## Target-device execution

Enable USB or wireless debugging and accept the computer's authorization, then:

```powershell
spike/tools/prepare-device.ps1
# With multiple devices: spike/tools/prepare-device.ps1 -Serial <target serial>
```

That installs the debug app, stages the separate model in private app storage, and copies MP3s into Downloads for Pocket Casts. The model is deliberately not bundled in the APK. It is an explicitly downloaded, checksum-inventoried test artifact.

### 1. Local TTS

Tap **Benchmark Kokoro**. The app measures 1/2/4 threads, one warmup and three measured runs each, voice 0, speed 1. It records generation wall time, output duration, RTF, process CPU time, CPU percentage on a one-core basis, sampled peak PSS/RSS, and initialization time. CPU above 100% means multiple core-equivalents. Sampling overhead is included in process CPU, but sampler shutdown is excluded from the generation interval. If the diagnostic button is obscured by the status bar, launch it directly with `adb shell am start -n dev.yomu.spike/.SpikeActivity --ez runKokoroBenchmark true`.

Collect external `adb shell dumpsys meminfo dev.yomu.spike` and thermal/device context if comparing devices or sustained runs. Repeat while cool and after extended generation. The short diagnostic run alone does not establish 90-minute battery/thermal behavior. PSS/RSS peaks sampled every 100 ms can miss brief spikes.

### 2. Media service lifecycle

Play the long MP3, then record evidence for each real event:

1. Lock screen: notification/lock-screen play-pause and seek controls act on the same session.
2. Bluetooth: disconnect an actual headset while playing; verify immediate pause and no speaker playback. An injected noisy broadcast is only a supplemental test.
3. Call: arrange a real incoming call with the user; verify focus interruption and record resume behavior. Do not initiate/contact another person without explicit authorization.
4. Process loss: note position, place app in background, terminate its process using a controlled same-UID/debugging action, then reconnect through the app/controller. Verify restored URI/position and paused state within one checkpoint interval plus pending write tolerance.

`am force-stop` is a separate negative case: Android intentionally suppresses automatic restart after a force-stop. Passing restoration on reconnect does not prove autonomous OS resurrection. The current spike persists/restores state but does not implement production media-button playback resumption; that remains a separate gate if required.

### 3. Article extraction

Tap **Compare fixture extraction**. Both engines receive the same saved HTML. Six runs each include one warmup. The Mozilla WebView measurement times Readability parsing after DOM creation; JNI/UI bridge overhead and DOM creation are excluded. Readability4J timing covers its constructor/parse path, so retain scope differences when comparing results. Inspect saved extracted text against each article for omissions, ads, and ordering. Length alone is not a quality score. QuickJS is not included because the requested alternative WebView path is implemented.

The comparison action also applies `ArticleTextCleaner` and saves `*-clean.txt` outputs alongside raw text. On the two current fixtures it removes related-story and footer boilerplate and the captured copied social post while retaining checked article copy, quotations, and captions. `py -3 spike/tools/verify_cleaned_extraction.py` validates those saved Galaxy outputs. This is only a focused marker test; add more publisher fixtures before treating the cleanup rules as broadly reliable.

### 4. Azure alignment

Create a private JSON file on the device containing `key`, `region`, and `tier: "F0"`. Do not paste credentials into chat, put them in assets/source, or stage them in shared phone storage. The app reads `files/azure-credentials.json`; the F0 label is a user assertion, not a verified subscription query.

Tap **Generate Azure alignment fixture**, then **Play Azure fixture + timing trace**. The app requests sentence boundaries and stores callback-arrival wall time separately from each audio-offset timestamp. Audio offsets are converted from 100-ns ticks to ms. ExoPlayer's position drives boundary rendering; callback arrival is never treated as playback time.

For the REST comparison, tap **Generate Azure REST comparison**. This uses OkHttp to list account voices and synthesize the same English voice through the Azure REST endpoint, saving `azure-rest.wav` and a credential-free evidence row. It checks response audio and headers for timing metadata; it does not assume REST supports the SDK's live `WordBoundary` callback. Compare its response with the SDK fixture before choosing the production adapter. The probe can also be launched with `am start -n dev.yomu.spike/.SpikeActivity --ez runAzureRestProbe true` after the private credential JSON is staged.

The speech code now routes the existing diagnostics through a capability-based `CloudSpeechProvider` contract. `AzureSpeechProvider` combines REST voice discovery with SDK synthesis and word/sentence timing; it passed on-device with 958 voices and 32 boundaries. Local generation uses `OfflineSpeechProvider` with the eSpeak-backed sherpa/Kokoro adapter. sherpa currently performs its Kokoro text front-end internally, so this boundary makes the local backend replaceable but does not yet permit swapping only the phonemizer; Misaki requires verified external-token support or a different adapter. Adapter smoke runs are available through `--ez runAzureSdkProbe true`, `--ez runAzureRestProbe true`, and `--ez runKokoroProviderProbe true`.

The service keeps Media3 audio-focus handling enabled. Since One UI silenced a real incoming call without pausing the player, the diagnostic app includes an **Enable call-pause detection** button. Tapping it requests `READ_PHONE_STATE`; granting it enables call-state pause/resume. The listener uses only ringing/off-hook/idle states and never reads phone numbers or call history. Repeat a real ring and answered-call test after enabling it.

The trace polls every 20 ms and reports lateness against the player's content clock, not acoustic output latency. Azure may emit sentence callbacks after later word callbacks, so the playback probe sorts all rows by `audio_ms` before scheduling. The corrected Galaxy run observed all 32 boundaries in order with 11 ms median and 19.5 ms 95th-percentile poll lateness during uninterrupted 1x playback. Pause/resume and changed-speed drift still need separate runs. Device callback and sorted playback evidence are in `results/azure-boundaries-device.json` and `results/azure-playback-trace.jsonl`.

The **Generate longer article REST chunks** diagnostic uses the cleaned 9to5Google article staged in `files/azure-rest-long-article.txt`; it synthesizes one sentence per REST request and records exact PCM offsets. On this Galaxy run, 14 chunks formed a 105.3-second WAV in 9.58 seconds of synthesis, and all 14 player-clock highlights landed within 19.5 ms of their sentence starts. The output is pre-generated, not streamed. `py -3 spike/tools/collect_azure_rest_long_article.py --adb <adb-path>` retrieves the existing run without resynthesizing it, verifies WAV duration/offset continuity, and saves the article WAV and trace report in `results/`. See `SPIKE-RESULTS.md` for the detailed measurement and limits.

### 5. MP3 encoder and Pocket Casts

The Spike's **Generate 15-minute Android LAME MP3** button produces the untagged encoder output in app-private Music storage. The captured sample was tagged on the host with ID3v2.3 and v2.4 CHAP/CTOC entries, decoded end-to-end, and copied to Downloads as `yomu-android-chapters-v23.mp3` and `yomu-android-chapters-v24.mp3`. The user imported both into Pocket Casts and confirmed Chapters 1–3 display for each, with next/previous chapter navigation working. The audio is the same in both versions, as expected.

### Evidence collection

```powershell
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getconf PAGE_SIZE
adb shell run-as dev.yomu.spike cat files/evidence.jsonl
adb shell dumpsys meminfo dev.yomu.spike
```

Export logs as UTF-8 into `results/`; review them before sharing. Do not collect credential files. No device-dependent gate is complete until measured evidence is attached to the report.
