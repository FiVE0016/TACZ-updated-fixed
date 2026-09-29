#Requires -Version 5.1
<#
  discard_probe3.ps1  --  DECISIVE TEST for defect #5 (high-zoom black lens)
  ------------------------------------------------------------------
  What it does
    In IrisShaderCreatorMixin.java, the injected GLSL has TWO nested checks:

        if (tacz_ScopeMaskMode != 0) {                       <- outer
            if ((tacz_ScopeMaskMode == 1 && tacz_insideScope)
             || (tacz_ScopeMaskMode == 2 && !tacz_insideScope)) {   <- inner
                ... discard ...
            }
        }

    Everything upstream is proven working: mode=1 is written, all 17 HAND
    programs are INJECTED, the ocular ring is drawn. The ONLY unproven link
    is whether tacz_insideScope ever reads as true.

    This script replaces the WHOLE `String branch = ...` statement with:

        if (tacz_ScopeMaskMode != 0) {
            vec2 tacz_probeUv = gl_FragCoord.xy
                 / max(vec2(textureSize(tacz_ScopeMaskSampler, 0)), vec2(1.0));
            if (tacz_probeUv.x >= 0.0) discard;      // UV is always >= 0
        }

    i.e. mode!=0 -> discard unconditionally (the whole gun/hand model goes),
    BUT textureSize(tacz_ScopeMaskSampler, 0) is kept on purpose so the GLSL
    compiler cannot dead-code-eliminate the sampler uniform.  Without that
    reference the uniform disappears, glGetUniformLocation returns -1, and
    if writeScopeMaskState bails early then mode=1 never gets written - which
    would make "no change" a FALSE NEGATIVE (v1 of this probe had that flaw).

    Then READ THE SCREEN:
      * scope body DISAPPEARS  -> injected GLSL IS running.
                                  Root cause is 100% the tacz_insideScope
                                  sampling -> next: texture unit / UV.
      * NO CHANGE AT ALL       -> injected GLSL never runs.
                                  Next: shader compile failure / program reuse.

  Safety
    - PREVIEW by default.  Nothing is written unless you pass -Apply.
    - Timestamped backup, restore with -Revert.
    - Refuses to touch the file if the anchor is not found exactly once.

  Usage
    # 0. FIRST restore the pristine 33-line branch (bak-probe2 was taken
    #    BEFORE v2 patched, so it is the clean original):
    powershell -ExecutionPolicy Bypass -File .\discard_probe2.ps1 -Revert
    # 1. then:
    powershell -ExecutionPolicy Bypass -File .\discard_probe3.ps1           # preview
    powershell -ExecutionPolicy Bypass -File .\discard_probe3.ps1 -Apply    # patch
    powershell -ExecutionPolicy Bypass -File .\discard_probe3.ps1 -Revert   # undo
#>

[CmdletBinding()]
param(
    [string]$Repo   = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed',
    [string]$File   = 'IrisShaderCreatorMixin.java',
    [switch]$Apply,
    [switch]$Revert
)

$ErrorActionPreference = 'Stop'
$BAK_TAG = 'probe3'

# ---------------------------------------------------------------- locate
function Find-File {
    param([string]$Root, [string]$Name)
    $hit = Get-ChildItem -Path $Root -Filter $Name -Recurse -File -ErrorAction SilentlyContinue |
           Where-Object { $_.FullName -notmatch '\\build\\' } | Select-Object -First 1
    return $hit
}

if (-not (Test-Path -LiteralPath $Repo)) {
    Write-Host "Repo not found: $Repo" -ForegroundColor Red
    Write-Host "Re-run with -Repo <path>."
    exit 2
}

$t = Find-File -Root $Repo -Name $File
if (-not $t) { Write-Host "NOT FOUND: $File under $Repo" -ForegroundColor Red; exit 2 }
$path = $t.FullName

# ---------------------------------------------------------------- revert
if ($Revert) {
    $baks = Get-ChildItem -Path (Split-Path $path) -Filter "$([IO.Path]::GetFileName($path)).bak-$BAK_TAG-*" -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    if (-not $baks) { Write-Host "No $BAK_TAG backup found - nothing to revert." -ForegroundColor Yellow; exit 1 }
    Copy-Item -LiteralPath $baks[0].FullName -Destination $path -Force
    Write-Host "Reverted from: $($baks[0].Name)" -ForegroundColor Green
    exit 0
}

