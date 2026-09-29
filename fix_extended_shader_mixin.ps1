#Requires -Version 5.1
<#
  fix_extended_shader_mixin.ps1
  ------------------------------------------------------------------
  Repairs IrisExtendedShaderMixin.java.

  BACKGROUND (plain words)
    This file is a "mixin" - a way of adding our own code into Iris
    (the shader mod) without editing Iris itself.  To do that, our
    function must list exactly the same parameters as Iris' function.

    Iris' function now takes ONE parameter (a List).
    Our file still declares TWO old ones (HashMap, GpuTextureView).
    Because they no longer match, our code was simply never run -
    and because the file also said "require = 0" (= "if it fails,
    stay quiet"), nothing appeared in the log as an error.

    Result: the scope-mask switch was never turned on while drawing,
    so the shader never removed the black lens.

  WHAT THIS SCRIPT DOES
    1. Backs up the file.
    2. Rewrites it with the correct parameter list: (List, CallbackInfo).
    3. Changes "require = 0" to "require = 1", so that if Iris ever
       changes again, the game reports it loudly instead of silently
       disabling the feature.
    4. Verifies the result.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\fix_extended_shader_mixin.ps1
#>

[CmdletBinding()]
param(
    [string]$Repo = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed'
)

$ErrorActionPreference = 'Stop'

$target = Get-ChildItem -Path $Repo -Filter 'IrisExtendedShaderMixin.java' -Recurse -File -ErrorAction SilentlyContinue |
          Where-Object { $_.FullName -notmatch '\\build\\' } | Select-Object -First 1
if (-not $target) { Write-Host "NOT FOUND: IrisExtendedShaderMixin.java under $Repo" -ForegroundColor Red; exit 2 }
$path = $target.FullName

Write-Host "File : $path"
$old = @(Get-Content -LiteralPath $path)
Write-Host "Lines before: $($old.Count)"

$already = @($old | Where-Object { $_ -match 'List<\?>\s+samplers' }).Count
if ($already -gt 0) {
    Write-Host "This file already looks fixed (List samplers present). Nothing to do." -ForegroundColor Green
    exit 0
}

$new = @(
'package com.tacz.guns.mixin.client.iris;',
'',
'import com.tacz.guns.compat.iris.IrisScopeMaskState;',
'import org.spongepowered.asm.mixin.Mixin;',
'import org.spongepowered.asm.mixin.injection.At;',
'import org.spongepowered.asm.mixin.injection.Inject;',
'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;',
'',
'import java.util.List;',
'',
'/**',
' * Writes the TACZ scope-mask uniforms when Iris sets up an ExtendedShader program.',
' *',
' * <p>Iris runs {@code ProgramSamplers#update()} and {@code ProgramUniforms#update()},',
' * which reset every custom uniform back to its default. This hook runs after that',
' * reset and re-applies the scope-mask mode, so the value survives until the draw',
' * call - see {@code IrisScopeMaskState#applyToShaderProgram}.</p>',
' *',
' * <p>MC 26.3 / Iris: the hook signature is {@code (List, CallbackInfo)}.',
' * It used to be {@code (HashMap, GpuTextureView, CallbackInfo)}; when that drifted,',
' * {@code require = 0} let the injector fail silently and the scope-mask mode stayed',
' * 0 at draw time, which produced the black lens. {@code require = 1} makes any',
' * future drift fail loudly at startup instead of silently disabling the feature.</p>',
' */',
'@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)',
'public abstract class IrisExtendedShaderMixin {',
'    @Inject(method = "iris$setupState", at = @At("RETURN"), require = 1)',
'    private void tacz$setupScopeMaskUniforms(List<?> samplers, CallbackInfo ci) {',
'        IrisScopeMaskState.applyToShaderProgram((Object) this);',
'    }',
'}'
)

Write-Host "Lines after : $($new.Count)"

$bak = "$path.bak-fixmixin-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -LiteralPath $path -Destination $bak -Force
Write-Host "Backup: $bak"

$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($path, $new, $utf8)

# ---- verify
$after = @(Get-Content -LiteralPath $path)
# Code lines only: drop the javadoc block, otherwise the explanatory text
# mentioning the OLD names would be counted as leftovers.
$code = @($after | Where-Object { $_ -notmatch '^\s*\*' -and $_ -notmatch '^\s*/\*' })

$hasList   = @($code | Where-Object { $_ -match 'List<\?>\s+samplers' }).Count
$hasReq1   = @($code | Where-Object { $_ -match '@Inject' -and $_ -match 'require\s*=\s*1' }).Count
$hasMap    = @($code | Where-Object { $_ -match 'HashMap' }).Count
$hasGpu    = @($code | Where-Object { $_ -match 'GpuTextureView' }).Count
$hasInject = @($code | Where-Object { $_ -match 'iris\$setupState' }).Count
$hasCall   = @($code | Where-Object { $_ -match 'applyToShaderProgram' }).Count

Write-Host ""
Write-Host "--- verification ---" -ForegroundColor Cyan
Write-Host "lines                       : $($after.Count)   (expect 31)"
Write-Host "code lines (no javadoc)      : $($code.Count)"
Write-Host "'List<?> samplers' present  : $hasList   (expect 1)"
Write-Host "'require = 1' present       : $hasReq1   (expect 1)"
Write-Host "'HashMap' left over         : $hasMap    (expect 0)"
Write-Host "'GpuTextureView' left over  : $hasGpu    (expect 0)"
Write-Host "'iris\$setupState' present   : $hasInject (expect 1)"
Write-Host "'applyToShaderProgram' call : $hasCall   (expect 1)"

$ok = ($after.Count -eq 31) -and ($hasList -eq 1) -and ($hasReq1 -eq 1) -and
      ($hasMap -eq 0) -and ($hasGpu -eq 0) -and ($hasInject -eq 1) -and ($hasCall -eq 1)
if ($ok) { Write-Host "RESULT: OK" -ForegroundColor Green }
else     { Write-Host "RESULT: CHECK NEEDED - paste this output back." -ForegroundColor Yellow }

Write-Host ""
Write-Host "Next:  .\\gradlew.bat :fabric:build --console=plain"
