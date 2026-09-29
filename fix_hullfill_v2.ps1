[CmdletBinding()]
param(
    [string]$Src = "C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed\common\src\main\java\com\tacz\guns\client\render\scope\ScopeMaskRenderer.java",
    [switch]$Apply,
    [switch]$Restore
)

#
# fix_hullfill_v2.ps1 -- 缺陷 3 修复（第二版）
#
# 与 v1 的区别：v1 把所有凸包顶点统一写在 z = -1 平面上，且把「近平面保护」
# 阈值错设成 0.02（目镜离相机只有几厘米，大量近端顶点被误丢），
# 又把方向上限错设成 8.0（原 NDC 上限是 2.0，放宽了 4 倍）→ 凸包被撑变形。
#
# v2：凸包顶点写回它【自己】的原始绘制空间坐标 (x, y, z)。
#     dir 只用来做凸包排序与筛选，不参与输出。
#     于是深度天然正确、形状天然贴合，两个阈值也回到接近原语义的取值。
#
# 用法：
#   .\fix_hullfill_v2.ps1            # 预览
#   .\fix_hullfill_v2.ps1 -Apply     # 打补丁（先做完整快照备份）
#   .\fix_hullfill_v2.ps1 -Restore   # 还原（用 fix_hullfill.ps1 的备份）
#
$ErrorActionPreference = 'Stop'

$A_NDC      = '    private static final float NDC_SANITY_LIMIT = 2.0f;'
$A_METHOD   = '    private static boolean writeHullFill(BufferBuilder builder, Matrix4f pose, java.util.List<BedrockCube> cubes) {'
$A_PROJ     = '        // 【26.2 取证】RenderSystem 已没有 getProjectionMatrix()——投影矩阵只以'
$A_PTSADD   = '                    pts.add(new float[]{ndcX, ndcY});'
$A_INV      = '        // 凸包顶点（NDC）逆投影回绘制空间，按退化四边形扇写出'
$A_EMIT     = '            emitNdcAsQuad(builder, invProj, p0, hull.get(i), hull.get(i + 1));'
$A_QUADDOC  = '    /** 把三个 NDC 点写成一个退化四边形（第 4 顶点重复），顺带回绘制空间。 */'
$A_QUADEND  = '        builder.addVertex(v.x(), v.y(), v.z());'
$A_CROSSDOC = '    /** 单调链叉积：(b−a)×(c−a) 的 z 分量。 */'

function Test-Bom([string]$p) {
    $fs = [System.IO.File]::OpenRead($p)
    try {
        $b = New-Object byte[] 3
        $n = $fs.Read($b, 0, 3)
        return ($n -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF)
    } finally { $fs.Close() }
}
function Test-Crlf([string]$p) {
    $b = [System.IO.File]::ReadAllBytes($p)
    for ($i = 0; $i -lt $b.Length; $i++) { if ($b[$i] -eq 13) { return $true } }
    return $false
}
function Read-SrcUtf8([string]$p) { return [System.IO.File]::ReadAllLines($p, [System.Text.Encoding]::UTF8) }
function Write-SrcUtf8([string]$p, [string[]]$lines, [bool]$bom, [bool]$crlf) {
    $enc = New-Object System.Text.UTF8Encoding($bom)
    $sw  = New-Object System.IO.StreamWriter($p, $false, $enc)
    try {
        if ($crlf) { $sw.NewLine = "`r`n" } else { $sw.NewLine = "`n" }
        foreach ($l in $lines) { $sw.WriteLine($l) }
    } finally { $sw.Close() }
}
function Find-Line([string[]]$lines, [string]$needle) {
    $h = New-Object System.Collections.Generic.List[int]
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $needle) { $h.Add($i) } }
    return $h
}
function Find-LineAfter([string[]]$lines, [string]$needle, [int]$from) {
    for ($i = $from; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $needle) { return $i } }
    return -1
}

