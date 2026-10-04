param([string]$Serial)
$ErrorActionPreference = 'Stop'
$spikeRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$adbPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$deviceArgs = @(); if ($Serial) { $deviceArgs = @('-s', $Serial) }
function Invoke-Adb { & $adbPath @deviceArgs @args; if ($LASTEXITCODE -ne 0) { throw ('ADB command failed: ' + $args[0]) } }
Invoke-Adb install -r (Join-Path $spikeRoot 'android/app/build/outputs/apk/debug/app-debug.apk')
Invoke-Adb shell run-as dev.yomu.spike mkdir -p files
# Stage only nonsecret model/test files. Azure credentials must go directly into app-private storage.
Invoke-Adb push (Join-Path $spikeRoot 'downloads/kokoro-en-v0_19') /data/local/tmp/yomu-kokoro
Invoke-Adb shell run-as dev.yomu.spike cp -r /data/local/tmp/yomu-kokoro files/kokoro-en-v0_19
foreach ($version in @('v23','v24')) {
    Invoke-Adb push (Join-Path $spikeRoot "results/chapters-$version.mp3") "/data/local/tmp/yomu-chapters-$version.mp3"
    Invoke-Adb shell run-as dev.yomu.spike cp "/data/local/tmp/yomu-chapters-$version.mp3" "files/chapters-$version.mp3"
    Invoke-Adb push (Join-Path $spikeRoot "results/chapters-$version.mp3") "/sdcard/Download/Yomu-chapters-$version.mp3"
}
Invoke-Adb shell am start -n dev.yomu.spike/.SpikeActivity
Write-Output 'Spike installed and model/fixtures staged. No tests have been marked passed.'
