#Requires -Version 5.1
<#
  add_resolve_diag.ps1
  ------------------------------------------------------------------
  WHAT WE LEARNED FROM THE LAST LOG (Feb log, 1500 write records):
    * 1500 mode writes, ALL mode=0
    * the 13 programs that actually received the TACZ shader patch
      (676, 694, 712, 754, 796, 802, 805, 808, 625, 664, 679, 688, 715)
      were each written exactly ONCE - during world load
    * the programs written every single frame (348 -> 1004 times,
      225 -> 270, 216 -> 90, 255, 54, 48) are NOT in that injected list
    => while aiming, the value written is always 0, and it goes to
       programs that were never patched. The patched hand programs are
       never touched again after load.

  SO THE NEXT QUESTION IS: when you actually aim down sight, does the
  code even RECOGNISE the scope pipeline?

  This script adds one log line inside resolveModeUncached(), printing
  the pipeline name the code actually sees, capped at 400 lines.

  Then in the log we expect to see either:
      ... pipeline=tacz:pipeline/scope_body_clipped -> mode=1   (recognised)
  or   ... pipeline=minecraft:pipeline/...          -> mode=0   (NOT recognised)
  The second case means resolveMode() never fires for the scope pass.

  Built with List.Add() one line at a time (a previous script used a
  multi-element array literal and the lines collapsed into one, which
  let a '/** */' comment swallow a field declaration).

  Usage:
    powershell -ExecutionPolicy Bypass -File .\add_resolve_diag.ps1            # apply
    powershell -ExecutionPolicy Bypass -File .\add_resolve_diag.ps1 -Preview   # look first
    powershell -ExecutionPolicy Bypass -File .\add_resolve_diag.ps1 -Revert    # undo
#>

[CmdletBinding()]
param(
    [string]$Repo = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed',
    [switch]$Preview,
    [switch]$Revert
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$BAK_TAG = 'resolvediag'

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
$A1 = 'private static int diagWriteSeq;'
$A2 = 'private static int resolveModeUncached(Object glPipeline) {'

$i1 = -1; $i2 = -1; $n1 = 0; $n2 = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Trim() -eq $A1) { if ($n1 -eq 0) { $i1 = $i }; $n1++ }
    if ($lines[$i].Trim() -eq $A2) { if ($n2 -eq 0) { $i2 = $i }; $n2++ }
}
Write-Host "anchor1 (counter field) count=$n1 at line: $($i1 + 1)"
Write-Host "anchor2 (method)        count=$n2 at line: $($i2 + 1)"

if ($n1 -ne 1) {
    Write-Host "REFUSING: '$A1' must exist exactly once (run add_write_diag2.ps1 first)." -ForegroundColor Red
    exit 1
}
if ($n2 -ne 1) {
    Write-Host "REFUSING: '$A2' must exist exactly once." -ForegroundColor Red
    exit 1
}

$already = 0
foreach ($l in $lines) { if ($l -match 'TACZ Scope\]\[resolve\]') { $already++ } }
if ($already -gt 0) {
    Write-Host "Diagnostic already present ($already lines) - nothing to do." -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------- build, one Add() per line
$ind1 = [regex]::Match($lines[$i1], '^\s*').Value
$ind2 = [regex]::Match($lines[$i2], '^\s*').Value
$body = $ind2 + '    '

$newField = New-Object System.Collections.Generic.List[string]
$newField.Add($ind1 + '/** TEMP DIAG: counts resolveModeUncached calls. */')
$newField.Add($ind1 + 'private static int diagResolveSeq;')

$newLog = New-Object System.Collections.Generic.List[string]
$newLog.Add($body + '// TEMP DIAG: print the pipeline name this method actually sees.')
$newLog.Add($body + 'if (diagResolveSeq < 400) {')
$newLog.Add($body + '    diagResolveSeq++;')
$newLog.Add($body + '    GunMod.LOGGER.info("[TACZ Scope][resolve] seq={} raw={}", diagResolveSeq, String.valueOf(taczResolvePipelineId(glPipeline)));')
$newLog.Add($body + '}')

# gate: a collapsed line would put '//' together with code
foreach ($l in $newField) { if ($l -match '/\*\*' -and $l -match 'private\s+static') { Write-Host "GATE FAIL (field) - lines collapsed"; exit 1 } }
foreach ($l in $newLog)   { if ($l -match '//' -and ($l -match 'if\s*\(' -or $l -match 'LOGGER')) { Write-Host "GATE FAIL (log) - lines collapsed"; exit 1 } }

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
    if ($l -match 'private static int diagResolveSeq;') { $f1++ }
    if ($l -match 'TACZ Scope\]\[resolve\]') { $f2++ }
    if ($l.Trim() -eq $A2) { $m1++ }
    if (($l -match '/\*\*' -and $l -match 'private\s+static') -or ($l -match '//' -and $l -match 'LOGGER')) { $bad++ }
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
