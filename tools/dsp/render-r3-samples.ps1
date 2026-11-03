#requires -Version 5.1
<#
.SYNOPSIS
 Render short Rubber Band R3 real-time test clips (no native code in APK).
.EXAMPLE
 .\tools\dsp\render-r3-samples.ps1 -InputAudio "C:\Music\jazz.mp3" -StartSeconds 30
#>
param(
    [Parameter(Mandatory=$true)] [string]$InputAudio,
    [string]$OutputDirectory = ".\dsp-comparison",
    [string]$FfmpegExe = "ffmpeg",
    [string]$RubberBandExe = "rubberband",
    [ValidateRange(0, 36000)] [int]$StartSeconds = 0,
    [ValidateRange(5, 180)] [int]$DurationSeconds = 35
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$source = (Resolve-Path -LiteralPath $InputAudio -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}
$dest = (Resolve-Path -LiteralPath $OutputDirectory).Path

try { $null = Get-Command $FfmpegExe -ErrorAction Stop }
catch { throw "ffmpeg missing: install it or use -FfmpegExe with its absolute path." }
try { $null = Get-Command $RubberBandExe -ErrorAction Stop }
catch { throw "Rubber Band CLI missing: install it or use -RubberBandExe with its path." }

$reference = Join-Path $dest "reference-100.wav"
Write-Host "Creating PCM reference:" $reference
& $FfmpegExe -hide_banner -loglevel error -y -ss $StartSeconds -i $source -t $DurationSeconds -vn -ar 44100 -ac 2 -c:a pcm_s16le $reference
if ($LASTEXITCODE -ne 0) { throw "ffmpeg conversion failed: $LASTEXITCODE" }

foreach ($percent in @(80, 85, 90, 95, 105, 110)) {
    $tempo = ([double]$percent / 100.0).ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)
    $output = Join-Path $dest ("r3-realtime-{0:D3}.wav" -f $percent)
    Write-Host "Rubber Band R3 real-time: tempo $tempo -> $output"
    # -3 R3/Finer, -R real-time, -T tempo multiplier (pitch stays unchanged).
    & $RubberBandExe -3 -R -T $tempo $reference $output
    if ($LASTEXITCODE -ne 0) { throw "Rubber Band failed at $tempo: $LASTEXITCODE" }
}
Write-Host "Finished. Play WAVs at 1.00x, and compare the original in Android/Sonic at the same speeds."