if (-not (Test-Path -LiteralPath $Src)) {
    Write-Host "[FATAL] 找不到源文件：`n  $Src" -ForegroundColor Red
    exit 1
}
if ($Apply -and $Restore) {
    Write-Host "[FATAL] -Apply 和 -Restore 不能一起用。" -ForegroundColor Red
    exit 1
}
if ($Restore) {
    $dir  = Split-Path -Parent $Src
    $base = Split-Path -Leaf $Src
    $baks = @(Get-ChildItem -LiteralPath $dir -Filter ($base + '.bak-hull-*') -File | Sort-Object Name)
    if ($baks.Count -eq 0) {
        Write-Host "[FATAL] 没有找到 .bak-hull-* 备份，无法还原。" -ForegroundColor Red
        exit 1
    }
    $pick = $baks[$baks.Count - 1]
    Copy-Item -LiteralPath $pick.FullName -Destination $Src -Force
    $a = Read-SrcUtf8 $Src
    $t = ($a -join "`n")
    $nFn = 0
    $nRb = 0
    foreach ($l in $a) {
        if ($l.Contains('emitHullVertex')) { $nFn++ }
        if ($l.Contains('getProjectionMatrixBuffer().map')) { $nRb++ }
    }
    Write-Host "[OK] 已还原 -> $($pick.Name)"
    Write-Host ("     lines=" + $a.Count + "  emitHullVertex=" + $nFn + "（应为 0）  UBO读回=" + $nRb + "（应为 1，属 computeMaskBounds）")
    exit 0
}

$bomFlag  = Test-Bom  $Src
$crlfFlag = Test-Crlf $Src
$lines    = Read-SrcUtf8 $Src
$srcText  = ($lines -join "`n")
Write-Host ("[INFO] 源文件 : " + $Src)
Write-Host ("[INFO] 行数   : " + $lines.Count + "   BOM=" + $bomFlag + "   CRLF=" + $crlfFlag)

if ($srcText.Contains('emitHullVertex')) {
    Write-Host "[FATAL] 文件里已经存在 emitHullVertex —— v1 补丁还在。" -ForegroundColor Red
    Write-Host "        请先跑：  .\fix_hullfill.ps1 -Restore" -ForegroundColor Yellow
    exit 1
}

$iMethod = Find-Line $lines $A_METHOD
if ($iMethod.Count -ne 1) { Write-Host ("[FATAL] writeHullFill 锚点命中 " + $iMethod.Count + " 次（应为 1）。") -ForegroundColor Red; exit 1 }
$m = $iMethod[0]
$iProj = Find-LineAfter $lines $A_PROJ $m
if ($iProj -lt 0) { Write-Host "[FATAL] 找不到读回区块起点注释。" -ForegroundColor Red; exit 1 }
$iPts = Find-Line $lines $A_PTSADD
$iInv  = Find-Line $lines $A_INV
$iEmit = Find-Line $lines $A_EMIT
$iQD   = Find-Line $lines $A_QUADDOC
$iQE   = Find-Line $lines $A_QUADEND
$iCross = Find-Line $lines $A_CROSSDOC
$iNdc  = Find-Line $lines $A_NDC
foreach ($pair in @(
    @('pts.add', $iPts.Count), @('invProj 注释', $iInv.Count), @('emitNdcAsQuad 调用', $iEmit.Count),
    @('emitNdcAsQuad 文档', $iQD.Count), @('addVertex 收尾', $iQE.Count),
    @('单调链叉积文档', $iCross.Count), @('NDC_SANITY_LIMIT', $iNdc.Count))) {
    if ($pair[1] -ne 1) { Write-Host ("[FATAL] 锚点 '" + $pair[0] + "' 命中 " + $pair[1] + " 次（应为 1）。") -ForegroundColor Red; exit 1 }
}
if ($iPts[0] -le $iProj) { Write-Host "[FATAL] 锚点顺序异常。" -ForegroundColor Red; exit 1 }
if ($lines[$iQE[0] + 1] -cne '    }') {
    Write-Host "[FATAL] addVertex 收尾行的下一行不是 '    }'。" -ForegroundColor Red
    Write-Host ("        line " + ($iQE[0] + 2) + " : " + $lines[$iQE[0] + 1])
    exit 1
}
Write-Host ("[OK] 常量锚点   : line " + ($iNdc[0] + 1))
Write-Host ("[OK] 读回区块   : line " + ($iProj + 1) + " .. " + ($iPts[0] + 1) + "  （" + ($iPts[0] - $iProj + 1) + " 行）")
Write-Host ("[OK] invProj 段 : line " + ($iInv[0] + 1) + " .. " + ($iEmit[0] + 1))
Write-Host ("[OK] emit 区块  : line " + ($iQD[0] + 1) + " .. " + ($iQE[0] + 2))
Write-Host ("[OK] 新方法插入 : line " + ($iCross[0] + 1) + " 之前")

