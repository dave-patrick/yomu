# Publication validation — October 14, 2026

The branded GitHub Pages landing page, help, existing screenshots, source links and explicit alpha14 download are published. Local asset/link checks pass and the deployed homepage, help and icon returned HTTP 200. The browser preview failed to attach, so no new desktop/mobile visual sign-off is claimed.

Current source declares 1.0.0-alpha25. Local `assembleDebug` completed successfully with JDK 17 and the declared Android toolchain. No APK was installed on a device during this work.

The accompanying `testDebugUnitTest` run did not complete: its worker remained in `SentenceLookAheadTest.preparesAheadWithBoundedConcurrencyAndReusesRequests`, waiting inside `runBlocking`, for more than six minutes. The owned test worker was stopped. This is an incomplete validation run, not a passing test result or a diagnosed runtime defect.

The alpha25 APK is retained locally at `android/app/build/outputs/apk/debug/app-debug.apk` and excluded from git. It was not published as a new release. The public site deliberately points to the existing alpha14 prerelease and distinguishes it from current source.

Before publishing a newer APK, resolve the test hang and finish the existing source/relinking/notices packaging work recorded in `spike/LICENSE-BOM.md`. Versioned release notes must report what was built, tested, published, installed and device-verified separately.
