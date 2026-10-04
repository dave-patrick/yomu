# Yomu V1 build and operating notes

## Build

From this directory on Windows, build the installable arm64 debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed for private sideloading and development; it is not a Play Store or public-release package. The build uses Kotlin, Jetpack Compose/Material 3, Room, DataStore, Media3, OkHttp, Readability4J, jsoup, Tesseract4Android, Apache Commons Compress, Apache POI HWPF, sherpa-onnx, and a dynamically replaceable LGPL LAME encoder. It does not bundle the proprietary Azure Speech SDK; Azure Speech is accessed through its REST API.

Run local tests with `.\gradlew.bat :app:testDebugUnitTest` and device tests with `.\gradlew.bat :app:connectedDebugAndroidTest` while an Android device is connected. `spike/LICENSE-BOM.md` is the detailed native and transitive-license audit; the exact license texts shipped in the APK are under `app/src/main/assets/licenses/`.

## Current V1 behavior

- Shares public URLs and supported documents into To read without autoplay. URL extraction runs as a persistent, network-constrained WorkManager job, retries transient connectivity/server failures, and recovers pending imports at app startup. HTML downloads are bounded to 16 MiB before parsing with Readability4J/jsoup; documents are copied to app-private storage and extracted locally.
- Reads selectable-text, scanned, and mixed PDFs. Scanned pages use bundled English Tesseract OCR. Password-protected PDFs can prompt for a password on Android 15 (API 35) and newer through Android's `PdfRenderer`; the password is kept only for that extraction attempt. A protected-PDF import/retry/extraction test passed on the Galaxy S26 Ultra running Android 17. Android's PDF renderer reports unsupported or incorrect PDF security with the same failure class, so Yomu cannot promise support for every encryption implementation, corrupt file, handwritten scan, or unusable image.
- Reads DOCX, legacy binary Word `.doc`, PDF, and plain text. `.doc` text extraction uses Apache POI HWPF 5.5.1 from `poi-scratchpad`, with an 80 MiB source-file limit. The importer preserves the original file and extracts paragraph text plus footnotes/endnotes when enabled. Complex layouts, embedded objects, unusual encodings, and damaged/encrypted Word files may not extract completely; the importer has not yet been exercised on the Galaxy with a `.doc` fixture.
- Keeps library content, playlists, reading progress, and preferences on the phone; API keys use Android Keystore-backed encryption and are excluded from backups. Online synthesis sends the current article/document text to the selected provider.
- Offers a Read footnotes preference for article/Word endnotes and a best-effort PDF bottom-page heuristic; OCR text is processed as extracted.
- Offers reader text scaling, compact/comfortable/relaxed line spacing, background/accent-text sentence highlights, and elapsed/remaining/total/hidden time displays. Listening time is an estimate based on saved sentence progress, document word count, and selected speed.
- The To read list omits duration estimates; Up next retains them. Reading shows a sentence-position slider, and a separate speed button opens a 0.7×–1.5× speed slider.
- Offers Automatic speech (online with offline fallback) and Offline only. Offline only bypasses Azure and ElevenLabs and uses the selected installed Kokoro voice; text stays on the device for speech. Each online provider requires the user's own API key and account. Free quotas, voice access, and paid-use rules are controlled by the provider and can change. The paid ElevenLabs tier remains blocked until the user opts in.
- Shows the Yomu book/headphones mark with the Yomu wordmark in the app bar. Settings lets the user configure swipe-right and swipe-left actions for library items.
- Downloads Kokoro on demand (about 320 MB compressed, about 350 MB installed). It is not included in the APK.
- Exports one MP3 per item with numbered ID3 chapters at roughly 1,000-word boundaries. Export reports chapter progress, can be cancelled between synthesis chunks and during destination writing, and deletes temporary or partial output on cancellation/failure. Pocket Casts imports the user's selected export file or folder using its regular file import workflow; Yomu does not publish a private podcast feed.
- Uses Media3 playback resumption to restore the last saved item and sentence when playback is requested again from Android's system media controls or a media button after the service has been terminated. On-demand speech regenerates the saved sentence; this production path compiles, but still needs a service-kill/resume test on the Galaxy.
- Advertises the Media3 session as a MediaLibraryService for Android Auto. The car-safe browse tree contains To read, Up next, and custom playlists; selecting an item starts its sentence-based TTS, and next/previous controls move by sentence. Browse requests are limited to trusted/system controllers and a verified Android Auto host. The feature and instrumentation-test APK compile; the data-query instrumentation test and Android Auto head unit/Desktop Head Unit test have not yet been run.
- Long press or the item's accessible Play now menu action starts that item immediately. When interrupting another item, Yomu persists its sentence position and resumes it when the Play now item finishes, before advancing Up next. This return flow has focused JVM state tests; service-level playback verification still needs the Galaxy.
- Launcher artwork is inset within Android's adaptive icon safe area so the book/headphone mark fits platform masks without clipping.

## Release posture

The current APK is for this user's private sideload. The native pronunciation path incorporates GPL-3.0-or-later eSpeak-NG, and the LAME encoder is LGPL-2.0-or-later. A public release would need an explicit distribution license decision, corresponding source/build materials and a tested relinking path, complete notices and model/data provenance, a release signing key, and full production-app device QA. The included Spike BOM is preliminary evidence, not legal advice or release clearance.

## Instrumented test coverage

Android instrumentation tests cover Room migrations and repository operations, protected-PDF retry, legacy `.doc` extraction, MP3 chapter tags, article URL import outcomes, and the To read / Play now UI. The current build compiles these tests, but they still need to be executed on the Galaxy over wireless ADB.
