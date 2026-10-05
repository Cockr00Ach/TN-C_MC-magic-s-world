# tools/gen_dragon_orbit.ps1
#
# 生成光龙的「环绕」动画（作者 2026-10-03：“三条龙头对尾绕成一个圆接在一起”）：
#   把身体从鼻子到尾巴**均匀弯成 360/count 度的弧**（count=3 ⇒ 120°）⇒
#   整条龙正好贴在一个半径 r = 体长 × count / 2π 的圆上 ⇒ 三条首尾相接围成一个整圆。
#
# 每一节转多少度：这一节自己的弧长占全长的比例 × 总角度（= 这一节弦的"中点切线"方向）。
# 逐节累加即得到"每节朝向"，两节朝向之差 = 这一节要多转的角度 —— 这就是写进动画的值。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_dragon_orbit.ps1            # 写进 dragon.animation.json
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_dragon_orbit.ps1 -DryRun    # 只看数、不改文件
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_dragon_orbit.ps1 -Count 4   # 换条数（环半径会跟着变）
#
# ★ 只会**加/换**一个 "orbit" 动画，dash / idle 一个字节都不动（靠括号配对删旧的 orbit，不重新格式化整个文件）。
# ★ 生成完会自己验一遍：把整条身体的多边形算出来，量每个顶点到拟合圆的距离（应该 < 0.6 单位 = 1 厘米）。
# ★ 本文件必须存成 **UTF-8 with BOM** ✗ —— Windows PowerShell 5.1 不认"无 BOM 的 UTF-8"，
#    会把中文注释按 GBK 读、读出一堆乱码引号，脚本直接语法错 ✗（改完这个文件记得补回 BOM ✓）。
#
param(
    [int]$Count = 3,
    [double]$Direction = 1.0,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$geoPath = Join-Path $root 'src\main\resources\assets\tnc\geo\entity\dragon.geo.json'
$animPath = Join-Path $root 'src\main\resources\assets\tnc\animations\entity\dragon.animation.json'
$inv = [System.Globalization.CultureInfo]::InvariantCulture

$geo = Get-Content $geoPath -Raw | ConvertFrom-Json
$geoBones = $geo.'minecraft:geometry'[0].bones

# pivot z（模型单位）按骨头名存一份；顺手量出整条龙的 z 范围（鼻尖 / 尾尖）
$pivotZ = @{}
$zMin = [double]::MaxValue
$zMax = [double]::MinValue
foreach ($b in $geoBones) {
    $pivotZ[$b.name] = [double]$b.pivot[2]
    foreach ($c in $b.cubes) {
        $a = [double]$c.origin[2]
        $e = $a + [double]$c.size[2]
        if ($a -lt $zMin) { $zMin = $a }
        if ($e -gt $zMax) { $zMax = $e }
    }
}

# 从鼻子到尾巴的骨链（body1 是"身体切线 = 实体朝向"的那一节 = 环上的参考点）
$chain = @(
    'head', 'neck2', 'neck1', 'body1',
    'body2', 'body3', 'body4', 'body5', 'body6', 'body7',
    'body8', 'body9', 'body10', 'body11', 'body12',
    'tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tailFin'
)

# 每一节的长度（沿 z 从鼻尖走到尾尖）：
#   顶点 = 鼻尖 + 骨链上所有 pivot（去重：neck1 和 body1 在同一个 z ✓）+ 尾尖 ⇒ 正好 21 段 = 21 块骨头 ✓
$verts = @($zMin) + @($chain | ForEach-Object { $pivotZ[$_] } | Sort-Object -Unique) + @($zMax)
if ($verts.Count -ne $chain.Count + 1) {
    throw ("顶点数不对：{0} 个（应该是骨链 {1} + 1）" -f $verts.Count, $chain.Count)
}
$len = @()
$uStart = @()
for ($i = 0; $i -lt $chain.Count; $i++) {
    $uStart += ($verts[$i] - $zMin)
    $len += ($verts[$i + 1] - $verts[$i])
}
$total = $verts[$verts.Count - 1] - $verts[0]

# 弯曲中心 = body1 的 pivot（那里"身体切线 = 实体朝向"）
$curlU = $uStart[$chain.IndexOf('body1')]
$theta = 360.0 / $Count                                       # 每条龙要占的圆心角
$radius = $total * $Count / (2.0 * [math]::PI)                # 模型单位（16 单位 = 1 格）
$perUnit = $theta / $total

# 每节的"中点切线"方向 = 累加朝向；两节之差 = 这一节要转的角度
$acc = @()
for ($i = 0; $i -lt $chain.Count; $i++) {
    $mid = $uStart[$i] + $len[$i] / 2.0
    $acc += ($curlU - $mid) * $perUnit
}

# 父级：head/neck2/neck1 的父级在**后面**（i+1），body1 的父级是 root（0），其余父级是前一节
$rot = @{}
for ($i = 0; $i -lt $chain.Count; $i++) {
    if ($i -lt 3) { $parentAcc = $acc[$i + 1] }
    elseif ($i -eq 3) { $parentAcc = 0.0 }
    else { $parentAcc = $acc[$i - 1] }
    $rot[$chain[$i]] = ($acc[$i] - $parentAcc) * $Direction
}

# ---- 自检：把身体的多边形算出来，量每个顶点离拟合圆多远 ----
$pts = @()
$px = 0.0
$pz = 0.0
$pts += , @($px, $pz)
for ($i = 0; $i -lt $chain.Count; $i++) {
    $a = [math]::PI * $acc[$i] / 180.0
    $px += [math]::Sin($a) * $len[$i]
    $pz += [math]::Cos($a) * $len[$i]
    $pts += , @($px, $pz)
}
$a0 = $pts[0]
$a1 = $pts[[int]($pts.Count / 2)]
$a2 = $pts[$pts.Count - 1]
$d = 2.0 * ($a0[0] * ($a1[1] - $a2[1]) + $a1[0] * ($a2[1] - $a0[1]) + $a2[0] * ($a0[1] - $a1[1]))
$ux = (($a0[0] * $a0[0] + $a0[1] * $a0[1]) * ($a1[1] - $a2[1]) + ($a1[0] * $a1[0] + $a1[1] * $a1[1]) * ($a2[1] - $a0[1]) + ($a2[0] * $a2[0] + $a2[1] * $a2[1]) * ($a0[1] - $a1[1])) / $d
$uy = (($a0[0] * $a0[0] + $a0[1] * $a0[1]) * ($a2[0] - $a1[0]) + ($a1[0] * $a1[0] + $a1[1] * $a1[1]) * ($a0[0] - $a2[0]) + ($a2[0] * $a2[0] + $a2[1] * $a2[1]) * ($a1[0] - $a0[0])) / $d
$devMax = 0.0
foreach ($p in $pts) {
    $rr = [math]::Sqrt(($p[0] - $ux) * ($p[0] - $ux) + ($p[1] - $uy) * ($p[1] - $uy))
    $dev = [math]::Abs($rr - $radius)
    if ($dev -gt $devMax) { $devMax = $dev }
}

Write-Host ("骨链 {0} 节, 全长 {1:N1} 单位 = {2:N2} 格" -f $chain.Count, $total, ($total / 16.0))
Write-Host ("圆心角 {0:N1}°, 环半径 {1:N1} 单位 = {2:N2} 格 (scale=1) / {3:N2} 格 (scale=0.30)" -f $theta, $radius, ($radius / 16.0), ($radius / 16.0 * 0.30))
Write-Host ("自检: 顶点离拟合圆最大偏差 {0:N3} 单位 (= {1:N4} 格 @scale0.30)" -f $devMax, ($devMax / 16.0 * 0.30))
foreach ($n in $chain) {
    Write-Host ("  {0,-8} {1,8:N2}°" -f $n, $rot[$n])
}

# ---- 拼 orbit 动画（静态姿势：环永远是闭的）----
$tab = [string][char]9
$lines = @()
$lines += '"orbit": {'
$lines += "$tab$tab$tab`"loop`": true,"
$lines += "$tab$tab$tab`"animation_length`": 3.0,"
$lines += "$tab$tab$tab`"bones`": {"
for ($i = 0; $i -lt $chain.Count; $i++) {
    $comma = if ($i -lt $chain.Count - 1) { ',' } else { '' }
    $val = [string]::Format($inv, '{0:0.00}', $rot[$chain[$i]])
    $lines += "$tab$tab$tab$tab`"$($chain[$i])`": { `"rotation`": [0, $val, 0] }$comma"
}
$lines += "$tab$tab$tab}"
$lines += "$tab$tab}"
# ★ 这个文件是 **LF** 行尾 ✓（作者手写的 Bedrock 文件 ✓）—— 不要写成 CRLF ✗，免得一个文件里混着两套行尾 ✗
$orbitJson = ($lines -join "`n")

if ($DryRun) {
    Write-Host '----- orbit 动画（未写入）-----'
    Write-Host $orbitJson
    return
}

$text = [System.IO.File]::ReadAllText($animPath)

# 先删掉旧的 orbit（括号配对，别动别的字节）
$marker = '"orbit"'
$at = $text.IndexOf($marker)
if ($at -ge 0) {
    $open = $text.IndexOf('{', $at)
    $depth = 0
    $end = -1
    for ($i = $open; $i -lt $text.Length; $i++) {
        $ch = $text[$i]
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') {
            $depth--
            if ($depth -eq 0) { $end = $i; break }
        }
    }
    if ($end -lt 0) { throw 'orbit 动画的括号不配对，先别改文件' }
    # 连同后面可能跟着的逗号/空白一起吃掉
    $after = $end + 1
    while ($after -lt $text.Length -and ($text[$after] -eq ',' -or $text[$after] -eq ' ' -or $text[$after] -eq $tab -or $text[$after] -eq "`r" -or $text[$after] -eq "`n")) { $after++ }
    $text = $text.Substring(0, $at) + $text.Substring($after)
    Write-Host '（已替换掉旧的 orbit 动画）'
}

# 再插到 "animations": { 后面
$anchor = '"animations"'
$ai = $text.IndexOf($anchor)
if ($ai -lt 0) { throw '找不到 "animations"' }
$brace = $text.IndexOf('{', $ai)
$head = $text.Substring(0, $brace + 1)
$tail = $text.Substring($brace + 1)
$text = $head + "`n$tab$tab" + $orbitJson + ',' + $tail

[System.IO.File]::WriteAllText($animPath, $text, [System.Text.UTF8Encoding]::new($false))
Write-Host ("已写入 {0}" -f $animPath)
