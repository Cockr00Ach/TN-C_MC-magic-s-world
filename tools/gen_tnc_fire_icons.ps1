# Generates the 15 FIRE spell icons (16x16) plus the 5 burning EFFECT icons
# (18x18, same art as the spell). All ASCII only (tools/*.ps1 convention - a
# single Chinese character in a tool script gets mis-decoded as GBK and can
# swallow a newline, which silently breaks here-strings).
#
# Icons: <pack>\config\openloader\resources\TN-C\assets\tnc\textures\spell\<id>.png
# Effects: ...\textures\mob_effect\<id>.png (18x18, vanilla effect icon size)
#
# Placeholders on purpose: readable at 16x16, consistent family, easy to redraw.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$pack = Get-ChildItem (Join-Path $root 'modpack') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'config\openloader\resources\TN-C') } |
        Select-Object -First 1
if (-not $pack) { throw 'TN-C resource pack folder not found' }
$assets = Join-Path $pack.FullName 'config\openloader\resources\TN-C\assets\tnc'
$iconDir = Join-Path $assets 'textures\spell'
$effDir = Join-Path $assets 'textures\mob_effect'
New-Item -ItemType Directory -Force -Path $iconDir | Out-Null
New-Item -ItemType Directory -Force -Path $effDir | Out-Null

$DARK  = [System.Drawing.Color]::FromArgb(255, 0x7A, 0x14, 0x08)
$RED   = [System.Drawing.Color]::FromArgb(255, 0xD0, 0x38, 0x10)
$ORNG  = [System.Drawing.Color]::FromArgb(255, 0xF0, 0x82, 0x20)
$YEL   = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xD0, 0x50)
$WHITE = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xF6, 0xD8)
$CLEAR = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)

