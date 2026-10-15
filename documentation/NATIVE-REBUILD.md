# Native build inputs and replacement route

The alpha25 candidate's repository JNI SHA256 is
`3a9902e7a88d9f568e8fdc6de876d69147dbcd667b2d94e16f1d9d13afbb7567`,
matching the prior source-link audit. The native-input archive preserves that build's sherpa checkout, extracted dependency inputs and audit manifest, excluding Git metadata.

The sherpa commit is `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf`. The audit identifies eSpeak-NG `ed530aa113046142eb5115cf2fc9157854d0ffe1` and piper-phonemize `f3ff95afc03640bc1399e113e83361192a2fafb4`. The ONNX Runtime input is its Android 1.28.2 prebuilt distribution, not ONNX Runtime source. The other *-src directories contain the extracted upstream sources used by CMake. Upstream licence files remain in those directories; APK notices are in android/app/src/main/assets/licenses.

## Rebuild the speech JNI

On Windows, install the declared Android SDK, NDK 28.2.13676358, CMake 3.31.0 and JDK 17. Extract the native-input archive into a separate directory, then run from the Yomu repository:

```powershell
./scripts/rebuild-native.ps1 -NativeInputs C:/path/to/extracted-native-inputs
```

The wrapper passes extracted source locations to CMake and builds into output/native-rebuild. It does not install an APK or overwrite the packaged JNI automatically. Generated outputs can vary by toolchain/build path; the original audit hash is provenance, not a promise of byte-identical rebuilds.

After reviewing your rebuilt library, copy output/native-rebuild/lib/libsherpa-onnx-jni.so to android/app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so, then run android/gradlew.bat with assembleDebug. Keep the separately supplied ONNX Runtime library and its notices.

## Replace or rebuild the LAME encoder

Exact LAME 3.100 sources are included under android/app/src/main/cpp/vendor/lame-3.100. android/app/src/main/cpp/CMakeLists.txt builds the encoder as a separate libmp3lame.so and links the Yomu JNI adapter dynamically. Modify or replace those sources and rebuild the APK with Gradle to relink the app. The app source and build files provide the replacement route; the LGPL notice is retained in the APK assets.

An APK you rebuild is signed with your own development certificate. Updating an existing installation requires its matching certificate; preserve app data and signing keys. This guide does not instruct a device installation or removal.

## Publication status

These files prepare concrete release materials. The application's own licence still needs an explicit choice: the current repository has no root app licence, and the existing licence audit proposes GPL-3.0-or-later for the combined native speech route. Bundled third-party code/data retain their respective terms. Do not describe this candidate as Apache/MIT-only or as device-verified.
