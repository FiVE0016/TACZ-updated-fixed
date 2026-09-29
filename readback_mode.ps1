#Requires -Version 5.1
#
# readback_mode.ps1 -- 缺陷 5 探针：验证 mode uniform 到底有没有活到作画
#
# 用法（在源码所在目录或任意位置运行）：
#   .\readback_mode.ps1                 # 默认 = Preview，只打印，不改文件
#   .\readback_mode.ps1 -Apply          # 真正打补丁（会先做完整快照备份）
#   .\readback_mode.ps1 -Restore        # 从最新的 .bak-rb-* 还原
#
# 改两处：
#   1) 字段区（DIAG_DONE 之后）插入  private static int DIAG_RB_COUNT = 0;
#   2) writeScopeMaskState() 末尾的「写 uniform + 绑纹理」两行，包成 读before -> 写 -> 读after
#      的 if/else，并把 before/after/当前程序打进日志。
#
[CmdletBinding()]
param(
    [string]$Src = "C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed\common\src\main\java\com\tacz\guns\compat\iris\IrisScopeMaskState.java",
    [switch]$Apply,
    [switch]$Restore
)

$ErrorActionPreference = 'Stop'

# ---------------- 常量：锚点与标记 ----------------
$MARK_RB      = '[TACZ Scope][rb]'
$MARK_COUNT   = 'DIAG_RB_COUNT'
$ANCHOR_FIELD = '    private static final java.util.Set<Integer> DIAG_DONE = new java.util.HashSet<>();'
$ANCHOR_W1    = '        GL20C.glUniform1i(modeLocation, mode);'
$ANCHOR_W2    = '        bindMaskTexture(unit, textureId);'

# ---------------- 小工具 ----------------
function Test-Bom([string]$p) {
    $fs = [System.IO.File]::OpenRead($p)
    try {
        $b = New-Object byte[] 3
        $n = $fs.Read($b, 0, 3)
        return ($n -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF)
    } finally { $fs.Close() }
}

function Read-SrcUtf8([string]$p) {
    return [System.IO.File]::ReadAllLines($p, [System.Text.Encoding]::UTF8)
}

function Write-SrcUtf8([string]$p, [string[]]$lines, [bool]$bom) {
    $enc = New-Object System.Text.UTF8Encoding($bom)
    $sw  = New-Object System.IO.StreamWriter($p, $false, $enc)
    try { foreach ($l in $lines) { $sw.WriteLine($l) } } finally { $sw.Close() }
}

function Find-Line([string[]]$lines, [string]$needle) {
    $hits = New-Object System.Collections.Generic.List[int]
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -ceq $needle) { $hits.Add($i) }
    }
    return $hits
}

function Count-Literal([string[]]$lines, [string]$needle) {
    $c = 0
    foreach ($l in $lines) { if ($l.Contains($needle)) { $c++ } }
    return $c
}

# ---------------- 前置检查 ----------------
if (-not (Test-Path -LiteralPath $Src)) {
    Write-Host "[FATAL] 找不到源文件：`n  $Src" -ForegroundColor Red
    exit 1
}
if ($Apply -and $Restore) {
    Write-Host "[FATAL] -Apply 和 -Restore 不能一起用。" -ForegroundColor Red
    exit 1
}

# ---------------- Restore ----------------
if ($Restore) {
    $dir = Split-Path -Parent $Src
    $base = Split-Path -Leaf $Src
    $baks = @(Get-ChildItem -LiteralPath $dir -Filter ($base + '.bak-rb-*') -File | Sort-Object Name)
    if ($baks.Count -eq 0) {
        Write-Host "[FATAL] 没有找到 .bak-rb-* 备份，无法还原。" -ForegroundColor Red
        exit 1
    }
    $pick = $baks[$baks.Count - 1]
    Copy-Item -LiteralPath $pick.FullName -Destination $Src -Force
    $after = Read-SrcUtf8 $Src
    Write-Host ("[OK] 已还原 -> " + $pick.Name)
    Write-Host ("     lines=" + $after.Count + "  rb=" + (Count-Literal $after $MARK_RB) + "  count=" + (Count-Literal $after $MARK_COUNT))
    exit 0
}

# ---------------- 读入 ----------------
$bomFlag = Test-Bom $Src
$lines   = Read-SrcUtf8 $Src
$srcText = ($lines -join "`n")
Write-Host ("[INFO] 源文件 : " + $Src)
Write-Host ("[INFO] 行数   : " + $lines.Count + "   BOM=" + $bomFlag)

if ($srcText.Contains($MARK_RB)) {
    Write-Host "[FATAL] 目标文件里已经存在 '$MARK_RB'，补丁已打过或没清干净。" -ForegroundColor Red
    Write-Host "        请先跑  .\readback_mode.ps1 -Restore  或手动还原干净版。"
    exit 1
}

# ---------------- 定位锚点 ----------------
$hf = Find-Line $lines $ANCHOR_FIELD
$w1 = Find-Line $lines $ANCHOR_W1
if ($hf.Count -ne 1) {
    Write-Host ("[FATAL] 字段锚点命中 " + $hf.Count + " 次（应为 1）：" + $ANCHOR_FIELD.Trim()) -ForegroundColor Red
    exit 1
}
if ($w1.Count -ne 1) {
    Write-Host ("[FATAL] 写入锚点命中 " + $w1.Count + " 次（应为 1）：" + $ANCHOR_W1.Trim()) -ForegroundColor Red
    exit 1
}
$i = $w1[0]
if (($i + 1) -ge $lines.Count -or $lines[$i + 1] -cne $ANCHOR_W2) {
    Write-Host "[FATAL] 写入锚点的下一行不是 bindMaskTexture(unit, textureId);，源码已变化，脚本拒绝猜测。" -ForegroundColor Red
    Write-Host ("        line " + ($i + 1) + " : " + $lines[$i])
    Write-Host ("        line " + ($i + 2) + " : " + $lines[$i + 1])
    exit 1
}

