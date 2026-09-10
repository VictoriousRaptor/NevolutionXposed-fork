<#
.SYNOPSIS
Collects read-only device and WeChat facts for the notification-reply work.

.DESCRIPTION
Runs read-only adb commands (getprop, pm path, dumpsys, pull) and writes the
results under .debug-artifacts/device/<timestamp>/.
It never installs, uninstalls, force-stops, clears, or otherwise changes the
device or the WeChat installation.

.EXAMPLE
.\tools\collect-device-info.ps1
.EXAMPLE
.\tools\collect-device-info.ps1 -Decompile
#>
[CmdletBinding()]
param(
    [string]   $AdbPath  = 'C:\Users\Burning\scoop\apps\android-clt\current\platform-tools\adb.exe',
    [string]   $JadxPath = 'jadx',
    [string]   $OutRoot  = (Join-Path (Split-Path -Parent $PSScriptRoot) '.debug-artifacts\device'),
    [string]   $Package  = 'com.tencent.mm',
    [switch]   $Decompile,
    [string[]] $Anchor   = @(
        'MM_AUTO_REPLY_MESSAGE',
        'key_voice_reply_text',
        'RemoteInput.getResultsFromIntent',
        'android.car.EXTENSIONS',
        'MMAutoMessageReplyReceiver'
    )
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $AdbPath)) { throw "adb not found: $AdbPath" }

$listed = & $AdbPath devices
$devices = @(
    $listed | Select-Object -Skip 1 | Where-Object { $_.Trim() } | ForEach-Object {
        $fields = $_ -split '\s+'
        [pscustomobject]@{ Serial = $fields[0]; State = $fields[1] }
    }
)
if ($devices.Count -eq 0) {
    throw 'No device attached. Connect the phone with USB debugging enabled, then authorize this computer.'
}
if ($devices.State -contains 'unauthorized') {
    throw 'The device is unauthorized. Accept the "Allow USB debugging" prompt on the phone and rerun.'
}
$ready = @($devices | Where-Object State -eq 'device')
if ($ready.Count -eq 0) { throw "No usable device found. Reported states: $(($devices.State) -join ', ')." }
if ($ready.Count -gt 1) { throw 'More than one usable device is attached; disconnect the extra devices and rerun.' }
$serial = $ready[0].Serial

function Invoke-Adb {
    param([string[]] $Command)
    $output = & $AdbPath -s $serial @Command 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($Command -join ' ') failed: $($output -join ' ')" }
    return $output
}

$out = Join-Path $OutRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
$apkDir = Join-Path $out 'apks'
New-Item -ItemType Directory -Force -Path $apkDir | Out-Null
Write-Host "Collecting from $serial into $out"

$propertyNames = @(
    'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.version.security_patch',
    'ro.build.display.id', 'ro.product.manufacturer', 'ro.product.model', 'ro.product.cpu.abi'
)
$deviceLines = foreach ($name in $propertyNames) {
    "$name=" + ((Invoke-Adb @('shell', 'getprop', $name)) -join '').Trim()
}
$deviceLines | Set-Content -LiteralPath (Join-Path $out 'device.txt') -Encoding utf8

$packageDump = Invoke-Adb @('shell', 'dumpsys', 'package', $Package)
$packageFacts = $packageDump |
    Select-String -Pattern 'versionName=|versionCode=|installerPackageName=|codePath=|primaryCpuAbi=|firstInstallTime=|lastUpdateTime=' |
    ForEach-Object { $_.Line.Trim() } | Select-Object -Unique
$packageFacts | Set-Content -LiteralPath (Join-Path $out 'wechat-package.txt') -Encoding utf8

$apkPaths = (Invoke-Adb @('shell', 'pm', 'path', $Package)) |
    ForEach-Object { ($_ -replace '^package:', '').Trim() } |
    Where-Object { $_ }
if (-not $apkPaths) { throw "No APK path returned for $Package." }
$apkPaths | Set-Content -LiteralPath (Join-Path $out 'wechat-apk-paths.txt') -Encoding utf8

$modules = (Invoke-Adb @('shell', 'pm', 'list', 'packages')) | Select-String -Pattern 'lsposed|xposed|edxposed'
$modules | ForEach-Object { $_.Line.Trim() } | Set-Content -LiteralPath (Join-Path $out 'framework-packages.txt') -Encoding utf8

$index = 0
foreach ($remote in $apkPaths) {
    $name = if ($index -eq 0) { 'base.apk' } else { "split-$index.apk" }
    $local = Join-Path $apkDir $name
    & $AdbPath -s $serial pull $remote $local | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "adb pull failed for $remote" }
    $index++
}

$hashes = Get-ChildItem -LiteralPath $apkDir -Filter '*.apk' | ForEach-Object {
    '{0}  {1}  {2}' -f (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLower(), $_.Length, $_.Name
}
$hashes | Set-Content -LiteralPath (Join-Path $out 'apk-sha256.txt') -Encoding utf8
$hashes | Write-Host

if ($Decompile) {
    $baseApk = Join-Path $apkDir 'base.apk'
    if (-not (Test-Path -LiteralPath $baseApk)) { throw 'base.apk is missing; cannot decompile.' }
    $sources = Join-Path $out 'sources'
    Write-Host 'Decompiling base.apk with JADX (this can take several minutes)...'
    & $JadxPath -d $sources --no-res $baseApk
    if ($LASTEXITCODE -ne 0) { throw "jadx failed with exit code $LASTEXITCODE" }

    $anchorFile = Join-Path $out 'anchors.txt'
    foreach ($needle in $Anchor) {
        $matches = & rg --no-heading -n -F --max-count 20 $needle $sources 2>$null
        "== $needle" | Add-Content -LiteralPath $anchorFile -Encoding utf8
        if ($matches) { $matches | Add-Content -LiteralPath $anchorFile -Encoding utf8 }
        else { '(not found)' | Add-Content -LiteralPath $anchorFile -Encoding utf8 }
    }
    Write-Host "Anchor search written to $anchorFile"
}

Write-Host "Done. Artifacts: $out"
