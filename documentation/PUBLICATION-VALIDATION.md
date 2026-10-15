# Publication validation — October 14, 2026

The branded GitHub Pages landing page, help, existing screenshots, source links and explicit alpha14 download are published. Local asset/link checks pass and the deployed homepage, help and icon returned HTTP 200. The browser preview failed to attach, so no new desktop/mobile visual sign-off is claimed.

Current source declares 1.0.0-alpha25. Local `assembleDebug` completed successfully with JDK 17 and the declared Android toolchain. No APK was installed on a device during this work.

The initial `testDebugUnitTest` run did not complete: its worker remained in `SentenceLookAheadTest.preparesAheadWithBoundedConcurrencyAndReusesRequests`, waiting inside `runBlocking`, for more than six minutes. The owned test worker was stopped.

Follow-up diagnosed a test-ordering assumption: the test released request 1 without knowing whether it held one of the two permits. Dispatchers.IO can start a different pair. The corrected test records the requests that actually started, releases one of those, and checks that the remaining request starts. All three look-ahead tests now have a 15-second JUnit timeout. The full JVM suite then passed: **33 tests across 12 suites, zero failures and zero errors**, in 40 seconds. No production playback code changed.

The initial Pages deployment failed because Pages had not yet been enabled. After configuration, the exact failed run (38656506320) was rerun and succeeded. The published homepage returned HTTP 200.

The alpha25 APK is retained locally at `android/app/build/outputs/apk/debug/app-debug.apk` and excluded from git. It was not published as a new release. The public site deliberately points to the existing alpha14 prerelease and distinguishes it from current source.

Before publishing a newer APK, finish the existing source/relinking/notices packaging work recorded in `spike/LICENSE-BOM.md`. The repository JNI SHA256 matches the prior audited source-build binary exactly: `3a9902e7a88d9f568e8fdc6de876d69147dbcd667b2d94e16f1d9d13afbb7567`. Versioned release notes must report what was built, tested, published, installed and device-verified separately.
