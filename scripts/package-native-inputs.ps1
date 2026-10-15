param([Parameter(Mandatory=$true)][string]$AuditRoot)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$audit = (Resolve-Path -LiteralPath $AuditRoot).Path
$report = Get-Content (Join-Path $audit 'results/sherpa-source-link-closure.json') -Raw | ConvertFrom-Json
$jni = Join-Path $repo 'android/app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so'
if ((Get-FileHash $jni -Algorithm SHA256).Hash.ToLowerInvariant() -ne $report.output_binary.sha256) {
  throw 'The audited source report does not match the repository JNI binary.'
}
$output = Join-Path $repo 'output/release-candidate'
New-Item -ItemType Directory -Force $output | Out-Null
$inputs = @('vendor/sherpa-onnx', 'results/sherpa-source-link-closure.json')
$deps = Join-Path $audit 'downloads/sherpa-audit-build/_deps'
$inputs += Get-ChildItem -LiteralPath $deps -Directory -Filter '*-src' |
  ForEach-Object { 'downloads/sherpa-audit-build/_deps/' + $_.Name }
$archive = Join-Path $output 'Yomu-native-inputs-alpha25.tar.gz'
& tar -czf $archive --exclude=.git -C $audit @inputs
if ($LASTEXITCODE) { throw 'Native-input archive failed' }
Copy-Item (Join-Path $repo 'android/app/build/outputs/apk/debug/app-debug.apk') (Join-Path $output 'Yomu-1.0.0-alpha25-debug.apk')
$records = foreach ($item in Get-ChildItem -LiteralPath $output -File | Where-Object Extension -in '.gz','.apk') {
  "$((Get-FileHash $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($item.Name)"
}
$records | Set-Content (Join-Path $output 'SHA256SUMS.txt')
Write-Host 'Prepared audited native input archive and APK candidate. No release was published.'
