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

# ---------- the 15 ----------
Write-Output 'RAY chain:'
Icon-Ray 'fire_ray' 1
Icon-Ray 'thick_fire_ray' 2
Icon-Ray 'triple_fire_ray' 3
Icon-Ray 'explosive_fire_ray' 4
Icon-Ray 'cataclysm_fire_ray' 5
Write-Output 'BALL chain:'
Icon-Ball 'fireball' 1
Icon-Ball 'great_fireball' 2
Icon-Ball 'giant_fireball' 3
Icon-Ball 'self_destruct' 4
Icon-Ball 'meteor_fireball' 5
Write-Output 'BURN chain (+ effect icons):'
Icon-Burn 'fire_aspect' 1
Icon-Burn 'ember_burn' 2
Icon-Burn 'blaze_burn' 3
Icon-Burn 'inferno_burn' 4
Icon-Burn 'total_burn' 5

Write-Output ''
Write-Output ('done -> ' + $iconDir)
