#Requires -Version 5.1
<#
  add_write_diag.ps1
  ------------------------------------------------------------------
  Adds a temporary log line inside writeScopeMaskState() so we can see the
  REAL sequence of mode writes during one aim-down-sight frame.

  Why: we have proven that
     * the shader patch is applied (17 HAND programs INJECTED)
     * mode=1 IS written at least once ("bridge active (mode=1, ...)"
     * yet a test that discards everything whenever mode != 0 changed
       NOTHING on screen.
  The only remaining explanation is that the mode we write gets
  overwritten by a later write before the pixels are actually drawn.
  Logging every write will show that directly.

  What it adds (2 small pieces):
     1. a counter field
     2. one log line at the top of writeScopeMaskState, printing
        program id + mode, capped at 1500 lines and only while a
        scope mask exists this frame.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\add_write_diag.ps1            # apply
    powershell -ExecutionPolicy Bypass -File .\add_write_diag.ps1 -Preview   # look first
    powershell -ExecutionPolicy Bypass -File .\add_write_diag.ps1 -Revert    # undo
#>

[CmdletBinding()]
param(
    [string]$Repo = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed',
    [switch]$Preview,
    [switch]$Revert
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$target = Get-ChildItem -Path $Repo -Filter 'IrisScopeMaskState.java' -Recurse -File -ErrorAction SilentlyContinue |
          Where-Object { $_.FullName -notmatch '\\build\\' } | Select-Object -First 1
if (-not $target) { Write-Host "NOT FOUND: IrisScopeMaskState.java under $Repo" -ForegroundColor Red; exit 2 }
$path = $target.FullName
Write-Host "File : $path"

# ---------------------------------------------------------------- revert
if ($Revert) {
    $baks = Get-ChildItem -Path (Split-Path $path) -Filter "$([IO.Path]::GetFileName($path)).bak-writediag-*" -File -ErrorAction SilentlyContinue |
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

$i1 = @(); $i2 = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Trim() -eq $A1) { $i1 += $i }
    if ($lines[$i].Trim() -eq $A2) { $i2 += $i }
}
Write-Host "anchor1 (field)   found $($i1.Count) at: $(($i1 | ForEach-Object { $_ + 1 }) -join ', ')"
Write-Host "anchor2 (method)  found $($i2.Count) at: $(($i2 | ForEach-Object { $_ + 1 }) -join ', ')"

if ($i1.Count -ne 1 -or $i2.Count -ne 1) {
    Write-Host "REFUSING to patch: each anchor must appear exactly once." -ForegroundColor Red
    exit 1
}

# already patched?
$already = @($lines | Where-Object { $_ -match 'TACZ Scope\]\[write\]' }).Count
if ($already -gt 0) {
    Write-Host "Diagnostic already present ($already lines) - nothing to do." -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------- build insertion
$ind1 = [regex]::Match($lines[$i1[0]], '^\s*').Value
$ind2 = [regex]::Match($lines[$i2[0]], '^\s*').Value
$body = $ind2 + '    '

$newField = @(
    $ind1 + '/** TEMP diagnostic counter for the scope-mask write sequence. */',
    $ind1 + 'private static int diagWriteSeq;'
)

$newLog = @(
    $body + '// TEMP diagnostic: log every mode write while a mask exists, capped at 1500 lines.',
    $body + 'if (diagWriteSeq < 1500 && ScopeMaskRenderer.hasMaskThisFrame()) {',
    $body + '    diagWriteSeq++;',
    $body + '    GunMod.LOGGER.info("[TACZ Scope][write] seq={} program={} mode={}", diagWriteSeq, programId, mode);',
    $body + '}'
)

Write-Host ""
Write-Host "----- will insert after line $($i1[0] + 1) -----" -ForegroundColor Yellow
foreach ($l in $newField) { Write-Host "  + $l" -ForegroundColor Yellow }
Write-Host "----- will insert after line $($i2[0] + 1) -----" -ForegroundColor Yellow
foreach ($l in $newLog) { Write-Host "  + $l" -ForegroundColor Yellow }
Write-Host "----- context around method -----" -ForegroundColor DarkGray
for ($k = $i2[0]; $k -le [Math]::Min($lines.Count - 1, $i2[0] + 4); $k++) {
    Write-Host ("{0,5}: {1}" -f ($k + 1), $lines[$k]) -ForegroundColor DarkGray
}
Write-Host ""

if ($Preview) { Write-Host "PREVIEW ONLY - nothing written." -ForegroundColor Yellow; exit 0 }

# ---------------------------------------------------------------- apply
$bak = "$path.bak-writediag-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -LiteralPath $path -Destination $bak -Force

$out = New-Object System.Collections.Generic.List[string]
for ($k = 0; $k -lt $lines.Count; $k++) {
    if ($k -eq $i1[0]) { $out.Add($lines[$k]); foreach ($l in $newField) { $out.Add($l) } }
    elseif ($k -eq $i2[0]) { $out.Add($lines[$k]); foreach ($l in $newLog) { $out.Add($l) } }
    else { $out.Add($lines[$k]) }
}
$u8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($path, $out, $u8)
Write-Host "Backup: $bak"

# ---------------------------------------------------------------- verify
$after = @([IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8))
$f1 = @($after | Where-Object { $_ -match 'private static int diagWriteSeq;' }).Count
$f2 = @($after | Where-Object { $_ -match 'TACZ Scope\]\[write\]' }).Count
$m1 = @($after | Where-Object { $_.Trim() -eq $A2 }).Count
$br = 0
foreach ($l in $after) { $br += ($l.Split('{').Count - 1) - ($l.Split('}').Count - 1) }
Write-Host ""
Write-Host "--- verification ---" -ForegroundColor Cyan
Write-Host "counter field  : $f1 (expect 1)"
Write-Host "log line       : $f2 (expect 1)"
Write-Host "method intact  : $m1 (expect 1)"
Write-Host "brace balance  : $br (expect 0)"
if ($f1 -eq 1 -and $f2 -eq 1 -and $m1 -eq 1 -and $br -eq 0) { Write-Host "RESULT: OK" -ForegroundColor Green }
else { Write-Host "RESULT: CHECK NEEDED - paste this back." -ForegroundColor Yellow }
