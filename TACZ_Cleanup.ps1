# ============================================================
# TACZ 仓库远程清理脚本 v1
# 做四件事:
#   1. 删除误传的 scope-pip-latest.log
#   2. .gitignore 补上 *.bak* / _archive_*/ 防线(已存在则跳过)
#   3. 报告 gradle.properties 里的版本号(只报告, 不擅自改)
#   4. 提交并推送
# 每一步都有检查, 不达标的步骤会跳过并在最后汇总
# ============================================================

$ErrorActionPreference = 'Continue'

$root = 'C:\Users\FiVE\Documents\GitHub\TACZ-updated-fixed'
$log  = New-Object System.Collections.ArrayList

function Say([string]$s) {
  if ($s -eq $null) { $s = '' }
  Write-Output $s
  [void]$log.Add($s)
}

Say '================================================================'
Say ' TACZ 仓库远程清理'
Say (' 时间: ' + (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))
Say (' 目录: ' + $root)
Say '================================================================'

if (-not (Test-Path $root)) {
  Say '[中止] 仓库目录不存在: ' + $root
  exit 1
}

Set-Location $root

# ---------- 0. 先看 git 状态 ----------
Say ''
Say '--- 0. 当前 git 状态 ---'
$br = (& git rev-parse --abbrev-ref HEAD 2>$null) -join ''
$hd = (& git rev-parse --short HEAD 2>$null) -join ''
Say ('分支: ' + $br)
Say ('HEAD: ' + $hd)
$st = @(& git status --porcelain 2>$null)
Say ('工作区未提交条目: ' + $st.Count)

# ---------- 1. 删除误传的日志 ----------
Say ''
Say '--- 1. 删除 scope-pip-latest.log ---'
$stray = Join-Path $root 'scope-pip-latest.log'
if (Test-Path $stray) {
  $sz = (Get-Item $stray).Length
  & git rm --cached $stray 2>$null | Out-Null
  Remove-Item $stray -Force -ErrorAction SilentlyContinue
  Say ('已删除 (原 ' + $sz + ' 字节)')
} else {
  Say '本地不存在该文件, 跳过删除'
}
# 远程若仍有, 用 git rm --cached 已处理; 这里再确认一次索引
$inIdx = (& git ls-files --error-unmatch scope-pip-latest.log 2>$null)
if ($inIdx) {
  & git rm --cached scope-pip-latest.log --quiet 2>$null | Out-Null
  Say '已从 git 索引移除'
}

# ---------- 2. .gitignore 补防线 ----------
Say ''
Say '--- 2. 补 .gitignore 防线 ---'
$gi = Join-Path $root '.gitignore'
if (Test-Path $gi) {
  $giTxt = [System.IO.File]::ReadAllText($gi)
  $need = New-Object System.Collections.ArrayList
  if ($giTxt -notmatch '(?m)^\s*\*\.bak\*')      { [void]$need.Add('*.bak*') }
  if ($giTxt -notmatch '(?m)^\s*_archive_\*/')   { [void]$need.Add('_archive_*/') }
  if ($giTxt -notmatch '(?m)^\s*\*\.log\s*$')    { [void]$need.Add('*.log') }

  if ($need.Count -eq 0) {
    Say '三条规则都已存在, 未改动'
  } else {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append($giTxt)
    if (-not $giTxt.EndsWith("`n")) { [void]$sb.Append("`n") }
    [void]$sb.Append("`n")
    [void]$sb.Append('# TACZ 本地诊断产物(勿上传)' + "`n")
    foreach ($r in $need) {
      [void]$sb.Append($r + "`n")
      Say ('新增规则: ' + $r)
    }
    [System.IO.File]::WriteAllText($gi, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
    Say '.gitignore 已更新'
  }
} else {
  Say '[警告] 未找到 .gitignore, 跳过'
}

# ---------- 3. 版本号 ----------
Say ''
Say '--- 3. 版本号核对(只报告, 不修改) ---'
$gp = Join-Path $root 'gradle.properties'
if (Test-Path $gp) {
  $lines = @(Get-Content $gp -Encoding UTF8)
  foreach ($l in $lines) {
    $tl = $l.Trim()
    if ($tl.StartsWith('#')) { continue }
    if ($tl -match 'version' -or $tl -match 'Version') {
      Say ('  ' + $tl)
    }
  }
} else {
  Say '  [未找到 gradle.properties]'
}
Say ''
Say '  ↑ 请把上面这些版本号贴回去确认。'
Say '  README 里我写的是 1.1.8 + mc26.3，若与此处不一致需人工对齐。'

# ---------- 4. 提交并推送 ----------
Say ''
Say '--- 4. 提交并推送 ---'

# 先把所有改动加入索引(不含被 ignore 的)
& git add -A 2>$null | Out-Null
$st2 = @(& git status --porcelain 2>$null)
Say ('待提交条目: ' + $st2.Count)
$i = 0
while ($i -lt $st2.Count -and $i -lt 30) { Say ('   ' + $st2[$i]); $i++ }

if ($st2.Count -eq 0) {
  Say '没有需要提交的改动, 跳过 commit/push'
} else {
  $msg = 'chore: remove stray log, harden .gitignore against local diagnostic artifacts'
  $out = (& git commit -m $msg 2>&1) -join ' | '
  Say ('commit: ' + $out)

  $push = (& git push 2>&1) -join ' | '
  Say ('push:   ' + $push)

  $hd2 = (& git rev-parse --short HEAD 2>$null) -join ''
  Say ('新 HEAD: ' + $hd2)
}

Say ''
Say '================================================================'
Say ' 清理结束。请把上面全部输出贴回对话。'
Say '================================================================'
