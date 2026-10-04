$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$android = Join-Path $root 'android'
$jni = Join-Path $android 'app\src\main\jniLibs\arm64-v8a\libsherpa-onnx-jni.so'
$newJni = Join-Path $root 'results\libsherpa-onnx-jni-source-stripped.so'
$backup = Join-Path $root 'results\libsherpa-onnx-jni-original-for-device-smoke.so'
$apk = Join-Path $android 'app\build\outputs\apk\debug\app-debug.apk'
$smokeApk = Join-Path $root 'results\yomu-sherpa-source-smoke.apk'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$package = 'dev.yomu.spike'
$restored = $false
$oldNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false

function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')`n$result" }
    return $result
}

try {
    Copy-Item -LiteralPath $jni -Destination $backup -Force
    Copy-Item -LiteralPath $newJni -Destination $jni -Force
    Push-Location $android
    try {
        & .\gradlew.bat :app:assembleDebug *> (Join-Path $root 'results\sherpa-source-smoke-build.log')
        if ($LASTEXITCODE -ne 0) { throw 'APK build failed; see results/sherpa-source-smoke-build.log' }
    } finally { Pop-Location }
    Copy-Item -LiteralPath $apk -Destination $smokeApk -Force
    Invoke-Adb @('install', '-r', $smokeApk) | Out-Null
    $startUptime = Invoke-Adb @('shell', 'cat', '/proc/uptime')
    $startMs = [int64]([double](($startUptime -split '\s+')[0]) * 1000)
    Invoke-Adb @('shell', 'am', 'start', '-n', "$package/.SpikeActivity") | Out-Null

    $tapped = $false
    for ($attempt = 0; $attempt -lt 15 -and -not $tapped; $attempt++) {
        Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/yomu-spike-window.xml') | Out-Null
        [xml]$ui = Invoke-Adb @('exec-out', 'cat', '/sdcard/yomu-spike-window.xml')
        foreach ($node in $ui.SelectNodes('//node')) {
            if ($node.GetAttribute('text') -ceq 'RUN LOCAL KOKORO SOURCE-AUDIT SMOKE') {
                if ($node.GetAttribute('bounds') -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
                    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
                    Invoke-Adb @('shell', 'input', 'tap', "$x", "$y") | Out-Null
                    $tapped = $true
                    break
                }
            }
        }
        if (-not $tapped) { Start-Sleep -Seconds 1 }
    }
    Invoke-Adb @('shell', 'rm', '-f', '/sdcard/yomu-spike-window.xml') | Out-Null
    if (-not $tapped) { throw 'Could not find the source-audit smoke button in the Spike UI' }

    $passed = $false
    for ($attempt = 0; $attempt -lt 36 -and -not $passed; $attempt++) {
        Start-Sleep -Seconds 2
        $raw = Invoke-Adb @('shell', 'run-as', $package, 'cat', 'files/evidence.jsonl')
        foreach ($line in $raw) {
            try { $row = $line | ConvertFrom-Json } catch { continue }
            if ($row.test -eq 'offline_provider_smoke' -and [int64]$row.uptime_ms -ge $startMs) {
                $row | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $root 'results\sherpa-source-device-smoke.json') -Encoding utf8
                $passed = $true
                break
            }
        }
    }
    if (-not $passed) { throw 'Timed out waiting for a fresh offline_provider_smoke evidence row' }
    Write-Host 'Source-built sherpa JNI smoke passed; audio was generated but not played.'
} finally {
    if (Test-Path -LiteralPath $backup) {
        Copy-Item -LiteralPath $backup -Destination $jni -Force
        $restored = $true
        Push-Location $android
        try {
            & .\gradlew.bat :app:assembleDebug *> (Join-Path $root 'results\sherpa-source-smoke-restore-build.log')
            if ($LASTEXITCODE -ne 0) { Write-Error 'Original JNI file was restored, but rebuilding the baseline APK failed.' }
        } finally { Pop-Location }
        if (Test-Path -LiteralPath $apk) {
            try { Invoke-Adb @('install', '-r', $apk) | Out-Null }
            catch { Write-Warning "Original JNI source file and APK were restored locally; phone APK reinstall failed: $_" }
        }
    }
    if (-not $restored) { Write-Warning 'No JNI replacement backup was found; no source library restoration was attempted.' }
    $PSNativeCommandUseErrorActionPreference = $oldNativeErrorPreference
}
