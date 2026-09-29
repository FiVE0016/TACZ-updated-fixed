#Requires -Version 5.1
<#
  retarget_diag.ps1
  ------------------------------------------------------------------
  Rewrites the TEMP diagnostic inside writeScopeMaskState() so it records
  ONLY the programs that actually received the TACZ shader patch, without
  any "a mask exists this frame" condition.

  WHY (three mistakes found in the previous diagnostic):
    1. The 1500-line cap was exhausted during WORLD LOAD (2 seconds), so
       nothing was recorded later when the user actually aimed.
    2. The condition "ScopeMaskRenderer.hasMaskThisFrame()" excluded the
       very moment mode=1 was written (that write happened while the
       condition was false) - so the interesting event was filtered out.
    3. Consequently the "1500 writes all mode=0" conclusion was based on
       load-time data, not on aiming. It is NOT a valid conclusion.

  NEW BEHAVIOUR
    - Record only if the program HAS the tacz uniform (i.e. it was patched).
      That is exactly the 13-17 hand programs we care about.
    - No mask-frame condition, so the moment mode becomes 1 is captured.
    - Cap 20000 lines, and log a timestamp so we can separate load from aim.
    - Also log whether a mask exists, as DATA (not as a filter).

  Built with List.Add() one line at a time - multi-element array literals
  have collapsed into a single line before, which let a comment swallow
  a declaration.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\retarget_diag.ps1            # preview by default? no: applies
    powershell -ExecutionPolicy Bypass -File .\retarget_diag.ps1 -Preview   # look first
    powershell -ExecutionPolicy Bypass -File .\retarget_diag.ps1 -Revert    # undo
#>

[CmdletBinding()]
param(
    [string]$Repo = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed',
    [switch]$Preview,
    [switch]$Revert
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$BAK_TAG = 'retargetdiag'

$target = Get-ChildItem -Path $Repo -Filter 'IrisScopeMaskState.java' -Recurse -File -ErrorAction SilentlyContinue |
          Where-Object { $_.FullName -notmatch '\\build\\' } | Select-Object -First 1
if (-not $target) { Write-Host "NOT FOUND: IrisScopeMaskState.java under $Repo" -ForegroundColor Red; exit 2 }
$path = $target.FullName
Write-Host "File : $path"

# ---------------------------------------------------------------- revert
if ($Revert) {
    $baks = Get-ChildItem -Path (Split-Path $path) -Filter "$([IO.Path]::GetFileName($path)).bak-$BAK_TAG-*" -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    if (-not $baks) { Write-Host "No backup found - nothing to revert." -ForegroundColor Yellow; exit 1 }
    Copy-Item -LiteralPath $baks[0].FullName -Destination $path -Force
    Write-Host "Reverted from: $($baks[0].Name)" -ForegroundColor Green
    exit 0
}

$lines = [System.Collections.Generic.List[string]][IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)
Write-Host "Lines: $($lines.Count)"

# ---------------------------------------------------------------- locate the TEMP DIAG block
# It is the 5 lines starting with the '// TEMP DIAG: log every mode write' comment.
$start = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '//\s*TEMP DIAG:\s*log every mode write') { $start = $i; break }
}
if ($start -lt 0) {
    Write-Host "REFUSING: could not find the previous TEMP DIAG block." -ForegroundColor Red
    Write-Host "Expected a line like:  // TEMP DIAG: log every mode write while a mask exists, capped at 1500 lines."
    exit 1
}
Write-Host "Found previous TEMP DIAG block at line $($start + 1)"

# the block is 5 lines: comment, if, increment, LOGGER, closing brace
if ($lines[$start + 4].Trim() -ne '}') {
    Write-Host "REFUSING: the block does not look like 5 lines ending with '}'." -ForegroundColor Red
    for ($k = $start; $k -le [Math]::Min($lines.Count - 1, $start + 6); $k++) {
        Write-Host ("{0,5}: {1}" -f ($k + 1), $lines[$k]) -ForegroundColor DarkGray
    }
    exit 1
}
$end = $start + 4

$ind = [regex]::Match($lines[$start], '^\s*').Value