Write-Host ("[OK] 字段锚点 : line " + ($hf[0] + 1))
Write-Host ("[OK] 写入锚点 : line " + ($i + 1) + " + " + ($i + 2))

# ---------------- 构造新块（逐行 Add，杜绝数组折叠 / 注释吞代码） ----------------
$block = New-Object System.Collections.Generic.List[string]
$block.Add('        if (DIAG_RB_COUNT < 300) {')
$block.Add('            DIAG_RB_COUNT++;')
$block.Add('            int[] rbBefore = new int[1];')
$block.Add('            GL20C.glGetUniformiv(programId, modeLocation, rbBefore);')
$block.Add('            GL20C.glUniform1i(modeLocation, mode);')
$block.Add('            bindMaskTexture(unit, textureId);')
$block.Add('            int[] rbAfter = new int[1];')
$block.Add('            GL20C.glGetUniformiv(programId, modeLocation, rbAfter);')
$block.Add('            int rbCur = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);')
$block.Add('            GunMod.LOGGER.info("[TACZ Scope][rb] program={} modeLoc={} wrote={} before={} after={} current={}", programId, modeLocation, mode, rbBefore[0], rbAfter[0], rbCur);')
$block.Add('        } else {')
$block.Add('            GL20C.glUniform1i(modeLocation, mode);')
$block.Add('            bindMaskTexture(unit, textureId);')
$block.Add('        }')

$fieldLine = '    private static int DIAG_RB_COUNT = 0;'

Write-Host ""
Write-Host "===== 将要插入的内容 =====" -ForegroundColor Cyan
Write-Host ("@line " + ($hf[0] + 2) + "  +1 行：")
Write-Host ("  " + $fieldLine) -ForegroundColor Yellow
Write-Host ("@line " + ($i + 1) + "  替换 2 行 -> " + $block.Count + " 行（净 +" + ($block.Count - 2) + "）：")
foreach ($b in $block) { Write-Host ("  " + $b) -ForegroundColor Yellow }
Write-Host ("总行数变化 : " + $lines.Count + " -> " + ($lines.Count + 1 + $block.Count - 2))
Write-Host "===== 结束 =====" -ForegroundColor Cyan
Write-Host ""

if (-not $Apply) {
    Write-Host "这是预览。确认无误后运行：  .\readback_mode.ps1 -Apply" -ForegroundColor Green
    exit 0
}

# ---------------- Apply ----------------
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$bak   = $Src + '.bak-rb-' + $stamp
Copy-Item -LiteralPath $Src -Destination $bak -Force
Write-Host ("[OK] 完整快照备份 -> " + (Split-Path -Leaf $bak))

$out = New-Object System.Collections.Generic.List[string]
for ($k = 0; $k -lt $lines.Count; $k++) {
    if ($k -eq $i) {
        foreach ($b in $block) { $out.Add($b) }   # 替换 418/419 两行
    } elseif ($k -eq ($i + 1)) {
        continue                                   # 原 bindMaskTexture 行已被块包含
    } else {
        $out.Add($lines[$k])
    }
    if ($k -eq $hf[0]) { $out.Add($fieldLine) }
}

# ---------------- 折叠/完整性门禁 ----------------
$expect = $lines.Count + 1 + ($block.Count - 2)
if ($out.Count -ne $expect) {
    Write-Host ("[FATAL] 行数不符合预期：期望 " + $expect + "，实际 " + $out.Count + "。未写盘。") -ForegroundColor Red
    exit 1
}
$joined = ($out -join "`n")
$nRb    = 0
$nCnt   = 0
foreach ($l in $out) { if ($l.Contains($MARK_RB)) { $nRb++ }; if ($l.Contains($MARK_COUNT)) { $nCnt++ } }
if ($nRb -ne 1 -or $nCnt -ne 3) {
    Write-Host ("[FATAL] 标记数不对：rb=" + $nRb + "（应 1），DIAG_RB_COUNT=" + $nCnt + "（应 3：字段声明 + 阈值判断 + 自增）。未写盘。") -ForegroundColor Red
    exit 1
}
if ($joined.Contains('int[] rbBefore') -eq $false -or $joined.Contains('glGetUniformiv') -eq $false) {
    Write-Host "[FATAL] 关键代码行丢失（疑似被注释吞掉）。未写盘。" -ForegroundColor Red
    exit 1
}

Write-SrcUtf8 $Src $out.ToArray() $bomFlag
Write-Host ""
Write-Host ("[DONE] 已写盘。lines=" + $out.Count + "  rb=1  DIAG_RB_COUNT=3  BOM=" + $bomFlag) -ForegroundColor Green
Write-Host ""
Write-Host "下一步（一次一件事）："
Write-Host "  1) 构建：  gradlew build"
Write-Host "  2) 开镜，日志里找  [TACZ Scope][rb]"
Write-Host "     after=1  -> 值确实写进去了，问题在更后面（或掩码内容本身）"
Write-Host "     after=0  -> glUniform1i 没生效，location 或 program 不对"
Write-Host "     current<>program -> 写进了别的程序"
Write-Host "  3) 撤销：  .\readback_mode.ps1 -Restore"