$NEWCONST = New-Object System.Collections.Generic.List[string]
$NEWCONST.Add('')
$NEWCONST.Add('    /** 折算屏幕方向时的最小「相机前方深度」；只用来挡住除零与贴近平面的点，不筛几何。 */')
$NEWCONST.Add('    private static final float HULL_MIN_DEPTH = 1.0e-4f;')
$NEWCONST.Add('')
$NEWCONST.Add('    /** 折算后方向坐标的绝对值上限；超出即判为近平面伪影（取代旧的 NDC_SANITY_LIMIT）。 */')
$NEWCONST.Add('    private static final float HULL_DIR_LIMIT = 3.0f;')

$NEW1 = New-Object System.Collections.Generic.List[string]
$NEW1.Add('        // 【不再读回投影 UBO】26.3 上 slice.map(true, false) 恒抛 "Buffer is not readable"，')
$NEW1.Add('        // 凸包路径因此从未真正生效过。改成纯 CPU 侧几何：')
$NEW1.Add('        // 顶点先进绘制空间（pose 已烘焙 ModelView），再折算成「屏幕方向」——')
$NEW1.Add('        // 即透视除法之后除以深度。投影对 xy 只多乘两个正常数，')
$NEW1.Add('        // 而凸包在正缩放下顶点一一对应，故选出的是同一批顶点。')
$NEW1.Add('        // 【关键】写出的仍是该顶点【自己】的绘制空间坐标，')
$NEW1.Add('        // 深度与形状都贴合真实目镜；dir 只用于排序与筛选。')
$NEW1.Add('        Vector4f tmp = new Vector4f();')
$NEW1.Add('        for (BedrockCube cube : cubes) {')
$NEW1.Add('            for (var polygon : cube.getPolygons()) {')
$NEW1.Add('                if (polygon == null) {')
$NEW1.Add('                    continue;')
$NEW1.Add('                }')
$NEW1.Add('                for (var vertex : polygon.vertices) {')
$NEW1.Add('                    tmp.set(vertex.pos.x() / 16.0F, vertex.pos.y() / 16.0F, vertex.pos.z() / 16.0F, 1.0F);')
$NEW1.Add('                    tmp.mul(pose);')
$NEW1.Add('                    float[] dir = toScreenDirection(tmp);')
$NEW1.Add('                    if (dir == null) {')
$NEW1.Add('                        continue;')
$NEW1.Add('                    }')
$NEW1.Add('                    pts.add(dir);')
# 三个 for 的闭合花括号在原文 699/700/701 行，不在替换区间内，保留 —— 这里不能再写。

$NEW2 = New-Object System.Collections.Generic.List[string]
$NEW2.Add('        // 每个凸包顶点写回它自己的原始绘制空间坐标：投影后 xy 精确落回')
$NEW2.Add('        // toScreenDirection 算出的方向，深度就是目镜本来的深度。')
$NEW2.Add('        // 于是既不需要做逆投影，也不会因为被压到某个统一深度平面而走形。')
$NEW2.Add('        float[] p0 = hull.get(0);')
$NEW2.Add('        for (int i = 1; i + 1 < hull.size(); i++) {')
$NEW2.Add('            emitHullQuad(builder, p0, hull.get(i), hull.get(i + 1));')
# 同理：for 的闭合花括号是原文 749 行，保留在替换区间外。

$NEW3 = New-Object System.Collections.Generic.List[string]
$NEW3.Add('    /** 把三个凸包顶点写成一个退化四边形（第 4 顶点重复）。 */')
$NEW3.Add('    private static void emitHullQuad(BufferBuilder builder, float[] a, float[] b, float[] c) {')
$NEW3.Add('        emitHullVertex(builder, a);')
$NEW3.Add('        emitHullVertex(builder, b);')
$NEW3.Add('        emitHullVertex(builder, c);')
$NEW3.Add('        emitHullVertex(builder, c);')
$NEW3.Add('    }')
$NEW3.Add('')
$NEW3.Add('    /** 凸包顶点：取该顶点【原始】的绘制空间坐标（dir 只用于凸包排序）。 */')
$NEW3.Add('    private static void emitHullVertex(BufferBuilder builder, float[] dir) {')
$NEW3.Add('        builder.addVertex(dir[2], dir[3], dir[4]);')
$NEW3.Add('    }')