$newBlock = New-Object System.Collections.Generic.List[string]
$newBlock.Add($ind + '// TEMP DIAG: record ONLY programs that carry the tacz uniform (i.e. patched).')
$newBlock.Add($ind + 'int taczDiagModeLoc = GL20C.glGetUniformLocation(programId, UNIFORM_MODE);')
$newBlock.Add($ind + 'if (taczDiagModeLoc >= 0 && diagWriteSeq < 20000) {')
$newBlock.Add($ind + '    diagWriteSeq++;')
$newBlock.Add($ind + '    GunMod.LOGGER.info("[TACZ Scope][write] seq={} program={} mode={} mask={} t={}ms",')
$newBlock.Add($ind + '            diagWriteSeq, programId, mode,')
$newBlock.Add($ind + '            ScopeMaskRenderer.hasMaskThisFrame(), System.currentTimeMillis() % 100000);')
$newBlock.Add($ind + '}')

# gate against collapsed lines
foreach ($l in $newBlock) {
    if ($l -match '//' -and ($l -match 'int\s+taczDiag' -or $l -match 'if\s*\(' -or $l -match 'LOGGER')) {
        Write-Host "GATE FAIL - lines collapsed" -ForegroundColor Red; exit 1
    }
}

Write-Host ""
Write-Host "----- will REPLACE lines $($start + 1) .. $($end + 1) -----" -ForegroundColor Red
for ($k = $start; $k -le $end; $k++) { Write-Host ("{0,5} X {1}" -f ($k + 1), $lines[$k]) -ForegroundColor Red }
Write-Host "----- WITH -----" -ForegroundColor Green
foreach ($l in $newBlock) { Write-Host "  + $l" -ForegroundColor Green }
Write-Host ""

if ($Preview) { Write-Host "PREVIEW ONLY - nothing written." -ForegroundColor Yellow; exit 0 }

function Get-BraceBalance {
    param([System.Collections.Generic.List[string]]$L)
    $b = 0
    foreach ($l in $L) { $b += ($l.Split('{').Count - 1) - ($l.Split('}').Count - 1) }
    return $b
}
$base = Get-BraceBalance -L $lines

# ---------------------------------------------------------------- apply
$bak = "$path.bak-$BAK_TAG-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -LiteralPath $path -Destination $bak -Force

$out = New-Object System.Collections.Generic.List[string]
for ($k = 0; $k -lt $lines.Count; $k++) {
    if ($k -eq $start) { foreach ($l in $newBlock) { $out.Add($l) } }
    elseif ($k -lt $start -or $k -gt $end) { $out.Add($lines[$k]) }
}
$u8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($path, $out, $u8)
Write-Host "Backup: $bak"

# ---------------------------------------------------------------- verify
$after = New-Object System.Collections.Generic.List[string]
foreach ($l in [IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)) { $after.Add($l) }
$new1 = 0; $old1 = 0; $c1 = 0; $c2 = 0; $bad = 0
foreach ($l in $after) {
    if ($l -match 'taczDiagModeLoc >= 0') { $new1++ }
    if ($l -match 'log every mode write while a mask exists') { $old1++ }
    if ($l -match 'private static int diagWriteSeq;') { $c1++ }
    if ($l -match 'private static int diagResolveSeq;') { $c2++ }
    if ($l -match '//' -and ($l -match 'if\s*\(' -or $l -match 'LOGGER' -or $l -match 'int\s+taczDiag')) { $bad++ }
}
$now = Get-BraceBalance -L $after
Write-Host ""
Write-Host "--- verification ---" -ForegroundColor Cyan
Write-Host "new guard present   : $new1 (expect 1)"
Write-Host "old block gone      : $old1 (expect 0)"
Write-Host "diagWriteSeq field  : $c1 (expect 1)"
Write-Host "diagResolveSeq field: $c2 (expect 1)"
Write-Host "collapsed lines     : $bad (expect 0)"
Write-Host "brace delta         : $($now - $base) (expect 0)"
if ($new1 -eq 1 -and $old1 -eq 0 -and $c1 -eq 1 -and $c2 -eq 1 -and $bad -eq 0 -and ($now - $base) -eq 0) {
    Write-Host "RESULT: OK" -ForegroundColor Green
} else {
    Write-Host "RESULT: CHECK NEEDED - paste this back." -ForegroundColor Yellow
}
