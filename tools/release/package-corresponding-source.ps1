#requires -Version 5.1
<#
.PURPOSE
  Prepare complete corresponding source for the exact locally built R3 version.
  This archive includes the app Git revision AND the pinned native Rubber Band
  4.0.0 checkout (the latter is normally gitignored in the app repository).
  It is NOT an upload/publishing action.
.EXAMPLE
  .\tools\release\package-corresponding-source.ps1
#>
param(
    [string]$OutputDirectory = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $root "dist" }

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git must be installed to package the exact source revision."
}

$changes = @(& git -C $root status --porcelain)
if ($LASTEXITCODE -ne 0) { throw "Unable to check Git working tree." }
if ($changes.Count -gt 0) {
    throw "Uncommitted/untracked changes found. Commit your release sources first."
}

$appRevision = (& git -C $root rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $appRevision -notmatch '^[0-9a-f]{40}$') {
    throw "Unable to identify exact source revision."
}

$vendor = Join-Path $root "app\src\main\cpp\vendor\rubberband"
$expectedRubberBand = "1d95888bec3ae0a17c0c4af791810d5a63f6bc35"
if (-not (Test-Path -LiteralPath (Join-Path $vendor "single\RubberBandSingle.cpp"))) {
    throw "Rubber Band 4.0.0 source missing. Run tools\dsp\prepare-rubberband.ps1 first."
}
$vendorRevision = (& git -C $vendor rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $vendorRevision -ne $expectedRubberBand) {
    throw "The native checkout does not match the pinned Rubber Band 4.0.0 revision."
}
$vendorChanges = @(& git -C $vendor status --porcelain)
if ($LASTEXITCODE -ne 0 -or $vendorChanges.Count -gt 0) {
    throw "Rubber Band checkout has uncommitted changes. Do not silently omit them."
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$destination = (Resolve-Path -LiteralPath $OutputDirectory).Path
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ("bpm-player-source-" + [guid]::NewGuid().ToString("N"))
$stage = Join-Path $temp "source"
New-Item -ItemType Directory -Path $stage -Force | Out-Null

try {
    # Archive tracked source, including all build scripts, wrapper, licence
    # text, project assets and exact Git revision — no untracked secrets.
    $appZip = Join-Path $temp "app.zip"
    & git -C $root archive --format=zip "--output=$appZip" $appRevision
    if ($LASTEXITCODE -ne 0) { throw "Could not archive app sources." }
    Expand-Archive -LiteralPath $appZip -DestinationPath $stage -Force

    # Archive *all* native source and upstream COPYING notices, not only
    # RubberBandSingle.cpp. A plain repo ZIP without this directory is incomplete.
    $vendorZip = Join-Path $temp "rubberband.zip"
    & git -C $vendor archive --format=zip "--output=$vendorZip" $vendorRevision
    if ($LASTEXITCODE -ne 0) { throw "Could not archive Rubber Band sources." }
    $vendorDest = Join-Path $stage "app\src\main\cpp\vendor\rubberband"
    New-Item -ItemType Directory -Path $vendorDest -Force | Out-Null
    Expand-Archive -LiteralPath $vendorZip -DestinationPath $vendorDest -Force

    $sourceInfo = @"
BPM Player — Corresponding Source Release
Application source Git revision: $appRevision
Rubber Band 4.0.0 revision: $vendorRevision
Licence for BPM Player: GPL-3.0-or-later (see LICENSE)
Upstream Rubber Band licence: GPL-2.0-or-later (see vendor/rubberband/COPYING)
Build requirements: JDK 17, Android SDK/NDK, CMake and Gradle wrapper.
To test this source on an arm64 Android device:
    .\gradlew.bat :app:assembleDebug -Prubberband=true
To build a signed release with the same source revision:
    .\gradlew.bat :app:assembleRelease -Prubberband=true -PsourceRevision=$appRevision
A signed release also requires signing variables; consult
docs/10B-signed-release.md and tools/release/build-release.ps1.
No keystore, password, user music or personally identifying data
is included in this archive.
"@
    Set-Content -LiteralPath (Join-Path $stage "RELEASE_SOURCE_INFO.txt") -Value $sourceInfo -Encoding UTF8

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $filename = "BPM_Player_corresponding-source_$($appRevision.Substring(0,12)).zip"
    $output = Join-Path $destination $filename
    if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $stage, $output, [System.IO.Compression.CompressionLevel]::Optimal, $false
    )

    $digest = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash
    Write-Host "Corresponding source archive: $output"
    Write-Host "SHA-256: $digest"
    Write-Host "Publish and keep this archive accessible with the matching APK/AAB."
    Write-Host "The archive does not automatically satisfy distribution obligations until made available."
} finally {
    if (Test-Path -LiteralPath $temp) {
        Remove-Item -LiteralPath $temp -Recurse -Force
    }
}