# ---------- shared drawing helpers ----------
$script:BMP = $null
$script:GFX = $null
function New-Icon {
    $script:BMP = New-Object System.Drawing.Bitmap(16, 16)
    $script:GFX = [System.Drawing.Graphics]::FromImage($script:BMP)
    $script:GFX.Clear($CLEAR)
}
function Px($x, $y, $c) {
    if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) { $script:GFX.FillRectangle((New-Object System.Drawing.SolidBrush $c), $x, $y, 1, 1) }
}
function Disc($cx, $cy, $r) {
    for ($y = -$r; $y -le $r; $y++) {
        for ($x = -$r; $x -le $r; $x++) {
            $d2 = $x * $x + $y * $y
            if ($d2 -gt $r * $r) { continue }
            if ($d2 -gt ($r - 1) * ($r - 1)) { Px ($cx + $x) ($cy + $y) $RED }
            elseif ($d2 -gt ($r - 2) * ($r - 2)) { Px ($cx + $x) ($cy + $y) $ORNG }
            elseif ($d2 -gt ($r - 4) * ($r - 4)) { Px ($cx + $x) ($cy + $y) $YEL }
            else { Px ($cx + $x) ($cy + $y) $WHITE }
        }
    }
}
function Flake($x, $y) {     # 3x5 flame
    Px $x ($y + 4) $DARK; Px ($x + 1) ($y + 4) $DARK; Px ($x + 2) ($y + 4) $DARK
    Px $x ($y + 3) $RED;  Px ($x + 1) ($y + 3) $RED;  Px ($x + 2) ($y + 3) $RED
    Px $x ($y + 2) $ORNG; Px ($x + 1) ($y + 2) $ORNG; Px ($x + 2) ($y + 2) $ORNG
    Px ($x + 1) ($y + 1) $YEL
    Px ($x + 1) $y $WHITE
}
function Save-Icon($name, $asEffect) {
    $script:GFX.Dispose()
    $p = Join-Path $iconDir "$name.png"
    $script:BMP.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $script:BMP.Dispose()
    $size = (Get-Item $p).Length
    if ($asEffect) {
        $img = [System.Drawing.Image]::FromFile($p)
        $e = New-Object System.Drawing.Bitmap(18, 18)
        $g2 = [System.Drawing.Graphics]::FromImage($e)
        $g2.Clear($CLEAR)
        $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g2.DrawImage($img, 1, 1, 16, 16)
        $g2.Dispose(); $img.Dispose()
        $e.Save((Join-Path $effDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $e.Dispose()
    }
    Write-Output ("  {0,-24} {1,5:N0} B{2}" -f "$name.png", $size, $(if ($asEffect) { '  +effect' } else { '' }))
}

# ---------- RAY chain: horizontal beam, thicker per tier, 3 beams at t3, burst at t4+ ----------
function Icon-Ray($name, $tier) {
    New-Icon
    $thick = 1 + $tier                      # 2..6 px tall
    $rows = if ($tier -ge 3) { @(3, 8, 13) } else { @(8) }
    foreach ($cy in $rows) {
        $half = [int][Math]::Floor($thick / 2)
        for ($dy = -$half; $dy -le $half; $dy++) {
            for ($x = 1; $x -le 12; $x++) {
                $c = if ([Math]::Abs($dy) -eq $half) { $DARK }
                     elseif ([Math]::Abs($dy) -le 0) { $WHITE }
                     else { $ORNG }
                Px $x ($cy + $dy) $c
            }
        }
    }
    if ($tier -ge 4) { Disc 14 8 (2 + $tier - 4) }      # explosion at the tip
    Save-Icon $name $false
}

# ---------- BALL chain: growing fireball; t4 = burst, t5 = meteor with a tail ----------
function Icon-Ball($name, $tier) {
    New-Icon
    if ($tier -eq 4) {
        Disc 8 8 7                                     # self destruct: whole icon is the blast
        for ($i = 0; $i -lt 8; $i++) {
            $a = $i * [Math]::PI / 4.0
            Px (8 + [int][Math]::Round(6 * [Math]::Cos($a))) (8 + [int][Math]::Round(6 * [Math]::Sin($a))) $WHITE
        }
    } elseif ($tier -eq 5) {
        Disc 8 10 5                                    # meteor: ball low + tail up
        for ($y = 0; $y -lt 6; $y++) { Px 8 $y $ORNG; Px 7 $y $RED; Px 9 $y $RED }
    } else {
        Disc 8 8 (3 + $tier)
    }
    Save-Icon $name $false
}

# ---------- BURN chain: one flame per tier level, plus a halo at t3+ ----------
function Icon-Burn($name, $tier) {
    New-Icon
    $xs = if ($tier -ge 4) { @(1, 6, 11) } elseif ($tier -eq 3) { @(2, 7, 12) } elseif ($tier -eq 2) { @(3, 9) } else { @(6) }
    foreach ($x in $xs) { Flake $x 5 }
    if ($tier -ge 3) {                                  # revive halo
        for ($a = 0; $a -lt 360; $a += 20) {
            $rad = $a * [Math]::PI / 180.0
            Px (8 + [int][Math]::Round(7 * [Math]::Cos($rad))) (8 + [int][Math]::Round(7 * [Math]::Sin($rad))) $YEL
        }
    }
    if ($tier -eq 5) { Px 8 0 $WHITE; Px 7 1 $WHITE; Px 9 1 $WHITE }
    Save-Icon $name $true                               # burning chain are all effects
}

# ---------- RAY chain (2026-10-05 redesign): each tier gets its OWN icon ----------
function HLine($x0, $x1, $y, $c) { for ($x = $x0; $x -le $x1; $x++) { Px $x $y $c } }
function VLine($x, $y0, $y1, $c) { for ($y = $y0; $y -le $y1; $y++) { Px $x $y $c } }

# t1 烈阳射线: a 3px thick white-hot beam + a small sun at the muzzle
function Icon-SunRay {
    New-Icon
    HLine 0 8 7 $ORNG; HLine 0 8 8 $WHITE; HLine 0 8 9 $ORNG
    Px 0 6 $YEL; Px 0 10 $YEL; Px 4 5 $YEL; Px 4 11 $YEL
    Disc 12 8 3
    Px 12 3 $YEL; Px 12 13 $YEL; Px 7 8 $YEL; Px 15 8 $YEL
    Save-Icon 'sun_ray' $false
}

# t2 爆炸射线: same beam, but it ends in a burst
function Icon-BlastRay {
    New-Icon
    HLine 0 7 7 $ORNG; HLine 0 7 8 $WHITE; HLine 0 7 9 $ORNG
    Disc 11 8 4
    for ($a = 0; $a -lt 360; $a += 45) {
        $r = $a * [Math]::PI / 180.0
        for ($k = 5; $k -le 7; $k++) {
            Px (11 + [int][Math]::Round($k * [Math]::Cos($r))) (8 + [int][Math]::Round($k * [Math]::Sin($r))) $YEL
        }
    }
    Save-Icon 'blast_ray' $false
}

# t3 火龙术: dragon head in profile facing right
#   (楔形上颌 + 吻端 + 张开的下颌留缝 + 牙 + 眼 + 后翘的角 + 颈后火苗)
function Icon-FireDragon {
    New-Icon
    HLine 4 9 4 $DARK
    HLine 3 10 5 $RED
    HLine 3 11 6 $RED
    HLine 3 12 7 $ORNG
    HLine 3 12 8 $ORNG
    HLine 4 11 9 $RED
    HLine 5 10 10 $DARK
    HLine 11 15 6 $DARK
    HLine 12 15 7 $ORNG
    Px 15 8 $DARK
    HLine 6 12 11 $DARK
    HLine 7 11 12 $RED
    Px 12 12 $ORNG
    Px 9 10 $WHITE; Px 11 10 $WHITE
    Px 7 6 $WHITE; Px 8 6 $YEL
    Px 4 3 $YEL; Px 5 2 $YEL; Px 6 2 $ORNG; Px 5 3 $ORNG
    Px 2 9 $DARK; Px 2 10 $DARK; Px 1 10 $YEL
    Px 0 6 $YEL; Px 1 6 $ORNG; Px 0 7 $ORNG; Px 1 7 $RED; Px 0 8 $RED
    Save-Icon 'fire_dragon' $false
}
# t4 炎魔龙之怒: signpost of the field - 2 obsidian pillars with sun orbs on top + ground sigil
function Icon-FlameDemonWrath {
    New-Icon
    # two pillars
    for ($y = 6; $y -le 13; $y++) { Px 3 $y $DARK; Px 4 $y $DARK; Px 11 $y $DARK; Px 12 $y $DARK }
    VLine 3 6 13 $DARK
    for ($y = 7; $y -le 12; $y++) { Px 3 $y $RED; Px 12 $y $RED }
    # orbs floating above the pillars
    Disc 4 3 2
    Disc 11 3 2
    Px 4 0 $YEL; Px 11 0 $YEL
    # ground sigil
    HLine 1 14 14 $ORNG
    Px 0 13 $DARK; Px 15 13 $DARK; Px 7 15 $YEL; Px 8 15 $YEL
    # centre heat
    Px 7 10 $YEL; Px 8 10 $YEL; Px 7 11 $WHITE; Px 8 11 $WHITE
    Save-Icon 'flame_demon_wrath' $false
}

# t5 太阳の审判: a big sun overhead raining many rays down onto a sigil
function Icon-SolarJudgment {
    New-Icon
    Disc 8 3 4
    # corona
    Px 8 0 $YEL; Px 3 1 $YEL; Px 13 1 $YEL; Px 0 3 $YEL; Px 15 3 $YEL
    # rain of rays
    foreach ($x in @(2, 5, 8, 11, 14)) { VLine $x 8 12 $ORNG; Px $x 13 $YEL }
    # sigil on the ground
    HLine 0 15 14 $ORNG
    Px 0 13 $DARK; Px 15 13 $DARK; Px 7 15 $WHITE; Px 8 15 $WHITE
    Px 4 15 $YEL; Px 11 15 $YEL
    Save-Icon 'solar_judgment' $false
}
# ---------- the 15 ----------
Write-Output 'RAY chain:'
Icon-SunRay
Icon-BlastRay
Icon-FireDragon
Icon-FlameDemonWrath
Icon-SolarJudgment
Write-Output 'BALL chain:'
Icon-Ball 'fireball' 1
Icon-Ball 'great_fireball' 2
Icon-Ball 'lava_fireball' 3   # renamed 2026-10-05 (giant_fireball -> lava_fireball)
Icon-Ball 'molten_skyfall' 4   # renamed 2026-10-05
Icon-Ball 'meteor_fall' 5     # renamed 2026-10-05
Write-Output 'BURN chain (+ effect icons):'
Icon-Burn 'fire_aspect' 1
Icon-Burn 'ember_burn' 2
Icon-Burn 'blaze_burn' 3
Icon-Burn 'inferno_burn' 4
Icon-Burn 'total_burn' 5

Write-Output ''
Write-Output ('done -> ' + $iconDir)