$NEWFN = New-Object System.Collections.Generic.List[string]
$NEWFN.Add('    /**')
$NEWFN.Add('     * 把一个「绘制空间」点折算成屏幕方向，并把原始坐标一并带出来。')
$NEWFN.Add('     *')
$NEWFN.Add('     * <p>绘制空间 = 顶点经 pose（已烘焙 ModelView）变换后的坐标：相机在原点、看向 -z。')
$NEWFN.Add('     * 投影矩阵对 xy 只做「除以深度、再乘两个正常数」，所以 {@code (x / -z, y / -z)}')
$NEWFN.Add('     * 与真正的 NDC 只差一个正缩放；而凸包在正缩放下顶点一一对应。')
$NEWFN.Add('     * 于是可以在完全不知道 fov、宽高比、投影矩阵的前提下选出同一批凸包顶点，')
$NEWFN.Add('     * 也就不必把投影矩阵从 GPU 读回 CPU（26.3 上那步恒失败）。')
$NEWFN.Add('     *')
$NEWFN.Add('     * @return {@code {dirX, dirY, x, y, z}}；点不可用（相机后方、数值爆炸）时返回 null')
$NEWFN.Add('     */')
$NEWFN.Add('    @Nullable')
$NEWFN.Add('    private static float[] toScreenDirection(Vector4f v) {')
$NEWFN.Add('        float w = v.w();')
$NEWFN.Add('        if (Math.abs(w) <= 1.0e-6f) {')
$NEWFN.Add('            return null;')
$NEWFN.Add('        }')
$NEWFN.Add('        float x = v.x() / w;')
$NEWFN.Add('        float y = v.y() / w;')
$NEWFN.Add('        float z = v.z() / w;')
$NEWFN.Add('        // 相机看向 -z：depth = -z 就是到相机的距离。')
$NEWFN.Add('        float depth = -z;')
$NEWFN.Add('        // 写成 !(depth > MIN) 而不是 depth <= MIN，顺带把 NaN 也挡掉。')
$NEWFN.Add('        if (!(depth > HULL_MIN_DEPTH)) {')
$NEWFN.Add('            return null;')
$NEWFN.Add('        }')
$NEWFN.Add('        float dirX = x / depth;')
$NEWFN.Add('        float dirY = y / depth;')
$NEWFN.Add('        if (!Float.isFinite(dirX) || !Float.isFinite(dirY)')
$NEWFN.Add('                || Math.abs(dirX) > HULL_DIR_LIMIT || Math.abs(dirY) > HULL_DIR_LIMIT) {')
$NEWFN.Add('            return null;')
$NEWFN.Add('        }')
$NEWFN.Add('        return new float[]{dirX, dirY, x, y, z};')
$NEWFN.Add('    }')
$NEWFN.Add('')

