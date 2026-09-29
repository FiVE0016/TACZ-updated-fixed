#Requires -Version 5.1
<#
  add_write_diag2.ps1
  ------------------------------------------------------------------
  Adds a temporary log line inside writeScopeMaskState() so we can see the
  REAL sequence of mode writes during one aim-down-sight frame.

  Why: we have proven that
     * the shader patch IS applied (17 HAND programs INJECTED)
     * mode=1 IS written at least once ("bridge active (mode=1, ...)")
     * yet a test that discards everything whenever mode != 0 changed
       NOTHING on screen.
  Remaining explanation: the mode we write gets overwritten by a later
  write before the pixels are drawn. Logging every write shows that.

  Same job as add_write_diag.ps1, but built with List.Add() one line at a
  time - the previous version used a multi-element array literal and the
  lines came out collapsed onto one line (the '/** */' comment then
  swallowed the field declaration). One Add() per line cannot collapse.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\add_write_diag2.ps1            # apply
    powershell -ExecutionPolicy Bypass -File .\add_write_diag2.ps1 -Preview   # look first
    powershell -ExecutionPolicy Bypass -File .\add_write_diag2.ps1 -Revert    # undo
#>

[CmdletBinding()]
param(
    [string]$Repo = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed',
    [switch]$Preview,
    [switch]$Revert
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$BAK_TAG = 'writediag'

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

# ---------------------------------------------------------------- anchors
$A1 = 'private static boolean loggedProgramMismatch;'
$A2 = 'private static void writeScopeMaskState(int programId, int mode, Object glRenderPass) {'

$i1 = -1; $i2 = -1; $n1 = 0; $n2 = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Trim() -eq $A1) { if ($n1 -eq 0) { $i1 = $i }; $n1++ }
    if ($lines[$i].Trim() -eq $A2) { if ($n2 -eq 0) { $i2 = $i }; $n2++ }
}
Write-Host "anchor1 (field)  count=$n1 at line: $($i1 + 1)"
Write-Host "anchor2 (method) count=$n2 at line: $($i2 + 1)"

if ($n1 -ne 1 -or $n2 -ne 1) {
    Write-Host "REFUSING to patch: each anchor must appear exactly once." -ForegroundColor Red
    exit 1
}

$already = 0
foreach ($l in $lines) { if ($l -match 'TACZ Scope\]\[write\]') { $already++ } }
if ($already -gt 0) {
    Write-Host "Diagnostic already present ($already lines) - nothing to do." -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------- build (one Add per line)
$ind1 = [regex]::Match($lines[$i1], '^\s*').Value
$ind2 = [regex]::Match($lines[$i2], '^\s*').Value
$body = $ind2 + '    '

$newField = New-Object System.Collections.Generic.List[string]
$newField.Add($ind1 + '/** TEMP DIAG: counts scope-mask mode writes. */')
$newField.Add($ind1 + 'private static int diagWriteSeq;')

$newLog = New-Object System.Collections.Generic.List[string]
$newLog.Add($body + '// TEMP DIAG: log every mode write while a mask exists, capped at 1500 lines.')
$newLog.Add($body + 'if (diagWriteSeq < 1500 && ScopeMaskRenderer.hasMaskThisFrame()) {')
$newLog.Add($body + '    diagWriteSeq++;')
$newLog.Add($body + '    GunMod.LOGGER.info("[TACZ Scope][write] seq={} program={} mode={}", diagWriteSeq, programId, mode);')
$newLog.Add($body + '}')

# hard gate: nothing may be one line containing two statements
foreach ($l in $newField) { if ($l -match '/\*\*' -and $l -match 'private\s+static') { Write-Host "GATE FAIL (field)"; exit 1 } }
foreach ($l in $newLog)   { if ($l -match '//' -and ($l -match 'if\s*\(' -or $l -match 'LOGGER')) { Write-Host "GATE FAIL (log)"; exit 1 } }

Write-Host ""
Write-Host "----- will insert after line $($i1 + 1) -----" -ForegroundColor Yellow
foreach ($l in $newField) { Write-Host "  + $l" -ForegroundColor Yellow }
Write-Host "----- will insert after line $($i2 + 1) -----" -ForegroundColor Yellow
foreach ($l in $newLog) { Write-Host "  + $l" -ForegroundColor Yellow }
Write-Host "----- context around method -----" -ForegroundColor DarkGray
for ($k = $i2; $k -le [Math]::Min($lines.Count - 1, $i2 + 3); $k++) {
    Write-Host ("{0,5}: {1}" -f ($k + 1), $lines[$k]) -ForegroundColor DarkGray
}
Write-Host ""

if ($Preview) { Write-Host "PREVIEW ONLY - nothing written." -ForegroundColor Yellow; exit 0 }

# ---------------------------------------------------------------- baseline braces
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
    if ($k -eq $i1) { $out.Add($lines[$k]); foreach ($l in $newField) { $out.Add($l) } }
    elseif ($k -eq $i2) { $out.Add($lines[$k]); foreach ($l in $newLog) { $out.Add($l) } }
    else { $out.Add($lines[$k]) }
}
$u8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($path, $out, $u8)
Write-Host "Backup: $bak"

# ---------------------------------------------------------------- verify
$after = New-Object System.Collections.Generic.List[string]
foreach ($l in [IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)) { $after.Add($l) }

$f1 = 0; $f2 = 0; $m1 = 0; $bad = 0
foreach ($l in $after) {
    if ($l -match 'private static int diagWriteSeq;') { $f1++ }
    if ($l -match 'TACZ Scope\]\[write\]') { $f2++ }
    if ($l.Trim() -eq $A2) { $m1++ }
    if ($l -match '/\*\*' -and $l -match 'private\s+static') { $bad++ }
}
$now = Get-BraceBalance -L $after
Write-Host ""
Write-Host "--- verification ---" -ForegroundColor Cyan
Write-Host "counter field   : $f1 (expect 1)"
Write-Host "log line        : $f2 (expect 1)"
Write-Host "method intact   : $m1 (expect 1)"
Write-Host "collapsed lines : $bad (expect 0)"
Write-Host "brace delta     : $($now - $base) (expect 0)"
if ($f1 -eq 1 -and $f2 -eq 1 -and $m1 -eq 1 -and $bad -eq 0 -and ($now - $base) -eq 0) {
    Write-Host "RESULT: OK" -ForegroundColor Green
} else {
    Write-Host "RESULT: CHECK NEEDED - paste this back." -ForegroundColor Yellow
}
