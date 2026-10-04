$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $cmake = Join-Path $env:LOCALAPPDATA 'Android\Sdk\cmake\3.31.0\bin\cmake.exe'
    $ninja = Join-Path $env:LOCALAPPDATA 'Android\Sdk\cmake\3.31.0\bin\ninja.exe'
    $ndk = Join-Path $env:LOCALAPPDATA 'Android\Sdk\ndk\28.2.13676358'
    $ndkBin = Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin'
    $build = Join-Path $root 'downloads\sherpa-audit-build'
    $map = (Join-Path $root 'results\sherpa-onnx-jni.map').Replace('\', '/')
    $binary = Join-Path $root 'downloads\sherpa-audit-build\lib\libsherpa-onnx-jni.so'
    $stripped = Join-Path $root 'results\libsherpa-onnx-jni-source-stripped.so'
    $buildLog = Join-Path $root 'results\sherpa-audit-build.log'

    $cmakeArgs = @(
        '-S', 'vendor\sherpa-onnx', '-B', 'downloads\sherpa-audit-build', '-G', 'Ninja',
        "-DCMAKE_MAKE_PROGRAM=$ninja",
        "-DCMAKE_TOOLCHAIN_FILE=$ndk\build\cmake\android.toolchain.cmake",
        '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_INSTALL_PREFIX=downloads\sherpa-audit-install',
        '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-21', '-DBUILD_SHARED_LIBS=ON',
        '-DSHERPA_ONNX_ENABLE_TTS=ON', '-DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF',
        '-DSHERPA_ONNX_ENABLE_BINARY=OFF', '-DSHERPA_ONNX_ENABLE_C_API=OFF',
        '-DSHERPA_ONNX_ENABLE_JNI=ON', '-DSHERPA_ONNX_ENABLE_PYTHON=OFF',
        '-DSHERPA_ONNX_ENABLE_TESTS=OFF', '-DSHERPA_ONNX_ENABLE_CHECK=OFF',
        '-DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF', '-DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF',
        '-DSHERPA_ONNX_ENABLE_RKNN=OFF', '-DSHERPA_ONNX_ENABLE_QNN=OFF',
        '-DBUILD_PIPER_PHONMIZE_EXE=OFF', '-DBUILD_PIPER_PHONMIZE_TESTS=OFF',
        '-DBUILD_ESPEAK_NG_EXE=OFF', '-DBUILD_ESPEAK_NG_TESTS=OFF',
        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-Map,$map"
    )
    & $cmake @cmakeArgs *> (Join-Path $root 'results\sherpa-audit-configure.log')
    if ($LASTEXITCODE -ne 0) { throw 'sherpa CMake configure failed; see results/sherpa-audit-configure.log' }

    & $cmake --build $build --target sherpa-onnx-jni --parallel 6 --verbose *> $buildLog
    if ($LASTEXITCODE -ne 0) { throw 'sherpa JNI build failed; see results/sherpa-audit-build.log' }

    Copy-Item -LiteralPath $binary -Destination $stripped -Force
    & (Join-Path $ndkBin 'llvm-strip.exe') --strip-debug $stripped
    if ($LASTEXITCODE -ne 0) { throw 'Could not strip debug-only sections from the audit copy' }

    $commit = (& git -C vendor\sherpa-onnx rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the checked-out sherpa commit' }
    py -3 tools\audit_sherpa_source_link.py --map $map --binary $stripped --commit $commit --output results\sherpa-source-link-closure.json --device-smoke results\sherpa-source-device-smoke.json
    if ($LASTEXITCODE -ne 0) { throw 'Could not write sherpa link-closure report' }

    $licenseOut = Join-Path $root 'results\licenses\sherpa-source-build'
    New-Item -ItemType Directory -Path $licenseOut -Force | Out-Null
    $sources = Join-Path $root 'downloads\sherpa-audit-build\_deps'
    Copy-Item -LiteralPath (Join-Path $root 'vendor\sherpa-onnx\LICENSE') -Destination (Join-Path $licenseOut 'sherpa-onnx-LICENSE') -Force
    $licenseFiles = @(
        @{ Source = 'espeak_ng-src\COPYING'; Name = 'espeak-ng-COPYING' },
        @{ Source = 'piper_phonemize-src\LICENSE.md'; Name = 'piper-phonemize-LICENSE.md' },
        @{ Source = 'kaldi_native_fbank-src\LICENSE'; Name = 'kaldi-native-fbank-LICENSE' },
        @{ Source = 'kaldi_decoder-src\LICENSE'; Name = 'kaldi-decoder-LICENSE' },
        @{ Source = 'kaldifst-src\LICENSE'; Name = 'kaldifst-LICENSE' },
        @{ Source = 'openfst-src\COPYING'; Name = 'openfst-COPYING' },
        @{ Source = 'eigen-src\COPYING.README'; Name = 'eigen-COPYING.README' },
        @{ Source = 'eigen-src\COPYING.APACHE'; Name = 'eigen-COPYING.APACHE' },
        @{ Source = 'eigen-src\COPYING.BSD'; Name = 'eigen-COPYING.BSD' },
        @{ Source = 'eigen-src\COPYING.MINPACK'; Name = 'eigen-COPYING.MINPACK' },
        @{ Source = 'eigen-src\COPYING.MPL2'; Name = 'eigen-COPYING.MPL2' },
        @{ Source = 'kissfft-src\COPYING'; Name = 'kissfft-COPYING' },
        @{ Source = 'simple-sentencepiece-src\LICENSE'; Name = 'simple-sentencepiece-LICENSE' },
        @{ Source = 'json-src\LICENSE.MIT'; Name = 'nlohmann-json-LICENSE.MIT' },
        @{ Source = 'onnxruntime-src\LICENSE'; Name = 'onnxruntime-LICENSE' },
        @{ Source = 'onnxruntime-src\ThirdPartyNotices.txt'; Name = 'onnxruntime-ThirdPartyNotices.txt' }
    )
    foreach ($file in $licenseFiles) {
        $source = Join-Path $sources $file.Source
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $licenseOut $file.Name) -Force
        }
    }
    foreach ($file in @(
        @{ Source = 'results\licenses\onnxruntime.txt'; Name = 'onnxruntime-LICENSE' },
        @{ Source = 'results\licenses\onnxruntime-thirdparty.txt'; Name = 'onnxruntime-ThirdPartyNotices.txt' }
    )) {
        $source = Join-Path $root $file.Source
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $licenseOut $file.Name) -Force
        }
    }
    Copy-Item -LiteralPath (Join-Path $ndk 'NOTICE') -Destination (Join-Path $licenseOut 'android-ndk-NOTICE') -Force
    Copy-Item -LiteralPath (Join-Path $ndk 'NOTICE.toolchain') -Destination (Join-Path $licenseOut 'android-ndk-NOTICE.toolchain') -Force
    Write-Host "Sherpa source-link audit complete: results\sherpa-source-link-closure.json"
}
finally {
    Pop-Location
}
