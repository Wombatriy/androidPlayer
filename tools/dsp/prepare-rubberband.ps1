#requires -Version 5.1
<#
 Downloads exact, pinned Rubber Band v4.0.0 source for **local evaluation**.
 GPL v2-or-later: do not redistribute an APK without source/licence compliance.
 This directory is gitignored; no third-party code is committed automatically.
#>
param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$vendor = Join-Path $ProjectRoot "app\src\main\cpp\vendor\rubberband"
$expected = "1d95888bec3ae0a17c0c4af791810d5a63f6bc35"
if (-not (Test-Path -LiteralPath $vendor)) {
    $parent = Split-Path $vendor
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    & git clone --depth 1 --branch v4.0.0 https://github.com/breakfastquay/rubberband.git $vendor
    if ($LASTEXITCODE -ne 0) { throw "Could not clone official Rubber Band source" }
}

$actual = (& git -C $vendor rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $actual -ne $expected) {
    throw "Unexpected Rubber Band commit: $actual (expected $expected). Check your source checkout."
}
if (-not (Test-Path -LiteralPath (Join-Path $vendor "single\RubberBandSingle.cpp"))) {
    throw "Rubber Band source incomplete: single/RubberBandSingle.cpp not found"
}
Write-Host "Rubber Band 4.0.0 source verified ($actual)."
Write-Host "Android NDK and CMake required. Build the test APK using:"
Write-Host '  .\gradlew.bat :app:assembleDebug -Prubberband=true'
