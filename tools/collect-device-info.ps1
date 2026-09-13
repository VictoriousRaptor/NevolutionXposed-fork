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
.EXAMPLE
.\tools\collect-device-info.ps1 -ProbeProfile
.EXAMPLE
.\tools\collect-device-info.ps1 -ProbeProfile -RunTests
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
    ),
    [switch]   $ProbeProfile,
    [switch]   $RunTests,
    [string]   $GradleUserHome = $env:GRADLE_USER_HOME,
    [string]   $GradlePath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'gradlew.bat'),
    [string[]] $ProbeAnchor = @(
        'MMAutoMessageReplyReceiver',
        'MM_AUTO_REPLY_MESSAGE',
        'key_voice_reply_text',
        'getResultsFromIntent',
        'android.car.EXTENSIONS'
    )
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $AdbPath)) { throw "adb not found: $AdbPath" }

if ($RunTests) { $ProbeProfile = $true }
$repoRoot = Split-Path -Parent $PSScriptRoot
if ($ProbeProfile -and -not (Get-Command rg -ErrorAction SilentlyContinue)) {
    throw 'ripgrep (rg) is required by -ProbeProfile. Install it or add it to PATH.'
}

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

if ($ProbeProfile) {
    $probe = Join-Path $out 'probe'
    $dexRoot = Join-Path $probe 'dex'
    $probeSources = Join-Path $probe 'sources'
    New-Item -ItemType Directory -Force -Path $dexRoot, $probeSources | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    Write-Host 'Extracting classes*.dex from every pulled APK...'
    $dexFiles = [System.Collections.Generic.List[string]]::new()
    foreach ($apk in Get-ChildItem -LiteralPath $apkDir -Filter '*.apk' | Sort-Object Name) {
        $apkDex = Join-Path $dexRoot $apk.BaseName
        New-Item -ItemType Directory -Force -Path $apkDex | Out-Null
        $archive = [System.IO.Compression.ZipFile]::OpenRead($apk.FullName)
        try {
            foreach ($entry in $archive.Entries) {
                if ($entry.FullName -notmatch '^classes\d*\.dex$') { continue }
                $target = Join-Path $apkDex $entry.FullName
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
                $dexFiles.Add($target)
            }
        } finally { $archive.Dispose() }
    }
    if ($dexFiles.Count -eq 0) { throw "No classes*.dex found inside $apkDir" }
    Write-Host ("Extracted {0} dex file(s)." -f $dexFiles.Count)

    $anchorReport = [System.Collections.Generic.List[string]]::new()
    foreach ($needle in $ProbeAnchor) {
        $anchorReport.Add("== $needle")
        $hits = @(& rg --no-heading -l -a -F $needle $dexFiles 2>$null)
        if ($hits.Count -gt 0) {
            foreach ($hit in $hits) { $anchorReport.Add('  ' + [System.IO.Path]::GetRelativePath($out, $hit)) }
        } else {
            $anchorReport.Add('  (not found)')
        }
    }
    $anchorReport | Set-Content -LiteralPath (Join-Path $probe 'dex-anchors.txt') -Encoding utf8

    function Get-ClassSourceFile {
        param([string] $ClassName)
        $relative = ($ClassName -replace '\.', '\') + '.java'
        $target = Join-Path (Join-Path $probeSources 'sources') $relative
        if (Test-Path -LiteralPath $target) { return $target }
        $descriptor = 'L' + ($ClassName -replace '\.', '/') + ';'
        $dexHits = @(& rg --no-heading -l -a -F $descriptor $dexFiles 2>$null)
        foreach ($dex in $dexHits) {
            & $JadxPath --no-res -j 8 -d $probeSources --single-class $ClassName $dex *> $null
            if (Test-Path -LiteralPath $target) { return $target }
        }
        return $null
    }

    $packageFacts = @(Get-Content -LiteralPath (Join-Path $out 'wechat-package.txt') | Where-Object { $_.Trim() })
    $versionName = $null
    $versionCode = $null
    foreach ($line in $packageFacts) {
        if (-not $versionName -and $line -match 'versionName=(\S+)') { $versionName = $Matches[1] }
        if (-not $versionCode -and $line -match 'versionCode=(\d+)') { $versionCode = $Matches[1] }
    }

    $receiverClass = 'com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver'
    $receiverFile = Get-ClassSourceFile $receiverClass
    $gateCandidates = @()
    $helperCandidates = @()
    $receiverLines = [System.Collections.Generic.List[string]]::new()

    if (-not $receiverFile) {
        $receiverLines.Add("- $receiverClass : NOT FOUND in the pulled APKs")
    } else {
        $receiverLines.Add('- ' + $receiverClass + ' : ' + [System.IO.Path]::GetRelativePath($out, $receiverFile))
        $receiverText = Get-Content -LiteralPath $receiverFile -Raw
        $imports = @{}
        foreach ($import in [regex]::Matches($receiverText, '(?m)^\s*import\s+([A-Za-z_][\w\.]*)\s*;')) {
            $full = $import.Groups[1].Value
            $imports[($full -split '\.')[-1]] = $full
        }

        # Car-mode gates: no-argument static calls on an imported class, e.g. a.c(), a.g(), a.b().
        $noArgCalls = [ordered]@{}
        foreach ($call in [regex]::Matches($receiverText, '(?<![\w.])([A-Za-z_]\w*)\.([A-Za-z_]\w*)\(\s*\)')) {
            $short = $call.Groups[1].Value
            $method = $call.Groups[2].Value
            if (-not $imports.ContainsKey($short)) { continue }
            if (-not $noArgCalls.Contains($short)) { $noArgCalls[$short] = [System.Collections.Generic.List[string]]::new() }
            if (-not $noArgCalls[$short].Contains($method)) { $noArgCalls[$short].Add($method) }
        }
        foreach ($short in @($noArgCalls.Keys)) {
            $file = Get-ClassSourceFile $imports[$short]
            if (-not $file) { continue }
            $text = Get-Content -LiteralPath $file -Raw
            $missing = @()
            foreach ($method in $noArgCalls[$short]) {
                if ($text -notmatch ('public\s+static\s+boolean\s+' + [regex]::Escape($method) + '\s*\(\s*\)')) { $missing += $method }
            }
            $status = if ($missing.Count -eq 0) { 'valid' } else { 'missing static boolean: ' + ($missing -join ', ') }
            $receiverLines.Add(('  - candidate gate {0} ({1}): methods [{2}] -> {3}' -f $short, $imports[$short], ($noArgCalls[$short] -join ', '), $status))
            if ($missing.Count -eq 0) {
                $gateCandidates += [pscustomobject]@{
                    Short = $short; Full = $imports[$short]; Methods = @($noArgCalls[$short]); Source = $file
                }
            }
        }

        # RemoteInput helper: single-argument call returning Bundle, e.g. s1.b(intent).
        $gateShortNames = @($gateCandidates | ForEach-Object { $_.Short })
        foreach ($call in [regex]::Matches($receiverText, '(?<![\w.])([A-Za-z_]\w*)\.([A-Za-z_]\w*)\(\s*([A-Za-z_]\w*)\s*\)')) {
            $short = $call.Groups[1].Value
            $method = $call.Groups[2].Value
            if (-not $imports.ContainsKey($short)) { continue }
            if ($gateShortNames -contains $short) { continue }
            $file = Get-ClassSourceFile $imports[$short]
            if (-not $file) { continue }
            $text = Get-Content -LiteralPath $file -Raw
            if ($text -match ('public\s+static\s+(android\.os\.)?Bundle\s+' + [regex]::Escape($method) + '\s*\(\s*(android\.content\.)?Intent\b')) {
                if (-not ($helperCandidates | Where-Object { $_.Full -eq $imports[$short] })) {
                    $helperCandidates += [pscustomobject]@{
                        Short = $short; Full = $imports[$short]; Method = $method; Source = $file
                    }
                }
            }
        }
    }

    $report = [System.Collections.Generic.List[string]]::new()
    $report.Add('# WeChat reply profile probe')
    $report.Add('')
    $report.Add('- generated: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
    $report.Add('- package: ' + $Package)
    foreach ($line in $packageFacts) { $report.Add('- ' + $line) }
    $report.Add('')
    $report.Add('## APK hashes')
    foreach ($line in Get-Content -LiteralPath (Join-Path $out 'apk-sha256.txt')) { $report.Add('- ' + $line) }
    $report.Add('')
    $report.Add('## Dex anchors')
    $report.Add('```text')
    foreach ($line in $anchorReport) { $report.Add($line) }
    $report.Add('```')
    $report.Add('')
    $report.Add('## Receiver analysis')
    foreach ($line in $receiverLines) { $report.Add($line) }
    $report.Add('')
    $report.Add('## Probe result')

    $gate = if ($gateCandidates.Count -eq 1) { $gateCandidates[0] } else { $null }
    $helper = if ($helperCandidates.Count -eq 1) { $helperCandidates[0] } else { $null }
    if (-not $gate) { $report.Add(('- gate: {0} candidate(s) -> ' -f $gateCandidates.Count) + (($gateCandidates | ForEach-Object { $_.Full }) -join ', ')) }
    else { $report.Add(('- gate: {0} [{1}]' -f $gate.Full, ($gate.Methods -join ', '))) }
    if (-not $helper) { $report.Add(('- helper: {0} candidate(s) -> ' -f $helperCandidates.Count) + (($helperCandidates | ForEach-Object { $_.Full }) -join ', ')) }
    else { $report.Add(('- helper: {0}.{1}(Intent) : Bundle' -f $helper.Full, $helper.Method)) }

    if (-not $versionName -or -not $versionCode) {
        $report.Add('- proposal: NOT GENERATED (WeChat versionName/versionCode missing from the package dump)')
    } elseif (-not $gate -or -not $helper) {
        $report.Add('- proposal: NOT GENERATED (candidates are not unique or a descriptor failed validation)')
    } else {
        $stamp = Get-Date -Format 'yyyy-MM-dd'
        $constName = 'RELEASE_' + ($versionName -replace '\.', '_')
        $label = "wechat-$versionName"
        $methodLiterals = (($gate.Methods | ForEach-Object { '"' + $_ + '"' }) -join ', ')
        $constLines = @(
            "`t/** Verified against WeChat $versionName/$versionCode on $stamp. */",
            "`tprivate static final WeChatReplyProfile $constName = new WeChatReplyProfile(",
            "`t`t`t`"$label`",",
            "`t`t`tnew String[] { RECEIVER_CLASS },",
            "`t`t`tnew String[] { `"$($helper.Full)`" },",
            "`t`t`tnew String[] { `"$($gate.Full)`" },",
            "`t`t`tnew String[] { $methodLiterals });"
        )
        $mappingLine = "`t`tif (`"$versionName`".equals(versionName) || versionCode == ${versionCode}L) return $constName;"
        $proposal = [System.Collections.Generic.List[string]]::new()
        $proposal.Add('// Paste into WeChatReplyProfile.java: the constant next to the other profiles')
        $proposal.Add('// and the mapping line as the first statement of forPackage().')
        foreach ($line in $constLines) { $proposal.Add($line) }
        $proposal.Add('')
        foreach ($line in @($mappingLine)) { $proposal.Add($line) }
        $proposal | Set-Content -LiteralPath (Join-Path $probe 'profile-proposal.java') -Encoding utf8
        $report.Add('- proposal: probe/profile-proposal.java')

        $repoRel = 'src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatReplyProfile.java'
        $sourcePath = Join-Path $repoRoot $repoRel
        $existingText = if (Test-Path -LiteralPath $sourcePath) { Get-Content -LiteralPath $sourcePath -Raw } else { $null }
        if ($existingText -and $existingText -match ('private static final WeChatReplyProfile ' + [regex]::Escape($constName) + '\b')) {
            $descriptorsMatch = ($existingText -match ('new String\[\] \{ "' + [regex]::Escape($helper.Full) + '" \}')) -and
                ($existingText -match ('new String\[\] \{ "' + [regex]::Escape($gate.Full) + '" \}')) -and
                ($existingText -match ('new String\[\] \{ ' + [regex]::Escape($methodLiterals) + ' \}'))
            $verdict = if ($descriptorsMatch) { 'already present and matches the probe' } else { 'already present but DIFFERS from the probe, review manually' }
            $report.Add("- patch: skipped, $constName $verdict")
        } elseif (Test-Path -LiteralPath $sourcePath) {
            $original = @(Get-Content -LiteralPath $sourcePath)
            $constIdx = -1
            $methodIdx = -1
            for ($i = 0; $i -lt $original.Count; $i++) {
                if ($constIdx -lt 0 -and $original[$i] -match '^\s*private static final WeChatReplyProfile ') { $constIdx = $i }
                if ($methodIdx -lt 0 -and $original[$i] -match 'public static WeChatReplyProfile forPackage\(') { $methodIdx = $i }
            }
            if ($constIdx -ge 0 -and $methodIdx -ge 0) {
                $updated = [System.Collections.Generic.List[string]]::new()
                for ($i = 0; $i -lt $original.Count; $i++) {
                    if ($i -eq $constIdx) {
                        foreach ($line in $constLines) { $updated.Add($line) }
                        $updated.Add('')
                    }
                    $updated.Add($original[$i])
                    if ($i -eq $methodIdx) { $updated.Add($mappingLine) }
                }
                $patchRoot = Join-Path $probe 'patch'
                $aFile = Join-Path $patchRoot ('a/' + $repoRel)
                $bFile = Join-Path $patchRoot ('b/' + $repoRel)
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $aFile), (Split-Path -Parent $bFile) | Out-Null
                Copy-Item -LiteralPath $sourcePath -Destination $aFile -Force
                $utf8 = New-Object System.Text.UTF8Encoding($false)
                [System.IO.File]::WriteAllText($bFile, (($updated -join "`n") + "`n"), $utf8)
                $diff = @(& git -C $patchRoot diff --no-color --no-index --no-prefix -- "a/$repoRel" "b/$repoRel" 2>&1) |
                    Where-Object { $_ -notmatch '^warning: ' }
                # Patches must keep LF line endings, otherwise every context line carries a
                # trailing CR and "git apply" rejects the file.
                [System.IO.File]::WriteAllText((Join-Path $probe 'profile-proposal.patch'), (($diff -join "`n") + "`n"), $utf8)
                $report.Add('- patch: probe/profile-proposal.patch (git apply from the repo root)')
            } else {
                $report.Add('- patch: NOT GENERATED (could not locate the profile constant or forPackage() in WeChatReplyProfile.java)')
            }
        }
    }
    $report | Set-Content -LiteralPath (Join-Path $probe 'profile-report.md') -Encoding utf8
    Write-Host "Profile probe written to $(Join-Path $probe 'profile-report.md')"
}

if ($RunTests) {
    $probe = Join-Path $out 'probe'
    New-Item -ItemType Directory -Force -Path $probe | Out-Null
    if (-not $GradleUserHome) { $GradleUserHome = Join-Path $env:USERPROFILE '.gradle' }
    $previousGradleHome = $env:GRADLE_USER_HOME
    $env:GRADLE_USER_HOME = $GradleUserHome
    Push-Location $repoRoot
    try {
        Write-Host "Running $GradlePath testDebugUnitTest --offline ..."
        $testOutput = & $GradlePath testDebugUnitTest --offline --console=plain 2>&1
        $testExit = $LASTEXITCODE
    } finally {
        Pop-Location
        $env:GRADLE_USER_HOME = $previousGradleHome
    }
    $testOutput | Set-Content -LiteralPath (Join-Path $probe 'test-result.txt') -Encoding utf8
    $summary = if ($testExit -eq 0) { 'PASS' } else { "FAIL (exit $testExit)" }
    $reportPath = Join-Path $probe 'profile-report.md'
    Add-Content -LiteralPath $reportPath -Encoding utf8 -Value @('', '## Test run', "- gradlew testDebugUnitTest --offline: $summary")
    Write-Host "Unit test result: $summary (probe/test-result.txt)"
}

Write-Host "Done. Artifacts: $out"
