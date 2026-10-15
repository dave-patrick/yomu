param([Parameter(Mandatory=$true)][string]$NativeInputs, [switch]$ConfigureOnly)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$inputs = (Resolve-Path -LiteralPath $NativeInputs).Path
$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$cmake = Join-Path $sdk 'cmake/3.31.0/bin/cmake.exe'
$ninja = Join-Path $sdk 'cmake/3.31.0/bin/ninja.exe'
$ndk = Join-Path $sdk 'ndk/28.2.13676358'
$build = Join-Path $repo 'output/native-rebuild'
$source = Join-Path $inputs 'vendor/sherpa-onnx'
if (!(Test-Path (Join-Path $source 'CMakeLists.txt'))) { throw 'Extract the native-input archive first.' }
$options = @('-S',$source,'-B',$build,'-G','Ninja',
  "-DCMAKE_MAKE_PROGRAM=$ninja",
  "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake",
  '-DCMAKE_BUILD_TYPE=Release','-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-21',
  '-DBUILD_SHARED_LIBS=ON','-DSHERPA_ONNX_ENABLE_TTS=ON',
  '-DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF','-DSHERPA_ONNX_ENABLE_BINARY=OFF',
  '-DSHERPA_ONNX_ENABLE_C_API=OFF','-DSHERPA_ONNX_ENABLE_JNI=ON',
  '-DSHERPA_ONNX_ENABLE_PYTHON=OFF','-DSHERPA_ONNX_ENABLE_TESTS=OFF',
  '-DSHERPA_ONNX_ENABLE_CHECK=OFF','-DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF',
  '-DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF','-DSHERPA_ONNX_ENABLE_RKNN=OFF',
  '-DSHERPA_ONNX_ENABLE_QNN=OFF','-DBUILD_PIPER_PHONMIZE_EXE=OFF',
  '-DBUILD_PIPER_PHONMIZE_TESTS=OFF','-DBUILD_ESPEAK_NG_EXE=OFF',
  '-DBUILD_ESPEAK_NG_TESTS=OFF')
Get-ChildItem (Join-Path $inputs 'downloads/sherpa-audit-build/_deps') -Directory -Filter '*-src' |
  ForEach-Object {
    $name = $_.Name.Substring(0,$_.Name.Length-4).ToUpperInvariant()
    $options += "-DFETCHCONTENT_SOURCE_DIR_$name=$($_.FullName)"
  }
& $cmake @options
if ($LASTEXITCODE) { throw 'Native configure failed' }
if ($ConfigureOnly) { Write-Host 'Native configuration validated; compilation was not requested.'; return }
& $cmake --build $build --target sherpa-onnx-jni --parallel 4
if ($LASTEXITCODE) { throw 'Native build failed' }
Write-Host "Rebuilt JNI: $build/lib/libsherpa-onnx-jni.so"
Write-Host 'No application files or installed apps were changed. See documentation/NATIVE-REBUILD.md.'
