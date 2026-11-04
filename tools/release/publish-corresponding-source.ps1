#requires -Version 5.1
<#
.SYNOPSIS
  Publish a verified source-only GitHub Release before Google Play upload.
  Requires GitHub CLI ("gh"), already signed in as the repository owner.
.EXAMPLE
  .\tools\release\package-corresponding-source.ps1
  .\tools\release\publish-corresponding-source.ps1 -Tag v1.0.0-rc1
Run from the final MERGED main revision used to build the AAB.
#>
param(
    [string]$Tag = "v1.0.0-rc1",
    [string]$Repository = "Wombatriy/androidPlayer"
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI missing. Install it from https://cli.github.com/ and run gh auth login."
}
$branch = (& git -C $root branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne "main") {
    throw "Publish sources from final merged main (current: $branch)."
}
$dirty = @(& git -C $root status --porcelain)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -gt 0) {
    throw "Working tree is dirty. Commit all release sources first."
}
$revision = (& git -C $root rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $revision -notmatch '^[0-9a-f]{40}$') {
    throw "Cannot determine exact release revision."
}
$upstream = (& git -C $root rev-parse origin/main).Trim()
if ($LASTEXITCODE -ne 0 -or $upstream -ne $revision) {
    throw "Local main must match origin/main. Run git fetch/pull first."
}
$filename = "BPM_Player_corresponding-source_$($revision.Substring(0,12)).zip"
$archive = Join-Path $root "dist\$filename"
if (-not (Test-Path -LiteralPath $archive)) {
    throw "Build the source archive first using tools\release\package-corresponding-source.ps1."
}
& (Join-Path $PSScriptRoot "verify-corresponding-source.ps1") -ArchivePath $archive -ExpectedRevision $revision

# Never silently replace or reinterpret an existing published tag.
& gh release view $Tag --repo $Repository --json tagName *> $null
if ($LASTEXITCODE -eq 0) {
    throw "Release $Tag already exists. Inspect its source and commit manually; refusing overwrite."
}

$notes = @"
BPM Player — corresponding source package (GPL-3.0-or-later).

Source for this R3-enabled Android release:
- BPM Player source commit: $revision
- Rubber Band v4.0.0 source: 1d95888bec3ae0a17c0c4af791810d5a63f6bc35
- Build scripts, resources, GPL and third-party notices, and full native source.

SOURCE CODE ONLY — no signed APK/AAB is attached.
Build instructions: RELEASE_SOURCE_INFO.txt inside the ZIP.
The AAB uploaded to Google Play must be built from this exact commit SHA.
If the source revision changes, publish a new corresponding-source release.
"@
Write-Host "About to publish a PUBLIC GitHub source-only release:"
Write-Host "  Repo: $Repository"
Write-Host "  Tag: $Tag"
Write-Host "  Commit: $revision"
Write-Host "  Source ZIP: $archive"
$answer = Read-Host "Type PUBLISH to confirm"
if ($answer -cne "PUBLISH") { throw "Cancelled. No release created." }

& gh release create $Tag $archive --repo $Repository --target $revision --title "BPM Player $Tag — corresponding source" --notes $notes
if ($LASTEXITCODE -ne 0) {
    throw "gh release create failed; inspect GitHub before retrying."
}
Write-Host "Source release published. Put the release URL alongside the binary."
Write-Host "The Play AAB must embed the same Git revision: $revision"
