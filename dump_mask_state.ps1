#Requires -Version 5.1
<#
  dump_mask_state.ps1
  ------------------------------------------------------------------
  Prints the parts of the code that decide the scope-mask "mode".

  WHY: we proved earlier that at the moment the lens is drawn, the
  mode is 0 (a test that said "erase the gun whenever mode is not 0"
  did nothing).  But a log line says mode=1 was written.
  So something writes it, and something else resets it - or it is
  written to the wrong place.  We need to read that code.

  Prints:
    1. IrisGlCommandEncoderMixin.java  (whole file)
    2. IrisScopeMaskState.java         (only the regions around the
       words that matter, +/- 30 lines each)

  Usage:
    powershell -ExecutionPolicy Bypass -File .\dump_mask_state.ps1
#>

[CmdletBinding()]
param(
    [string]$Repo = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed'
)

$ErrorActionPreference = 'Stop'

# Fix: Windows PowerShell 5.1 reads files as GBK by default, which turns the
# UTF-8 Chinese comments in the .java sources into garbage. Force UTF-8.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Test-Path -LiteralPath $Repo)) {
    Write-Host "Repo not found: $Repo" -ForegroundColor Red
    Write-Host "Re-run with -Repo <your path>."
    exit 2
}

function Print-File {
    param([string]$Path, [string]$Title)
    Write-Host ""
    Write-Host "########## $Title ##########" -ForegroundColor Cyan
    Write-Host "FILE : $Path" -ForegroundColor Yellow
    $c = @([IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8))
    Write-Host "LINES: $($c.Count)"
    $i = 0
    foreach ($l in $c) { $i++; Write-Host ("{0,4}: {1}" -f $i, $l) }
}

# ---------------------------------------------------------- 1. full small file
$enc = Get-ChildItem -Path $Repo -Filter 'IrisGlCommandEncoderMixin.java' -Recurse -File -ErrorAction SilentlyContinue |
       Where-Object { $_.FullName -notmatch '\\build\\' } | Select-Object -First 1
if ($enc) { Print-File -Path $enc.FullName -Title 'IrisGlCommandEncoderMixin.java (full)' }
else      { Write-Host "IrisGlCommandEncoderMixin.java not found" -ForegroundColor Yellow }

# ---------------------------------------------------------- 2. targeted regions
$st = Get-ChildItem -Path $Repo -Filter 'IrisScopeMaskState.java' -Recurse -File -ErrorAction SilentlyContinue |
      Where-Object { $_.FullName -notmatch '\\build\\' } | Select-Object -First 1
if (-not $st) { Write-Host "IrisScopeMaskState.java not found" -ForegroundColor Red; exit 1 }

$c = @([IO.File]::ReadAllLines($st.FullName, [System.Text.Encoding]::UTF8))
Write-Host ""
Write-Host "########## IrisScopeMaskState.java (targeted regions) ##########" -ForegroundColor Cyan
Write-Host "FILE : $($st.FullName)" -ForegroundColor Yellow
Write-Host "LINES: $($c.Count)"

$anchors = @(
    'applyToShaderProgram',
    'writeScopeMaskState',
    'resolveMode',
    'textureUnit',
    'activeMode',
    'currentMode',
    'Mode',
    'bridge active',
    'glUniform',
    'glGetUniformLocation',
    'glActiveTexture',
    'bindTexture'
)

$hits = @()
for ($i = 0; $i -lt $c.Count; $i++) {
    foreach ($a in $anchors) {
        if ($c[$i] -match [regex]::Escape($a)) { $hits += $i; break }
    }
}
$hits = $hits | Sort-Object -Unique
Write-Host "anchor lines: $($hits.Count)"

# expand +/- 30 and merge
$SPAN = 30
$keep = New-Object 'bool[]' ($c.Count)
foreach ($h in $hits) {
    $lo = [Math]::Max(0, $h - $SPAN)
    $hi = [Math]::Min($c.Count - 1, $h + $SPAN)
    for ($k = $lo; $k -le $hi; $k++) { $keep[$k] = $true }
}

Write-Host "----- begin (line numbers are the real ones) -----" -ForegroundColor DarkGray
$gap = $false
for ($i = 0; $i -lt $c.Count; $i++) {
    if ($keep[$i]) {
        if ($gap) { Write-Host "      ..." -ForegroundColor DarkGray; $gap = $false }
        Write-Host ("{0,4}: {1}" -f ($i + 1), $c[$i])
    } else { $gap = $true }
}
Write-Host "----- end -----" -ForegroundColor DarkGray
Write-Host ""
Write-Host ""
Write-Host "Encoding note: if Chinese text still looks broken in this window,"
Write-Host "run this once before the script:"
Write-Host "    chcp 65001" -ForegroundColor Yellow
Write-Host "Paste everything back." -ForegroundColor Green