Write-Host "File : $path"
Write-Host "Lines: $(([IO.File]::ReadAllLines($path)).Count)"

$lines = [System.Collections.Generic.List[string]][IO.File]::ReadAllLines($path)

# ---------------------------------------------------------------- anchor
# The outer check is the stable anchor; it appears in the Java source once.
$ANCHOR = 'tacz_ScopeMaskMode != 0'
$anchorIdxs = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match [regex]::Escape($ANCHOR)) { $anchorIdxs += $i }
}
Write-Host "Anchor occurrences: $($anchorIdxs.Count) at line(s): $(($anchorIdxs | ForEach-Object { $_ + 1 }) -join ', ')"

if ($anchorIdxs.Count -eq 0) {
    Write-Host "Anchor '$ANCHOR' not found. This script will not guess." -ForegroundColor Red
    Write-Host "Open the file and tell me the exact line of the 'String branch = ...' statement."
    exit 2
}
if ($anchorIdxs.Count -gt 1) {
    Write-Host "Anchor found more than once - refusing to auto-patch." -ForegroundColor Red
    Write-Host "Tell me which line number is the branch STRING and I will pin it."
    exit 2
}

$a = $anchorIdxs[0]

# ---------------------------------------------------------------- walk UP to statement start
$start = $a
$up = 0
while ($start -gt 0 -and $up -lt 8) {
    $prev = $start - 1
    if ([string]::IsNullOrWhiteSpace($lines[$prev])) { break }
    if ($lines[$prev] -match '^\s*(public|private|protected|static|final)\b' -and
        $lines[$prev] -notmatch 'String\s+\w+\s*=') { break }
    if ($lines[$prev] -match ';\s*$') { break }
    $start = $prev
    $up++
    if ($lines[$start] -match 'String\s+\w+\s*=') { break }
}

