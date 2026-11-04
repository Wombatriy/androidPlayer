#requires -Version 5.1
<#
Creates your long-lived Android release signing key OUTSIDE the repository.
Run only once. Back up the resulting .jks securely; never commit or share it.
#>
param(
    [string]$KeyStorePath = (Join-Path $HOME ".android\androidplayer-release.jks"),
    [string]$Alias = "androidplayer"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (Test-Path -LiteralPath $KeyStorePath) {
    throw "The keystore already exists: $KeyStorePath. Reuse it for updates; do not overwrite it."
}
$directory = Split-Path -Parent $KeyStorePath
if (-not (Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool not found. Install JDK 17 or run from an Android Studio JDK shell."
}
Write-Host "Generating a release key valid for 30 years. You'll be asked for a password and certificate details."
& keytool -genkeypair -keystore $KeyStorePath -alias $Alias -keyalg RSA -keysize 3072 -validity 10950 -storetype PKCS12
if ($LASTEXITCODE -ne 0) { throw "Keystore creation failed ($LASTEXITCODE)" }
Write-Host "Release signing key created at: $KeyStorePath"
Write-Host "BACK UP this keystore and password. Never commit or send it to another person."