$delta = $NEWCONST.Count + ($NEW1.Count - ($iPts[0] - $iProj + 1)) `
       + ($NEW2.Count - ($iEmit[0] - $iInv[0] + 1)) `
       + ($NEW3.Count - ($iQE[0] + 2 - $iQD[0])) `
       + $NEWFN.Count

Write-Host ""
Write-Host "===== 将要做的改动 =====" -ForegroundColor Cyan
Write-Host ("1) line " + ($iNdc[0] + 2) + " 之后插入 " + $NEWCONST.Count + " 行（两个常量）")
Write-Host ("2) line " + ($iProj + 1) + " .. " + ($iPts[0] + 1) + "  " + ($iPts[0] - $iProj + 1) + " 行 -> " + $NEW1.Count + " 行")
Write-Host ("3) line " + ($iInv[0] + 1) + " .. " + ($iEmit[0] + 1) + "  " + ($iEmit[0] - $iInv[0] + 1) + " 行 -> " + $NEW2.Count + " 行")
Write-Host ("4) line " + ($iQD[0] + 1) + " .. " + ($iQE[0] + 2) + "  " + ($iQE[0] + 2 - $iQD[0]) + " 行 -> " + $NEW3.Count + " 行")
Write-Host ("5) line " + ($iCross[0] + 1) + " 之前插入 " + $NEWFN.Count + " 行（toScreenDirection）")
Write-Host ("总行数变化 : " + $lines.Count + " -> " + ($lines.Count + $delta))
Write-Host "===== 结束 =====" -ForegroundColor Cyan
Write-Host ""

if (-not $Apply) {
    Write-Host "这是预览，未改动任何文件。确认后运行：  .\fix_hullfill_v2.ps1 -Apply" -ForegroundColor Green
    exit 0
}

$out = New-Object System.Collections.Generic.List[string]
$p = $iProj; $q = $iPts[0]; $r = $iInv[0]; $s = $iEmit[0]; $u = $iQD[0]; $v = $iQE[0] + 1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($i -eq $p) { foreach ($l in $NEW1) { $out.Add($l) }; $i = $q; continue }
    if ($i -eq $r) { foreach ($l in $NEW2) { $out.Add($l) }; $i = $s; continue }
    if ($i -eq $u) { foreach ($l in $NEW3) { $out.Add($l) }; $i = $v; continue }
    if ($i -eq $iCross[0]) { foreach ($l in $NEWFN) { $out.Add($l) } }
    $out.Add($lines[$i])
    if ($i -eq $iNdc[0]) { foreach ($l in $NEWCONST) { $out.Add($l) } }
}

$expect = $lines.Count + $delta
if ($out.Count -ne $expect) {
    Write-Host ("[FATAL] 行数不符：期望 " + $expect + "，实际 " + $out.Count + "。未写盘。") -ForegroundColor Red
    exit 1
}
$joined = ($out -join "`n")
if (-not $joined.Contains('toScreenDirection')) { Write-Host "[FATAL] toScreenDirection 丢失。未写盘。" -ForegroundColor Red; exit 1 }
if (-not $joined.Contains('emitHullQuad') -or -not $joined.Contains('emitHullVertex')) { Write-Host "[FATAL] emitHull* 丢失。未写盘。" -ForegroundColor Red; exit 1 }
if ($joined.Contains('emitNdcAsQuad') -or $joined.Contains('emitNdcVertex')) { Write-Host "[FATAL] 旧的 emitNdc* 仍有残留。未写盘。" -ForegroundColor Red; exit 1 }
if ($joined.Contains('invProj')) { Write-Host "[FATAL] invProj 仍有残留。未写盘。" -ForegroundColor Red; exit 1 }
if (-not $joined.Contains('dir[2], dir[3], dir[4]')) { Write-Host "[FATAL] emitHullVertex 未改用原始坐标。未写盘。" -ForegroundColor Red; exit 1 }
if (-not $joined.Contains('HULL_MIN_DEPTH = 1.0e-4f')) { Write-Host "[FATAL] 阈值未按 v2 取值。未写盘。" -ForegroundColor Red; exit 1 }

$wIdx = Find-Line $out.ToArray() $A_METHOD
$bodyEnd = Find-LineAfter $out.ToArray() '    private static void computeMaskBounds() {' $wIdx[0]
if ($bodyEnd -lt 0) { Write-Host "[FATAL] 找不到 computeMaskBounds。未写盘。" -ForegroundColor Red; exit 1 }
$bodyLines = New-Object System.Collections.Generic.List[string]
for ($i = $wIdx[0]; $i -lt $bodyEnd; $i++) { $bodyLines.Add($out[$i]) }
$body = ($bodyLines -join "`n")
if ($body.Contains('getProjectionMatrixBuffer') -or $body.Contains('MappedView')) {
    Write-Host "[FATAL] writeHullFill 里仍有 UBO 读回。未写盘。" -ForegroundColor Red
    exit 1
}
$scan = [regex]::Replace($joined, '"(\\.|[^"\\])*"', '""')
$scan = [regex]::Replace($scan, '//[^\n]*', '')
$scan = [regex]::Replace($scan, '/\*.*?\*/', '', [System.Text.RegularExpressions.RegexOptions]::Singleline)
$bd = ([regex]::Matches($scan, '\{')).Count - ([regex]::Matches($scan, '\}')).Count
$pd = ([regex]::Matches($scan, '\(')).Count - ([regex]::Matches($scan, '\)')).Count
if ($bd -ne 0 -or $pd -ne 0) {
    Write-Host ("[FATAL] 括号不平衡：brace=" + $bd + " paren=" + $pd + "。未写盘。") -ForegroundColor Red
    exit 1
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$bak   = $Src + '.bak-hull-' + $stamp
Copy-Item -LiteralPath $Src -Destination $bak -Force
Write-Host ("[OK] 完整快照备份 -> " + (Split-Path -Leaf $bak))
Write-SrcUtf8 $Src $out.ToArray() $bomFlag $crlfFlag
Write-Host ""
Write-Host ("[DONE] 已写盘。lines=" + $out.Count + "  brace=" + $bd + "  paren=" + $pd + "  BOM=" + $bomFlag + "  CRLF=" + $crlfFlag) -ForegroundColor Green
