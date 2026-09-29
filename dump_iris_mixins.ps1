#Requires -Version 5.1
<#
  dump_iris_mixins.ps1
  ------------------------------------------------------------------
  Repo moved to:  C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed

  Prints, with line numbers:
    A. probe-state check on IrisShaderCreatorMixin.java
       (is the v3 discard-probe still applied, or is it the clean 33-line
        original?  the probe must be REVERTED before any real fix)
    B. tacz.iris.mixins.json   -> class names it declares
    C. any file matching *ExtendedShader*  -> full source
    D. @Mixin / @Inject / method= / require= lines inside those files

  Usage:
    powershell -ExecutionPolicy Bypass -File .\dump_iris_mixins.ps1
    powershell -ExecutionPolicy Bypass -File .\dump_iris_mixins.ps1 -Repo <other path>
#>

[CmdletBinding()]
param(
    [string]$Repo    = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed',
    [string]$Pattern = '*ExtendedShader*'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Repo)) {
    Write-Host "Repo not found: $Repo" -ForegroundColor Red
    Write-Host "Re-run with -Repo <your actual path>."
    exit 2
}
Write-Host "Repo: $Repo" -ForegroundColor Yellow
Write-Host ""

# ================================================================ A. probe state
Write-Host "===== A. probe state: IrisShaderCreatorMixin.java =====" -ForegroundColor Cyan
$creator = Get-ChildItem -Path $Repo -Filter 'IrisShaderCreatorMixin.java' -Recurse -File -ErrorAction SilentlyContinue |
           Where-Object { $_.FullName -notmatch '\\build\\' } | Select-Object -First 1
if (-not $creator) {
    Write-Host "(IrisShaderCreatorMixin.java not found)" -ForegroundColor Yellow
} else {
    Write-Host "FILE : $($creator.FullName)"
    $cl = @(Get-Content -LiteralPath $creator.FullName)
    Write-Host "LINES: $($cl.Count)"
    $decls = @()
    for ($i = 0; $i -lt $cl.Count; $i++) {
        if ($cl[$i] -match 'String\s+branch\s*=') { $decls += ($i + 1) }
    }
    Write-Host "'String branch' declarations: $($decls.Count) at line(s): $($decls -join ', ')"
    if ($decls.Count -eq 0) {
        Write-Host "STATUS: UNKNOWN - no 'String branch' declaration found." -ForegroundColor Yellow
    } else {
        foreach ($ln in $decls) {
            $txt = $cl[$ln - 1].Trim()
            Write-Host ("  L{0}: {1}" -f $ln, $txt)
            if ($txt -match 'tacz_probeUv') {
                Write-Host "  STATUS: v3 DISCARD PROBE IS STILL APPLIED - revert before fixing!" -ForegroundColor Red
            } elseif ($txt -match 'tacz_insideScope') {
                Write-Host "  STATUS: looks like the ORIGINAL 33-line branch (clean)." -ForegroundColor Green
            } else {
                Write-Host "  STATUS: unrecognised branch content - inspect manually." -ForegroundColor Yellow
            }
        }
    }
}

# ================================================================ B. json
Write-Host ""
Write-Host "===== B. tacz.iris.mixins.json =====" -ForegroundColor Cyan
$jsons = @(Get-ChildItem -Path $Repo -Recurse -File -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -like 'tacz.iris.mixins.json' })
if ($jsons.Count -eq 0) {
    Write-Host "(no tacz.iris.mixins.json found)" -ForegroundColor Yellow
} else {
    foreach ($j in $jsons) {
        Write-Host "JSON : $($j.FullName)" -ForegroundColor Yellow
        Write-Host "----- content -----" -ForegroundColor DarkGray
        $n = 0
        Get-Content -LiteralPath $j.FullName | ForEach-Object { $n++; Write-Host ("{0,4}: {1}" -f $n, $_) }
        Write-Host "----- end -----" -ForegroundColor DarkGray
    }
}

# ================================================================ C. source
Write-Host ""
Write-Host "===== C. source files matching '$Pattern' =====" -ForegroundColor Cyan
$files = @(Get-ChildItem -Path $Repo -Recurse -File -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -like $Pattern })

if ($files.Count -eq 0) {
    Write-Host "NOTHING FOUND matching '$Pattern' under $Repo" -ForegroundColor Red
    Write-Host ""
    Write-Host "Fallback: listing every mixin file under ...\mixin\client\iris\" -ForegroundColor Yellow
    Get-ChildItem -Path $Repo -Recurse -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -replace '\\', '/' -match '/mixin/client/iris$' } |
        ForEach-Object {
            Write-Host "DIR: $($_.FullName)" -ForegroundColor Yellow
            Get-ChildItem -LiteralPath $_.FullName -File | ForEach-Object { Write-Host "   $($_.Name)" }
        }
    exit 1
}

foreach ($f in $files) {
    $content = @(Get-Content -LiteralPath $f.FullName)
    Write-Host ""
    Write-Host "FILE : $($f.FullName)" -ForegroundColor Yellow
    Write-Host "LINES: $($content.Count)" -ForegroundColor Yellow
    Write-Host "----- content -----" -ForegroundColor DarkGray
    $i = 0
    foreach ($l in $content) { $i++; Write-Host ("{0,4}: {1}" -f $i, $l) }
    Write-Host "----- end -----" -ForegroundColor DarkGray

    Write-Host ""
    Write-Host "--- annotations in this file ---" -ForegroundColor Cyan
    $i = 0
    foreach ($l in $content) {
        $i++
        if ($l -match '@Mixin|@Inject|@Shadow|method\s*=|require\s*=|target\s*=') {
            Write-Host ("{0,4}: {1}" -f $i, $l.Trim())
        }
    }
}

Write-Host ""
Write-Host "Copy the whole output and paste it back." -ForegroundColor Green
