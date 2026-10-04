$ErrorActionPreference = 'Stop'
$spikeRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$cachePath = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
$jarGroups = @('net.dankito.readability4j','org.jsoup','org.jetbrains.kotlin','org.slf4j')
$jars = foreach ($group in $jarGroups) { Get-ChildItem (Join-Path $cachePath $group) -Recurse -Filter '*.jar' | Select-Object -ExpandProperty FullName }
$classPath = $jars -join ';'
& javac -encoding UTF-8 -cp $classPath (Join-Path $PSScriptRoot 'Compare.java')
if ($LASTEXITCODE -ne 0) { throw 'Host extraction compile failed' }
& java -cp ($PSScriptRoot + ';' + $classPath) Compare $spikeRoot
if ($LASTEXITCODE -ne 0) { throw 'Readability4J host extraction failed' }
& node (Join-Path $PSScriptRoot 'compare.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Mozilla host extraction failed' }
