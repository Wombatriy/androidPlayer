#requires -Version 5.1
<#
Locally builds the distributable RELEASE APK with Rubber Band R3 by default.
Signing credentials never appear in the Gradle command line or in Git.
The APK is SIGNED, but Play Protect may still ask to scan a sideloaded app.
#>
param(
    [string]$KeyStorePath = (Join-Path $HOME ".android\androidplayer-release.jks"),
    [string]$Alias = "androidplayer"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$gradlew = Join-Path $root "gradlew.bat"
$vendor = Join-Path $root "app\src\main\cpp\vendor\rubberband\single\RubberBandSingle.cpp"
if (-not (Test-Path -LiteralPath $gradlew)) { throw "Gradle wrapper not found at $gradlew" }
if (-not (Test-Path -LiteralPath $KeyStorePath)) {
    throw "Release signing key missing. First run .\tools\release\create-keystore.ps1"
}
if (-not (Test-Path -LiteralPath $vendor)) {
    throw "Rubber Band source missing. First run .\tools\dsp\prepare-rubberband.ps1"
}

$secret = Read-Host "Release keystore password" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $env:ANDROID_RELEASE_KEYSTORE = (Resolve-Path -LiteralPath $KeyStorePath).Path
    $env:ANDROID_RELEASE_STORE_PASSWORD = $password
    $env:ANDROID_RELEASE_KEY_ALIAS = $Alias
    # PKCS12 created by our script uses the same key and store password.
    $env:ANDROID_RELEASE_KEY_PASSWORD = $password
    Remove-Variable password
    Push-Location $root
    try {
        & $gradlew ":app:assembleRelease" "-Prubberband=true" "--no-daemon"
        if ($LASTEXITCODE -ne 0) { throw "Android release build failed ($LASTEXITCODE)" }
        $apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
        if (-not (Test-Path -LiteralPath $apk)) { throw "Signed release APK not found: $apk" }
        $destination = Join-Path $root "dist"
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        $target = Join-Path $destination "AndroidPlayer-1.0.0-rc1-r3-arm64.apk"
        Copy-Item -LiteralPath $apk -Destination $target -Force
        Write-Host "SIGNED APK ready: $target"
        Write-Host "Note: signed APK != Play Protect approved. Unknown-source scan warnings can still appear."
        Write-Host "Do not distribute the R3 APK before completing the GPL source/release checklist."
    } finally {
        Pop-Location
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    Remove-Item Env:ANDROID_RELEASE_KEYSTORE -ErrorAction SilentlyContinue
    Remove-Item Env:ANDROID_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:ANDROID_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:ANDROID_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
