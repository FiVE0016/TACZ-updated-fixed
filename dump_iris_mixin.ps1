#Requires -Version 5.1
<#
  dump_iris_mixin.ps1
  ------------------------------------------------------------------
  Locates and prints, with line numbers:
    1. tacz.iris.mixins.json          -> which class names it declares
    2. any file matching *ExtendedShader*  -> the full source

  Why: the log showed
       Mixin apply for mod tacz failed tacz.iris.mixins.json:IrisExtendedShaderMixin
       -> net.irisshaders.iris.pipeline.programs.ExtendedShader
       Expected (Ljava/util/List;CallbackInfo;)V
       but found (Ljava/util/HashMap;GpuTextureView;CallbackInfo;)V
  so we need the real source before touching it.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\dump_iris_mixin.ps1
#>

[CmdletBinding()]
param(
    [string]$Repo    = 'C:\Users\FiVE\Downloads\TACZ-updated-main',
    [string]$Pattern = '*ExtendedShader*'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Repo)) {
    Write-Host "Repo not found: $Repo" -ForegroundColor Red
    Write-Host "Re-run with -Repo <your path>."
    exit 2
}

# ---------------------------------------------------------------- 1. json
Write-Host "===== 1. mixin json =====" -ForegroundColor Cyan
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

# ---------------------------------------------------------------- 2. source
Write-Host ""
Write-Host "===== 2. source files matching '$Pattern' =====" -ForegroundColor Cyan
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
    Write-Host "--- @Inject / @Mixin annotations in this file ---" -ForegroundColor Cyan
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
