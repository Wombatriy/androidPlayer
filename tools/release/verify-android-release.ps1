#requires -Version 5.1
<#
.SYNOPSIS
  Check native ELF alignment and Android APK/AAB packaging before Play upload.
.EXAMPLE
  .\tools\release\verify-android-release.ps1 -PackagePath .\dist\BPM_Player-1.0.0-rc1-r3-arm64.aab
.EXAMPLE
  .\tools\release\verify-android-release.ps1 -PackagePath .\dist\BPM_Player-1.0.0-rc1-r3-arm64.aab -BundletoolJar C:\tools\bundletool-all.jar

When bundletool is unavailable, AAB PAGE_ALIGNMENT_16K must STILL be
checked manually with bundletool or Play Console before distribution.
#>
param(
    [Parameter(Mandatory = $true)][string]$PackagePath,
    [string]$BundletoolJar = "",
    [string]$SdkRoot = ""
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $PackagePath)) { throw "Package not found: $PackagePath" }
$package = (Resolve-Path -LiteralPath $PackagePath).Path
$extension = [IO.Path]::GetExtension($package).ToLowerInvariant()
if ($extension -ne ".aab" -and $extension -ne ".apk") {
    throw "Expected an .aab or .apk package."
}
if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $SdkRoot = $env:ANDROID_SDK_ROOT }
    elseif ($env:LOCALAPPDATA) { $SdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
}
if (-not $SdkRoot -or -not (Test-Path -LiteralPath $SdkRoot)) {
    throw "Android SDK was not found. Set ANDROID_HOME or use -SdkRoot."
}
$ndk = Join-Path $SdkRoot "ndk\28.2.13676358"
$objdump = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objdump.exe"
if (-not (Test-Path -LiteralPath $objdump)) {
    throw "Pinned NDK r28c missing. Install 'NDK (Side by side) 28.2.13676358' in Android Studio."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$temp = Join-Path ([IO.Path]::GetTempPath()) ("bpm-android-verify-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    $zip = [IO.Compression.ZipFile]::OpenRead($package)
    try {
        # APK: lib/arm64-v8a/*.so; AAB: base/lib/arm64-v8a/*.so.
        $native = @($zip.Entries | Where-Object {
            $_.FullName -match '^(?:base/)?lib/[^/]+/[^/]+\.so$'
        })
        if ($native.Count -eq 0) { throw "No native .so in archive (did you build with Rubber Band R3?)" }
        Write-Host "Checking $($native.Count) native .so files for 16 KiB PT_LOAD alignment..."
        $index = 0
        foreach ($entry in $native) {
            $index++
            $extracted = Join-Path $temp ("$index-" + [IO.Path]::GetFileName($entry.FullName))
            $source = $entry.Open()
            try {
                $output = [IO.File]::Create($extracted)
                try { $source.CopyTo($output) } finally { $output.Dispose() }
            } finally { $source.Dispose() }

            $elf = (& $objdump -p $extracted 2>&1 | Out-String)
            if ($LASTEXITCODE -ne 0) {
                throw "Unable to inspect $($entry.FullName) with llvm-objdump."
            }
            $load = [regex]::Matches($elf, '(?m)^\s*LOAD\b[^\r\n]*\balign\s+2\*\*(\d+)')
            if ($load.Count -eq 0) {
                throw "Could not locate ELF PT_LOAD alignments for $($entry.FullName)."
            }
            foreach ($segment in $load) {
                $exponent = [int]$segment.Groups[1].Value
                if ($exponent -lt 14) {
                    throw "$($entry.FullName) contains a PT_LOAD segment with 2**$exponent alignment (needs >= 2**14)."
                }
            }
            Write-Host "  OK: $($entry.FullName) ($($load.Count) aligned LOAD segments)"
        }
    } finally { $zip.Dispose() }

    if ($extension -eq ".apk") {
        $zipalign = Join-Path $SdkRoot "build-tools\35.0.0\zipalign.exe"
        if (-not (Test-Path -LiteralPath $zipalign)) {
            throw "SDK Build Tools 35.0.0 zipalign.exe missing."
        }
        & $zipalign -v -c -P 16 4 $package
        if ($LASTEXITCODE -ne 0) { throw "APK zipalign -P 16 verification failed." }
        Write-Host "APK ELF and ZIP alignment checks passed."
    } else {
        if (-not $BundletoolJar -and $env:BUNDLETOOL_JAR) {
            $BundletoolJar = $env:BUNDLETOOL_JAR
        }
        if ($BundletoolJar) {
            if (-not (Test-Path -LiteralPath $BundletoolJar)) {
                throw "bundletool JAR not found: $BundletoolJar"
            }
            $configuration = (& java -jar $BundletoolJar dump config "--bundle=$package" 2>&1 | Out-String)
            if ($LASTEXITCODE -ne 0) { throw "bundletool could not inspect the AAB." }
            if ($configuration -notmatch 'PAGE_ALIGNMENT_16K') {
                throw "AAB does not declare PAGE_ALIGNMENT_16K. Do not upload it."
            }
            Write-Host "AAB declares PAGE_ALIGNMENT_16K; ELF alignment checks passed."
        } else {
            Write-Warning "ELF alignment passed, but AAB ZIP packaging has NOT been checked."
            Write-Warning "Download official bundletool, then rerun with -BundletoolJar <file.jar>."
            Write-Warning "The final 16 KiB compatibility must also be checked in Play Console and on a 16 KiB device."
        }
    }
    Write-Host "Native 16 KiB ELF compatibility verified (packaging caveats above)."
} finally {
    if (Test-Path -LiteralPath $temp) {
        Remove-Item -LiteralPath $temp -Recurse -Force
    }
}