# ---------------------------------------------------------------- walk DOWN to ';' at depth 0
function Get-ParenDelta {
    param([string]$Line)
    $d = 0
    $inStr = $false
    for ($k = 0; $k -lt $Line.Length; $k++) {
        $c = $Line[$k]
        if ($c -eq '"' -and ($k -eq 0 -or $Line[$k-1] -ne '\')) { $inStr = -not $inStr }
        elseif (-not $inStr) {
            if ($c -eq '(') { $d++ }
            elseif ($c -eq ')') { $d-- }
        }
    }
    return $d
}
function Get-SemiAtDepth0 {
    param([string]$Line, [int]$Depth)
    $inStr = $false
    $d = $Depth
    for ($k = 0; $k -lt $Line.Length; $k++) {
        $c = $Line[$k]
        if ($c -eq '"' -and ($k -eq 0 -or $Line[$k-1] -ne '\')) { $inStr = -not $inStr }
        elseif (-not $inStr) {
            if ($c -eq '(') { $d++ }
            elseif ($c -eq ')') { $d-- }
            elseif ($c -eq ';' -and $d -eq 0) { return $true }
        }
    }
    return $false
}

$depth = 0
$end = -1
for ($j = $start; $j -lt $lines.Count; $j++) {
    if (Get-SemiAtDepth0 -Line $lines[$j] -Depth $depth) { $end = $j; break }
    $depth += (Get-ParenDelta -Line $lines[$j])
}
if ($end -lt 0) {
    Write-Host "Could not find the end of the branch statement." -ForegroundColor Red
    exit 1
}
# Guard: if the next non-blank line continues the concatenation, we stopped too early.
while ($end + 1 -lt $lines.Count) {
    $nx = $end + 1
    if ([string]::IsNullOrWhiteSpace($lines[$nx])) { break }
    if ($lines[$nx].TrimStart() -like '+*') { $end = $nx } else { break }
    if (Get-SemiAtDepth0 -Line $lines[$end] -Depth 0) { break }
}

$indent = [regex]::Match($lines[$start], '^\s*').Value
$varName = 'branch'
$m = [regex]::Match($lines[$start], 'String\s+(\w+)\s*=')
if ($m.Success) { $varName = $m.Groups[1].Value }

# ---------------------------------------------------------------- replacement
# v2 emitted a '//' comment line and the Java statement as two array elements,
# PowerShell collapsed them into ONE line, and the leading '//' commented the
# declaration out -> "cannot find symbol: variable branch".
#
# v3 therefore writes EXACTLY ONE LINE and NO comment line.  One line cannot
# be collapsed and cannot be commented out.
#
# textureSize(tacz_ScopeMaskSampler, 0) is kept on purpose so the GLSL compiler
# cannot dead-code-eliminate the sampler uniform.  Without that reference the
# uniform disappears, glGetUniformLocation returns -1, and if
# writeScopeMaskState bails early then mode=1 never gets written - which would
# make "no change" a FALSE NEGATIVE.

# Single-quoted pieces: '\n' stays a literal backslash-n, which is what Java needs.
$glsl = ''
$glsl += '\n    if (tacz_ScopeMaskMode != 0) {\n'
$glsl += '        vec2 tacz_probeUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ScopeMaskSampler, 0)), vec2(1.0));\n'
$glsl += '        if (tacz_probeUv.x >= 0.0) discard;\n'
$glsl += '    }\n'

$newStmt = $indent + 'String ' + $varName + ' = "' + $glsl + '";'

# hard gate before anything is written
if ($newStmt -notmatch '^\s*String\s+\w+\s*=') {
    Write-Host "Internal error: replacement is not a Java declaration." -ForegroundColor Red
    exit 1
}
if (-not $newStmt.TrimEnd().EndsWith(';')) {
    Write-Host "Internal error: replacement does not end with ';'." -ForegroundColor Red
    exit 1
}
if ($newStmt.Contains("`n") -or $newStmt.Contains("`r")) {
    Write-Host "Internal error: replacement spans more than one line." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "----- context BEFORE -----" -ForegroundColor DarkGray
for ($k = [Math]::Max(0, $start - 3); $k -lt $start; $k++) {
    Write-Host ("{0,5} | {1}" -f ($k + 1), $lines[$k]) -ForegroundColor DarkGray
}
Write-Host "----- WILL BE REPLACED -----" -ForegroundColor Red
for ($k = $start; $k -le $end; $k++) {
    Write-Host ("{0,5} X {1}" -f ($k + 1), $lines[$k]) -ForegroundColor Red
}
Write-Host "----- WITH -----" -ForegroundColor Green
Write-Host "      + $newStmt" -ForegroundColor Green
Write-Host "----- context AFTER -----" -ForegroundColor DarkGray
for ($k = $end + 1; $k -le [Math]::Min($lines.Count - 1, $end + 3); $k++) {
    Write-Host ("{0,5} | {1}" -f ($k + 1), $lines[$k]) -ForegroundColor DarkGray
}
Write-Host ""

if (-not $Apply) {
    Write-Host "PREVIEW ONLY - nothing written." -ForegroundColor Yellow
    Write-Host "Confirm the red block is the whole 'String $varName = ...' statement, then re-run with -Apply"
    exit 0
}

# ---------------------------------------------------------------- apply
$bak = "$path.bak-$BAK_TAG-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -LiteralPath $path -Destination $bak -Force

$out = New-Object System.Collections.Generic.List[string]
for ($k = 0; $k -lt $lines.Count; $k++) {
    if ($k -eq $start) { $out.Add($newStmt) }
    elseif ($k -lt $start -or $k -gt $end) { $out.Add($lines[$k]) }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($path, $out, $utf8)
Write-Host "Backup: $bak"
Write-Host "Patched. Replaced $($end - $start + 1) line(s) with 1." -ForegroundColor Green
# ---------------------------------------------------------------- verify
$after = [IO.File]::ReadAllLines($path)
$decls = @($after | Where-Object { $_ -match ('String\s+' + $varName + '\s*=') })
Write-Host ""
Write-Host "--- verification ---" -ForegroundColor Cyan
Write-Host "'$varName' declarations found: $($decls.Count)"
foreach ($d in $decls) { Write-Host "  > $($d.Trim())" }
if ($decls.Count -ne 1) { Write-Host "PROBLEM: expected exactly 1 declaration." -ForegroundColor Red }
elseif ($decls[0].TrimStart().StartsWith('//')) { Write-Host "PROBLEM: declaration is commented out." -ForegroundColor Red }
else { Write-Host "OK: single live declaration." -ForegroundColor Green }

Write-Host ""
Write-Host "Next:  .\gradlew.bat :fabric:build --console=plain"
Write-Host "Then drop the new jar into mods and ADS."
Write-Host "  scope body vanishes -> GLSL runs; problem is tacz_insideScope sampling"
Write-Host "  no change           -> GLSL never runs; check shader compile / program reuse"
Write-Host "Undo with: -Revert"
