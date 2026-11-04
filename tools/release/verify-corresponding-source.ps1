#requires -Version 5.1
<#
Validate a GPL corresponding-source ZIP BEFORE releasing any R3 binary.
Ensure it has the exact application revision and a substantial complete
upstream Rubber Band v4.0.0 source snapshot, not merely a GitHub source ZIP.
#>
param(
    [Parameter(Mandatory = $true)][string]$ArchivePath,
    [Parameter(Mandatory = $true)][string]$ExpectedRevision
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($ExpectedRevision -notmatch '^[a-f0-9]{40}$') {
    throw "ExpectedRevision must be the full Git commit SHA (40 hex digits)."
}
if (-not (Test-Path -LiteralPath $ArchivePath)) {
    throw "Source archive not found: $ArchivePath"
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ArchivePath).Path)
try {
    $names = @($zip.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
    $essential = @(
        "LICENSE",
        "README.md",
        "app/build.gradle.kts",
        "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar",
        "app/src/main/cpp/rubberband_jni.cpp",
        "app/src/main/cpp/vendor/rubberband/single/RubberBandSingle.cpp",
        "app/src/main/cpp/vendor/rubberband/COPYING",
        "app/src/main/cpp/vendor/rubberband/src/ext/kissfft/COPYING",
        "app/src/main/cpp/vendor/rubberband/src/ext/speex/COPYING",
        "RELEASE_SOURCE_INFO.txt"
    )
    foreach ($required in $essential) {
        if ($required -notin $names) { throw "Source ZIP missing required file: $required" }
    }
    $vendorPrefix = "app/src/main/cpp/vendor/rubberband/"
    $vendorFiles = @($names | Where-Object {
        $_.StartsWith($vendorPrefix) -and -not $_.EndsWith("/")
    })
    if ($vendorFiles.Count -lt 150) {
        throw "Rubber Band source tree appears incomplete ($($vendorFiles.Count) files, expected >= 150)."
    }
    $sensitive = @($names | Where-Object {
        $_ -eq 'local.properties' -or $_ -match '^\.idea/' -or
        $_ -match '(^|/)\.git/' -or $_ -match '\.(jks|keystore|p12)$'
    })
    if ($sensitive.Count -gt 0) {
        throw "Source ZIP contains private/local configuration: $($sensitive -join ', ')"
    }
    $info = $zip.GetEntry("RELEASE_SOURCE_INFO.txt")
    $reader = New-Object IO.StreamReader($info.Open())
    try { $information = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if (-not $information.Contains("Application source Git revision: $ExpectedRevision")) {
        throw "RELEASE_SOURCE_INFO does not identify the exact expected application revision."
    }
    if (-not $information.Contains(
        "Rubber Band 4.0.0 revision: 1d95888bec3ae0a17c0c4af791810d5a63f6bc35"
    )) {
        throw "Wrong or unrecorded Rubber Band upstream revision."
    }

    Write-Host "Corresponding-source archive verified."
    Write-Host "Application source revision: $ExpectedRevision"
    Write-Host "Bundled Rubber Band upstream files: $($vendorFiles.Count)"
    Write-Host "SHA256: $((Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash)"
    Write-Host "This verifies archive structure only; dependency licensing still needs review."
} finally {
    $zip.Dispose()
}
