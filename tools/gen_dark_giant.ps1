<#
    gen_dark_giant.ps1 -- assets for the dark-element boss "giant beast-man lord" (巨兽人领主)

    Produces the three data files of the boss in one deterministic pass:
      1) src/main/resources/assets/tnc/geo/entity/dark_giant.geo.json
      2) src/main/resources/assets/tnc/textures/entity/dark_giant_bedrock.png   (128x128 RGBA)
      3) src/main/resources/assets/tnc/animations/entity/dark_giant.animation.json (empty on purpose)

    Author's ask (2026-10-03): a giant beast-man lord whose armour is BAKED INTO THE MODEL --
    chest plate, abdominal plates, spiked pauldrons, bracers, greaves, boots, a belt with a big
    buckle and a dark cape are all extra cubes on the same bones, never separate items.

    WHY THE TEXTURE IS GENERATED AND NOT PAINTED BY HAND
    Every cube is painted with the same box-UV maths the game renderer samples with:
        up = (u+d, v)          down = (u+d+w, v)
        -X = (u, v+d)          -Z = (u+d, v+d)      +X = (u+d+w, v+d)      +Z = (u+d+w+d, v+d)
    for a cube of size (w,h,d) at texture offset (u,v). The atlas is packed in two passes:
    pass 1 measures every cube's footprint (2*(d+w) wide, d+h tall) and places it with a
    skyline packer, pass 2 paints it. Move a cube in the table below, re-run, and the atlas
    re-packs itself -- nothing here is hand-maintained.

    A NOTE ON THE ATLAS BUDGET (why this model is not more detailed)
    128x128 is 16384 pixels, and box UV costs roughly twice a model's surface area. A 4.5-block
    giant has ~38,000 square units of surface (16x an adult humanoid), so the full-detail
    version of this boss needs ~29,000 atlas pixels -- about 1.8x the whole atlas. The table
    below is the biggest version that actually fits: the deep parts (chest, thigh, shin, boot,
    waist) are cut into two stacked cubes, which costs k * 2*(d+w) * ceil(d+h/k) instead of
    2*(d+w) * (d+h), i.e. close to half. If the author wants the full detail, the texture has to
    grow to 256x256 -- see the trailer comment at the bottom of this file.

    MODEL SPACE (same convention as this repo's other bosses)
    +Y up, feet at y=0, FRONT = -Z (yan.geo.json's hairBangs sits at -Z and that model is
    verified in game). Model right = -X, model left = +X. Units are 1/16 block.

    ASCII only on purpose (no BOM needed on a GBK host). Deterministic: every random detail
    comes from a named seed, so two runs produce byte-identical files.

    Usage:  powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\gen_dark_giant.ps1
#>
param(
    [string]$Repo = '',
    [int]$TexSize = 128
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not $Repo) { $Repo = Split-Path $PSScriptRoot -Parent }
$GEO_OUT = Join-Path $Repo 'src\main\resources\assets\tnc\geo\entity\dark_giant.geo.json'
$TEX_OUT = Join-Path $Repo 'src\main\resources\assets\tnc\textures\entity\dark_giant_bedrock.png'
$ANI_OUT = Join-Path $Repo 'src\main\resources\assets\tnc\animations\entity\dark_giant.animation.json'

$TEXW = $TexSize
$TEXH = $TexSize

# ===========================================================================
#  1. PALETTE  (dark element: blackened steel / leather / fur / bone / crimson)
# ===========================================================================
function C([int]$r, [int]$g, [int]$b, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }

$COL = @{
    steel     = (C 0x2A 0x2A 0x31)   # blackened steel / dark gunmetal
    steelHi   = (C 0x55 0x55 0x62)
    steelLo   = (C 0x1B 0x1B 0x22)
    steelDark = (C 0x13 0x13 0x18)
    leather   = (C 0x3A 0x2A 0x22)   # leather straps
    leatherHi = (C 0x55 0x3E 0x30)
    leatherLo = (C 0x24 0x19 0x13)
    fur       = (C 0x24 0x1C 0x18)   # beast fur
    furHi     = (C 0x3A 0x2D 0x24)
    furLo     = (C 0x14 0x0F 0x0D)
    bone      = (C 0xC9 0xBF 0xA6)   # horns / tusks / claws
    boneHi    = (C 0xE4 0xDC 0xC8)
    boneLo    = (C 0x96 0x8B 0x72)
    cloth     = (C 0x5A 0x16 0x20)   # dark crimson cape
    clothHi   = (C 0x78 0x20 0x2C)
    clothLo   = (C 0x38 0x0C 0x14)
    rune      = (C 0x8E 0x1B 0x2A)   # sinister glow (eyes + runes)
    eye       = (C 0xFF 0x54 0x22)
    eyeCore   = (C 0xFF 0xDC 0xA0)
}

# ===========================================================================
#  2. NOISE  (deterministic, tileable so a wrapped face shows no seam)
# ===========================================================================
$script:NW = 256
$script:NH = 256
$script:NOISE = New-Object 'double[,]' $script:NW, $script:NH
$script:NOISE2 = New-Object 'double[,]' $script:NW, $script:NH

function Smooth([double]$t) { return $t * $t * (3.0 - 2.0 * $t) }

function BuildNoise([int]$seed, [int]$layers, [bool]$second) {
    $rnd = New-Object System.Random $seed
    if ($second) { $target = $script:NOISE2 } else { $target = $script:NOISE }
    for ($y = 0; $y -lt $script:NH; $y++) {
        for ($x = 0; $x -lt $script:NW; $x++) { $target[$x, $y] = 0.0 }
    }
    $amp = 1.0
    $ampSum = 0.0
    for ($L = 0; $L -lt $layers; $L++) {
        $cell = [int](4 * [math]::Pow(2, $L))          # 32, 64, 128, 256 -- all divide 256
        $g = New-Object 'double[,]' $cell, $cell
        for ($i = 0; $i -lt $cell; $i++) {
            for ($j = 0; $j -lt $cell; $j++) { $g[$i, $j] = $rnd.NextDouble() * 2.0 - 1.0 }
        }
        $step = $script:NW / $cell
        for ($y = 0; $y -lt $script:NH; $y++) {
            $fy = $y / $step
            $y0 = [int][math]::Floor($fy)
            $ty = Smooth($fy - $y0)
            $y1 = ($y0 + 1) % $cell
            $y0 = $y0 % $cell
            for ($x = 0; $x -lt $script:NW; $x++) {
                $fx = $x / $step
                $x0 = [int][math]::Floor($fx)
                $tx = Smooth($fx - $x0)
                $x1 = ($x0 + 1) % $cell
                $x0 = $x0 % $cell
                $v0 = $g[$x0, $y0] + ($g[$x1, $y0] - $g[$x0, $y0]) * $tx
                $v1 = $g[$x0, $y1] + ($g[$x1, $y1] - $g[$x0, $y1]) * $tx
                $target[$x, $y] += ($v0 + ($v1 - $v0) * $ty) * $amp
            }
        }
        $ampSum += $amp
        $amp *= 0.5
    }
    for ($y = 0; $y -lt $script:NH; $y++) {
        for ($x = 0; $x -lt $script:NW; $x++) { $target[$x, $y] = $target[$x, $y] / $ampSum }
    }
}

BuildNoise 20261003 4 $false
BuildNoise 771013   4 $true

function Nz([int]$x, [int]$y, [int]$which = 0) {
    $xx = (($x % $script:NW) + $script:NW) % $script:NW
    $yy = (($y % $script:NH) + $script:NH) % $script:NH
    if ($which -eq 1) { return $script:NOISE2[$xx, $yy] }
    return $script:NOISE[$xx, $yy]
}

# ===========================================================================
#  3. CANVAS  (transparent until a cube is painted)
# ===========================================================================
$script:BMP = New-Object System.Drawing.Bitmap $TEXW, $TEXH, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$script:CLEAR = C 0 0 0 0
for ($y = 0; $y -lt $TEXH; $y++) {
    for ($x = 0; $x -lt $TEXW; $x++) { $script:BMP.SetPixel($x, $y, $script:CLEAR) }
}

function Put([int]$x, [int]$y, $col) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $TEXW -or $y -ge $TEXH) { return }
    $script:BMP.SetPixel($x, $y, $col)
}

function Get0([int]$x, [int]$y) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $TEXW -or $y -ge $TEXH) { return $script:CLEAR }
    return $script:BMP.GetPixel($x, $y)
}

function Shade($col, [double]$f) {
    $r = [int][math]::Round($col.R * $f); if ($r -gt 255) { $r = 255 }; if ($r -lt 0) { $r = 0 }
    $g = [int][math]::Round($col.G * $f); if ($g -gt 255) { $g = 255 }; if ($g -lt 0) { $g = 0 }
    $b = [int][math]::Round($col.B * $f); if ($b -gt 255) { $b = 255 }; if ($b -lt 0) { $b = 0 }
    return (C $r $g $b $col.A)
}

function Mix($a, $b, [double]$t) {
    $r = [int][math]::Round($a.R + ($b.R - $a.R) * $t)
    $g = [int][math]::Round($a.G + ($b.G - $a.G) * $t)
    $bl = [int][math]::Round($a.B + ($b.B - $a.B) * $t)
    return (C $r $g $bl $a.A)
}

# ===========================================================================
#  4. THE MODEL TABLE
#     One AddBox per box; the last argument is how many stacked slabs that box is cut into.
#     Bones: root -> body, head, armright, armleft, legright, legleft, cape, tail
#            head -> hornright, hornleft       body -> pauldronright, pauldronleft
# ===========================================================================
function Cu([string]$bone, [string]$name, $o, $s, [string]$mat) {
    return [ordered]@{ bone = $bone; name = $name; o = $o; s = $s; mat = $mat }
}

$CUBES = New-Object System.Collections.ArrayList

function AddBox([string]$bone, [string]$name, [double]$x, [double]$y, [double]$z,
                [double]$w, [double]$h, [double]$d, [string]$mat, [int]$slabs = 1) {
    for ($i = 0; $i -lt $slabs; $i++) {
        $y0 = $y + $h * $i / $slabs
        $hh = $h / $slabs
        $nm = if ($slabs -eq 1) { $name } else { "{0}{1}" -f $name, ($i + 1) }
        [void]$CUBES.Add((Cu $bone $nm @($x, $y0, $z) @($w, $hh, $d) $mat))
    }
}

# ---------------------------------------------------------------- legs
# Feet at y=0, hip at y=40. The deep parts (thigh, shin, boot, chest, waist) are cut into two
# stacked cubes: box UV for one cube of size (w,h,d) is 2*(d+w) * (d+h), while k slabs cost
# k * 2*(d+w) * (d+h/k) -- close to half for k=2. That trade is what lets a 4-block giant fit.
foreach ($side in @(@('right', -8.0), @('left', 0.0))) {
    $tag = $side[0]
    $x0 = $side[1]
    $bone = "leg$tag"
    AddBox $bone "thigh_$tag" $x0       26 -3   8  14 5  'fur'   1
    AddBox $bone "knee_$tag"  ($x0-1)   20 -4   10 6  3  'steel' 1
    AddBox $bone "shin_$tag"  $x0       8  -3   7  13 5  'steel' 2
    AddBox $bone "boot_$tag"  ($x0-1)   0  -5   9  8  9  'steel' 2
}

# ---------------------------------------------------------------- arms
foreach ($side in @(@('right', -16.0), @('left', 8.0))) {
    $tag = $side[0]
    $ax = $side[1]
    $bone = "arm$tag"
    AddBox $bone "upperarm_$tag" ($ax)     46 -3   7 14 5  'fur'     1
    AddBox $bone "forearm_$tag"  ($ax)     34 -3   6 12 5  'fur'     1
    AddBox $bone "bracer_$tag"   ($ax-0.5) 33 -4   7 12 2  'steel'  1
    AddBox $bone "hand_$tag"     ($ax-0.5) 22 -4   8 11 6  'steelLo' 1
    AddBox $bone "paw_$tag"      ($ax-0.5) 16 -4.5 8 6  3  'bone'    1
}

# ---------------------------------------------------------------- torso (bone: body)
AddBox 'body' 'neck'        -3    70 -3   6  5  5   'steel'   1
AddBox 'body' 'chest'       -9    52 -4.5 18 16 7   'steel'   2
AddBox 'body' 'chestplate'  -10   53 -6.5 20 9  2.5 'steel'   1
AddBox 'body' 'chestplate2' -10   45 -6.5 20 8  2.5 'steel'   1
AddBox 'body' 'chestrune'   -3    54 -7   6  5  1.5 'rune'    1
AddBox 'body' 'waist'       -8    40 -4.5 16 12 6   'steel'   1
AddBox 'body' 'belt'        -9    37 -5   18 5  3   'leather' 1
AddBox 'body' 'buckle'      -3.5  37 -6.5 7  5  3   'steelHi' 1

# cape: stacked slabs (one 12x48x2 slab would cost three times as much atlas)
AddBox 'cape' 'cape1' -6 57 6.5 12 15 2 'cloth' 1
AddBox 'cape' 'cape2' -6 42 6.5 12 15 2 'cloth' 1
AddBox 'cape' 'cape3' -6 24 6.5 12 18 2 'cloth' 1

AddBox 'tail' 'tail1' -2.5 27 5.5 5 9 4 'fur' 1
AddBox 'tail' 'tail2' -2   18 6   4 9 3 'fur' 1
AddBox 'tail' 'tail3' -1.5 10 6.5 3 9 3 'fur' 1

# ---------------------------------------------------------------- head (bone: head)
AddBox 'head' 'skull'  -6   76   -5   12 11 8 'fur'  1
AddBox 'head' 'snout'  -4.5 73   -8.5 9  8  5 'fur'  1
AddBox 'head' 'brow'   -6   85   -6   12 3  4 'fur'  1
AddBox 'head' 'tusk_r' -5.5 71   -8.5 2  5  2 'bone' 1
AddBox 'head' 'tusk_l'  3.5 71   -8.5 2  5  2 'bone' 1
AddBox 'head' 'eye_r'  -4.5 81   -5.5 2.5 2 0.8 'eye' 1
AddBox 'head' 'eye_l'   2   81   -5.5 2.5 2 0.8 'eye' 1
AddBox 'head' 'ear_r'  -7   83   -1.5 2 3  2  'fur'  1
AddBox 'head' 'ear_l'   5   83   -1.5 2 3  2  'fur'  1

# horns: base + tip per side (out and back), with an iron collar at the root
foreach ($side in @(@('right', -1.0), @('left', 1.0))) {
    $tag = $side[0]
    $sgn = $side[1]
    $x0 = if ($sgn -lt 0) { -8.0 } else { 4.0 }
    $x1 = if ($sgn -lt 0) { -11.5 } else { 7.5 }
    $x2 = if ($sgn -lt 0) { -9.0 } else { 5.0 }
    AddBox "horn$tag" "hornbase_$tag" $x0 86   -2 4 5 4 'bone'    1
    AddBox "horn$tag" "horntip_$tag"  $x1 89   -1 4 7 4 'bone'    1
    AddBox "horn$tag" "hornring_$tag" $x2 87.5 -2 5 2 4 'steelLo' 1
}

# ---------------------------------------------------------------- pauldrons
foreach ($side in @(@('right', -1.0), @('left', 1.0))) {
    $tag = $side[0]
    $sgn = $side[1]
    $capA = if ($sgn -lt 0) { -22.0 } else { 12.0 }
    AddBox "pauldron$tag" "pauldroncapA_$tag" $capA 70 -5   10 8 9 'steel' 2
    AddBox "pauldron$tag" "pauldroncapB_$tag" $capA 62 -4.5 9  8 8 'steel' 1
    $sx = if ($sgn -lt 0) { @(-20.0, -17.5, -15.0) } else { @(10.0, 12.5, 15.0) }
    $i = 0
    foreach ($spx in $sx) {
        $tipx = if ($sgn -lt 0) { $spx - 1.5 } else { $spx + 1.5 }
        AddBox "pauldron$tag" "spike_${tag}_$i"    $spx  78 -5 2 6 3 'steelHi' 1
        AddBox "pauldron$tag" "spiketip_${tag}_$i" $tipx 84 -4 2 4 2 'bone'    1
        $i++
    }
}
# ===========================================================================
#  5. BOX-UV PACKER
#     A skyline (bottom-left) packer plus a growing binary-tree packer, several sort
#     orders, flattest result wins. Deterministic. Falls back to a plain shelf packer if
#     the atlas is very full, and throws with a readable message when a cube cannot fit.
# ===========================================================================
$PAD = 1
$placed = New-Object System.Collections.ArrayList
$order = 0
foreach ($cu in $CUBES) {
    $w = [double]$cu.s[0]; $h = [double]$cu.s[1]; $d = [double]$cu.s[2]
    $fw = [int][math]::Round(2 * ($d + $w))
    $fh = [int][math]::Round($d + $h)
    if ($fw -le 0 -or $fh -le 0) { throw ("cube {0}: degenerate footprint {1}x{2}" -f $cu.name, $fw, $fh) }
    if ($fw + 2 * $PAD -gt $TEXW -or $fh + 2 * $PAD -gt $TEXH) {
        throw ("cube {0} needs {1}x{2} px -- bigger than the {3}x{4} atlas, shrink it" -f `
                $cu.name, $fw, $fh, $TEXW, $TEXH)
    }
    [void]$placed.Add([ordered]@{ cu = $cu; fw = $fw; fh = $fh; order = $order; u = 0; v = 0 })
    $order++
}

# skyline packer: keep the profile of the filled area, drop each rectangle at the lowest x
function PackSkyline($items, [int]$size, [int]$pad, [string]$sortKey) {
    $work = switch ($sortKey) {
        'tall' { $items | Sort-Object -Property @{Expression = { - ($_.fh + $pad) } }, @{Expression = { - ($_.fw + $pad) } }, order }
        'wide' { $items | Sort-Object -Property @{Expression = { - ($_.fw + $pad) } }, @{Expression = { - ($_.fh + $pad) } }, order }
        'area' { $items | Sort-Object -Property @{Expression = { - (($_.fw + $pad) * ($_.fh + $pad)) } }, order }
        default { $items | Sort-Object -Property order }
    }
    $sky = New-Object System.Collections.ArrayList
    [void]$sky.Add(@(0, $size, 0))
    $used = 0
    foreach ($it in $work) {
        $bw = $it.fw + $pad
        $bh = $it.fh + $pad
        $bestX = -1; $bestY = [int]::MaxValue
        for ($i = 0; $i -lt $sky.Count; $i++) {
            $x0 = $sky[$i][0]
            if ($x0 + $bw -gt $size) { continue }
            $y0 = 0; $cx = $x0; $j = $i
            while ($cx -lt ($x0 + $bw) -and $j -lt $sky.Count) {
                if ($sky[$j][2] -gt $y0) { $y0 = $sky[$j][2] }
                $cx = $sky[$j][0] + $sky[$j][1]
                $j++
            }
            if ($cx -lt ($x0 + $bw)) { continue }
            if ($y0 -lt $bestY -or ($y0 -eq $bestY -and ($bestX -lt 0 -or $x0 -lt $bestX))) {
                $bestY = $y0; $bestX = $x0
            }
        }
        if ($bestX -lt 0 -or ($bestY + $bh) -gt $size) { return $null }
        $it.u = $bestX
        $it.v = $bestY
        if (($bestY + $bh) -gt $used) { $used = $bestY + $bh }
        $new = New-Object System.Collections.ArrayList
        foreach ($seg in $sky) {
            $sx = $seg[0]; $sw = $seg[1]; $sy = $seg[2]
            if (($sx + $sw) -le $bestX -or $sx -ge ($bestX + $bw)) { [void]$new.Add(@($sx, $sw, $sy)); continue }
            if ($sx -lt $bestX) { [void]$new.Add(@($sx, ($bestX - $sx), $sy)) }
            if (($sx + $sw) -gt ($bestX + $bw)) {
                [void]$new.Add(@(($bestX + $bw), (($sx + $sw) - ($bestX + $bw)), $sy))
            }
        }
        [void]$new.Add(@($bestX, $bw, ($bestY + $bh)))
        $sorted = $new | Sort-Object -Property @{Expression = { $_[0] } }
        $sky = New-Object System.Collections.ArrayList
        foreach ($seg in $sorted) {
            if ($sky.Count -gt 0) {
                $last = $sky[$sky.Count - 1]
                if ((($last[0] + $last[1]) -eq $seg[0]) -and ($last[2] -eq $seg[2])) {
                    $sky[$sky.Count - 1] = @($last[0], ($last[1] + $seg[1]), $seg[2])
                    continue
                }
            }
            [void]$sky.Add(@($seg[0], $seg[1], $seg[2]))
        }
    }
    return $used
}

# plain shelf packer: last resort, and the fallback when the skyline pass fails
function PackShelf($items, [int]$size, [int]$pad) {
    $work = $items | Sort-Object -Property @{Expression = { - ($_.fh + $pad) } }, @{Expression = { - ($_.fw + $pad) } }, order
    $cx = $pad; $cy = $pad; $shelf = 0
    foreach ($it in $work) {
        if (($cx + $it.fw + $pad) -gt $size) { $cy += $shelf; $cx = $pad; $shelf = 0 }
        if (($cy + $it.fh + $pad) -gt $size) { return $null }
        $it.u = $cx; $it.v = $cy
        $cx += ($it.fw + 2 * $pad)
        if (($it.fh + 2 * $pad) -gt $shelf) { $shelf = $it.fh + 2 * $pad }
    }
    return ($cy + $shelf)
}

$packArea = 0
foreach ($r in $placed) { $packArea += ($r.fw * $r.fh) }
$bestUsed = [int]::MaxValue
$bestKey = ''
$bestU = New-Object System.Collections.ArrayList
foreach ($r in $placed) { [void]$bestU.Add([ordered]@{ cu = $r.cu; fw = $r.fw; fh = $r.fh; order = $r.order; u = 0; v = 0 }) }

foreach ($key in @('tall', 'wide', 'area', 'decl')) {
    $trial = New-Object System.Collections.ArrayList
    foreach ($r in $placed) { [void]$trial.Add([ordered]@{ cu = $r.cu; fw = $r.fw; fh = $r.fh; order = $r.order; u = 0; v = 0 }) }
    $used = PackSkyline $trial $TEXW $PAD $key
    if ($null -ne $used -and $used -lt $bestUsed) {
        $bestUsed = $used
        $bestKey = "skyline/$key"
        $bestU = $trial
    }
}
if ($bestUsed -eq [int]::MaxValue) {
    $trial = New-Object System.Collections.ArrayList
    foreach ($r in $placed) { [void]$trial.Add([ordered]@{ cu = $r.cu; fw = $r.fw; fh = $r.fh; order = $r.order; u = 0; v = 0 }) }
    $used = PackShelf $trial $TEXW $PAD
    if ($null -ne $used) {
        $bestUsed = $used
        $bestKey = 'shelf'
        $bestU = $trial
    }
}
if ($bestUsed -eq [int]::MaxValue) {
    throw ("the {0}x{1} atlas cannot hold {2} cubes needing {3} px -- cut cubes or shrink them" -f `
            $TEXW, $TEXH, $placed.Count, $packArea)
}
$placed = $bestU
$uvOf = @{}
foreach ($r in $placed) { $uvOf[$r.cu.name] = @($r.u, $r.v) }

# x,y,w,h of one face inside the atlas -- authoritative for the painters and the preview
function FaceRect($r, [string]$face) {
    $u = [int]$r.u; $v = [int]$r.v
    $w = [int][math]::Round([double]$r.cu.s[0])
    $h = [int][math]::Round([double]$r.cu.s[1])
    $d = [int][math]::Round([double]$r.cu.s[2])
    switch ($face) {
        'up'    { return @(($u + $d), $v, $w, $d) }
        'down'  { return @(($u + $d + $w), $v, $w, $d) }
        'west'  { return @($u, ($v + $d), $d, $h) }
        'north' { return @(($u + $d), ($v + $d), $w, $h) }
        'east'  { return @(($u + $d + $w), ($v + $d), $d, $h) }
        'south' { return @(($u + $d + $w + $d), ($v + $d), $w, $h) }
        default { throw "unknown face '$face'" }
    }
}

# ===========================================================================
#  6. MATERIAL PAINTERS
# ===========================================================================
function FrameEdge([int]$x0, [int]$y0, [int]$w, [int]$h, $col) {
    for ($i = 0; $i -lt $w; $i++) { Put ($x0 + $i) $y0 $col; Put ($x0 + $i) ($y0 + $h - 1) $col }
    for ($j = 0; $j -lt $h; $j++) { Put $x0 ($y0 + $j) $col; Put ($x0 + $w - 1) ($y0 + $j) $col }
}

function PaintNoiseFill([int]$x0, [int]$y0, [int]$w, [int]$h, $base, [double]$amp, [int]$seed) {
    for ($j = 0; $j -lt $h; $j++) {
        for ($i = 0; $i -lt $w; $i++) {
            $n = Nz ($x0 + $i + $seed) ($y0 + $j + $seed) 0
            Put ($x0 + $i) ($y0 + $j) (Shade $base (1.0 + $n * $amp))
        }
    }
}

function PaintFur([int]$x0, [int]$y0, [int]$w, [int]$h, [int]$seed) {
    PaintNoiseFill $x0 $y0 $w $h $COL.fur 0.30 $seed
    for ($i = 0; $i -lt $w; $i++) {
        $n = Nz ($x0 + $i * 3 + $seed) ($y0 + $seed) 1
        if ($n -gt 0.0) {
            for ($j = 0; $j -lt $h; $j++) {
                $n2 = Nz ($x0 + $i) ($y0 + $j) 1
                if ($n2 -gt 0.12) { Put ($x0 + $i) ($y0 + $j) (Mix (Get0 ($x0 + $i) ($y0 + $j)) $COL.furHi 0.55) }
                elseif ($n2 -lt -0.25) { Put ($x0 + $i) ($y0 + $j) (Mix (Get0 ($x0 + $i) ($y0 + $j)) $COL.furLo 0.45) }
            }
        }
    }
    FrameEdge $x0 $y0 $w $h (Shade $COL.furLo 0.75)
}

function PaintSteel([int]$x0, [int]$y0, [int]$w, [int]$h, [int]$seed) {
    PaintNoiseFill $x0 $y0 $w $h $COL.steel 0.24 $seed
    for ($j = 0; $j -lt $h; $j++) {
        $band = 0.14 * [math]::Sin(($j / [double][math]::Max(1, ($h - 1))) * [math]::PI)
        if ($band -gt 0.02) {
            for ($i = 0; $i -lt $w; $i++) { Put ($x0 + $i) ($y0 + $j) (Shade (Get0 ($x0 + $i) ($y0 + $j)) (1.0 + $band)) }
        }
    }
    FrameEdge $x0 $y0 $w $h $COL.steelDark
    if ($w -ge 6 -and $h -ge 6) {
        foreach ($rp in @(@(1, 1), @(($w - 3), 1), @(1, ($h - 3)), @(($w - 3), ($h - 3)))) {
            Put ($x0 + $rp[0]) ($y0 + $rp[1]) $COL.steelLo
            Put ($x0 + $rp[0] + 1) ($y0 + $rp[1]) $COL.steelHi
            Put ($x0 + $rp[0]) ($y0 + $rp[1] + 1) $COL.steelHi
        }
    }
    if ($h -ge 12) {
        $mj = [int]($h / 2)
        for ($i = 1; $i -lt $w - 1; $i++) { Put ($x0 + $i) ($y0 + $mj) $COL.steelDark }
    }
}

function PaintLeather([int]$x0, [int]$y0, [int]$w, [int]$h, [int]$seed) {
    PaintNoiseFill $x0 $y0 $w $h $COL.leather 0.28 $seed
    for ($j = 0; $j -lt $h; $j++) {
        for ($i = 0; $i -lt $w; $i++) {
            $n = Nz ($x0 + $i + $seed) ($y0 + $j) 1
            if ($n -gt 0.42) { Put ($x0 + $i) ($y0 + $j) $COL.leatherHi }
            elseif ($n -lt -0.42) { Put ($x0 + $i) ($y0 + $j) $COL.leatherLo }
        }
    }
    FrameEdge $x0 $y0 $w $h $COL.leatherLo
    if ($h -ge 5) { for ($i = 1; $i -lt $w - 1; $i += 2) { Put ($x0 + $i) ($y0 + 2) $COL.leatherHi } }
}

function PaintBone([int]$x0, [int]$y0, [int]$w, [int]$h, [int]$seed) {
    PaintNoiseFill $x0 $y0 $w $h $COL.bone 0.18 $seed
    for ($i = 0; $i -lt $w; $i++) {
        $n = Nz ($x0 + $i * 5 + $seed) ($y0 + $seed) 1
        if ($n -gt 0.20) {
            for ($j = 0; $j -lt $h; $j++) { Put ($x0 + $i) ($y0 + $j) (Mix (Get0 ($x0 + $i) ($y0 + $j)) $COL.boneHi 0.55) }
        } elseif ($n -lt -0.30) {
            for ($j = 0; $j -lt $h; $j++) { Put ($x0 + $i) ($y0 + $j) (Mix (Get0 ($x0 + $i) ($y0 + $j)) $COL.boneLo 0.60) }
        }
    }
    for ($j = 0; $j -lt $h; $j += 4) {
        for ($i = 1; $i -lt $w - 1; $i++) { Put ($x0 + $i) ($y0 + $j) (Mix (Get0 ($x0 + $i) ($y0 + $j)) $COL.boneLo 0.45) }
    }
    FrameEdge $x0 $y0 $w $h $COL.boneLo
}

function PaintCloth([int]$x0, [int]$y0, [int]$w, [int]$h, [int]$seed) {
    PaintNoiseFill $x0 $y0 $w $h $COL.cloth 0.22 $seed
    for ($j = 0; $j -lt $h; $j++) {
        for ($i = 0; $i -lt $w; $i++) {
            if ((($i + $j) % 4) -eq 0) { Put ($x0 + $i) ($y0 + $j) (Mix (Get0 ($x0 + $i) ($y0 + $j)) $COL.clothHi 0.35) }
            elseif ((($i - $j) % 7) -eq 0) { Put ($x0 + $i) ($y0 + $j) (Mix (Get0 ($x0 + $i) ($y0 + $j)) $COL.clothLo 0.45) }
        }
    }
    FrameEdge $x0 $y0 $w $h (Shade $COL.clothLo 0.9)
}

function PaintGlow([int]$x0, [int]$y0, [int]$w, [int]$h, [string]$which) {
    for ($j = 0; $j -lt $h; $j++) {
        for ($i = 0; $i -lt $w; $i++) {
            $edge = ($i -eq 0 -or $j -eq 0 -or $i -eq $w - 1 -or $j -eq $h - 1)
            if ($which -eq 'eye') {
                $c = if ($edge) { $COL.rune } else { $COL.eye }
                if (-not $edge -and (($i + $j) % 2) -eq 0 -and $i -ge 1 -and $i -le $w - 2) { $c = $COL.eyeCore }
            } else {
                $c = if ($edge) { Shade $COL.rune 0.55 } else { $COL.rune }
                if (-not $edge -and ((($i * 3 + $j * 5) % 7) -eq 0)) { $c = Mix $COL.rune $COL.eye 0.5 }
            }
            Put ($x0 + $i) ($y0 + $j) $c
        }
    }
}

function PaintBuckle([int]$x0, [int]$y0, [int]$w, [int]$h, [int]$seed) {
    PaintNoiseFill $x0 $y0 $w $h $COL.steelHi 0.16 $seed
    FrameEdge $x0 $y0 $w $h $COL.steelDark
    for ($j = 2; $j -lt $h - 2; $j++) {
        for ($i = 2; $i -lt $w - 2; $i++) {
            if ($i -eq 2 -or $j -eq 2 -or $i -eq $w - 3 -or $j -eq $h - 3) { Put ($x0 + $i) ($y0 + $j) $COL.steelLo }
        }
    }
}

function PaintFace([int]$x0, [int]$y0, [int]$w, [int]$h, $cu, [string]$face, [int]$seed) {
    switch ($cu.mat) {
        'steel'   { PaintSteel   $x0 $y0 $w $h $seed }
        'steelHi' { PaintBuckle  $x0 $y0 $w $h $seed }
        'steelLo' { PaintSteel   $x0 $y0 $w $h ($seed + 17) }
        'leather' { PaintLeather $x0 $y0 $w $h $seed }
        'fur'     { PaintFur     $x0 $y0 $w $h $seed }
        'bone'    { PaintBone    $x0 $y0 $w $h $seed }
        'cloth'   { PaintCloth   $x0 $y0 $w $h $seed }
        'rune'    { PaintGlow    $x0 $y0 $w $h 'rune' }
        'eye'     { PaintGlow    $x0 $y0 $w $h 'eye' }
        default   { throw ("unknown material '{0}' on cube {1}" -f $cu.mat, $cu.name) }
    }
}

# ---- pass 2: paint every cube face by face. The side strip (v+d .. v+d+h) is painted LAST
#      on purpose: painting order resolves the one row the side strip shares with up/down,
#      and the side faces are the ones that carry the readable detail.
$seedBase = 1000
foreach ($r in $placed) {
    $seed = $seedBase + $r.order * 37
    foreach ($face in @('up', 'down', 'west', 'north', 'east', 'south')) {
        $rc = FaceRect $r $face
        $extra = switch ($face) { 'up' { 0 } 'down' { 3 } 'west' { 5 } 'north' { 7 } 'east' { 11 } default { 13 } }
        PaintFace $rc[0] $rc[1] $rc[2] $rc[3] $r.cu $face ($seed + $extra)
    }
}

# ===========================================================================
#  7. FACE DETAIL  (drawn on top of the material pass so the head reads as a face)
# ===========================================================================
function Detail([string]$cubeName, [string]$face, [scriptblock]$paint) {
    # NOTE: @(...) around the pipeline is required -- without it a single match comes back as one
    # OrderedDictionary and "$x[0]" then means "the value under key 0", i.e. $null, and the next
    # call dies inside FaceRect with a confusing NullArray instead of naming the missing cube.
    $r = @($placed | Where-Object { $_.cu.name -eq $cubeName }) | Select-Object -First 1
    if (-not $r -or -not $r.cu) { throw "Detail: no cube named '$cubeName'" }
    $rc = FaceRect $r $face
    & $paint $rc[0] $rc[1] $rc[2] $rc[3]
}

# skull front (the model's face): deep-set burning eyes, heavy brow, old scars
Detail 'skull' 'north' {
    param($fx, $fy, $fw, $fh)
    foreach ($ex in @(1, 7)) {
        for ($j = 0; $j -lt 4; $j++) {
            for ($i = 0; $i -lt 4; $i++) { Put ($fx + $ex + $i) ($fy + 5 + $j) (Shade $COL.furLo 0.65) }
        }
        Put ($fx + $ex + 1) ($fy + 6) $COL.eye
        Put ($fx + $ex + 2) ($fy + 6) $COL.eye
        Put ($fx + $ex + 1) ($fy + 7) $COL.rune
        Put ($fx + $ex + 2) ($fy + 7) $COL.eyeCore
    }
    for ($i = 1; $i -lt $fw - 1; $i++) { Put ($fx + $i) ($fy + 4) (Mix (Get0 ($fx + $i) ($fy + 4)) $COL.furHi 0.65) }
    for ($i = 1; $i -lt 5; $i++) { Put ($fx + $i) ($fy + $i + 2) (Mix (Get0 ($fx + $i) ($fy + $i + 2)) $COL.boneLo 0.30) }
}

# snout front: nostrils, mouth line, bone studs on the bridge
Detail 'snout' 'north' {
    param($fx, $fy, $fw, $fh)
    Put ($fx + 2) ($fy + 3) $COL.furLo
    Put ($fx + $fw - 3) ($fy + 3) $COL.furLo
    for ($i = 0; $i -lt $fw; $i++) {
        Put ($fx + $i) ($fy + $fh - 1) $COL.furLo
        Put ($fx + $i) ($fy + $fh - 2) (Mix (Get0 ($fx + $i) ($fy + $fh - 2)) $COL.furLo 0.5)
    }
    Put ($fx + 4) ($fy + 5) (Mix (Get0 ($fx + 4) ($fy + 5)) $COL.boneLo 0.55)
}

# chest plate front: a rune sigil carved into the upper plate
Detail 'chestplate' 'north' {
    param($fx, $fy, $fw, $fh)
    $cx = $fx + [int]($fw / 2)
    $cy = $fy + [int]($fh / 2)
    for ($rr = 1; $rr -le 5; $rr++) {
        Put ($cx - $rr) ($cy - 2) (Mix (Get0 ($cx - $rr) ($cy - 2)) $COL.rune 0.55)
        Put ($cx + $rr) ($cy - 2) (Mix (Get0 ($cx + $rr) ($cy - 2)) $COL.rune 0.55)
        Put ($cx - $rr) ($cy + 2) (Mix (Get0 ($cx - $rr) ($cy + 2)) $COL.rune 0.55)
        Put ($cx + $rr) ($cy + 2) (Mix (Get0 ($cx + $rr) ($cy + 2)) $COL.rune 0.55)
    }
    for ($j = -3; $j -le 3; $j++) { Put $cx ($cy + $j) (Mix (Get0 $cx ($cy + $j)) $COL.rune 0.6) }
    for ($i = -7; $i -le 7; $i++) { Put ($cx + $i) $cy (Mix (Get0 ($cx + $i) $cy) $COL.rune 0.45) }
}

# lower plate: a rivet band so the two plates read as separate armour
Detail 'chestplate2' 'north' {
    param($fx, $fy, $fw, $fh)
    for ($i = 1; $i -lt $fw - 1; $i += 2) { Put ($fx + $i) ($fy + 2) $COL.steelHi }
    for ($i = 1; $i -lt $fw - 1; $i++) { Put ($fx + $i) ($fy + 6) $COL.steelDark }
}

# belt buckle front: bright metal, dark cross, a glow in the middle
Detail 'buckle' 'north' {
    param($fx, $fy, $fw, $fh)
    $cx = $fx + [int]($fw / 2)
    $cy = $fy + [int]($fh / 2)
    for ($j = -2; $j -le 2; $j++) { Put $cx ($cy + $j) $COL.steelDark }
    for ($i = -2; $i -le 2; $i++) { Put ($cx + $i) $cy $COL.steelDark }
    Put $cx ($cy - 1) $COL.rune
    Put $cx ($cy + 1) $COL.rune
}

# pauldron cap top: a rivet grid so the spikes read as mounted on the cap
foreach ($tag in @('right', 'left')) {
    foreach ($nm in @("pauldroncapA_${tag}1", "pauldroncapA_${tag}2")) {
        Detail $nm 'up' {
            param($fx, $fy, $fw, $fh)
            for ($i = 0; $i -lt $fw; $i++) {
                for ($j = 0; $j -lt $fh; $j++) {
                    if ((($i + 1) % 3) -eq 0 -and (($j + 1) % 3) -eq 0) { Put ($fx + $i) ($fy + $j) $COL.steelHi }
                }
            }
        }
    }
}

# boot front (lower slab): an iron toe-cap seam
foreach ($tag in @('right', 'left')) {
    Detail "boot_${tag}1" 'north' {
        param($fx, $fy, $fw, $fh)
        for ($i = 0; $i -lt $fw; $i++) { Put ($fx + $i) ($fy + 2) $COL.steelDark; Put ($fx + $i) ($fy + 3) $COL.steelLo }
    }
}

# ===========================================================================
#  8. WRITE THE THREE FILES
# ===========================================================================
function JNum([double]$x) {
    if ($x -eq [math]::Floor($x)) { return ([int]$x).ToString([Globalization.CultureInfo]::InvariantCulture) }
    return $x.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

$boneOrder = @('root', 'body', 'head', 'hornright', 'hornleft', 'armright', 'armleft',
               'pauldronright', 'pauldronleft', 'legright', 'legleft', 'cape', 'tail')
$boneParent = @{ root = $null; body = 'root'; head = 'body'; hornright = 'head'; hornleft = 'head';
                 armright = 'body'; armleft = 'body'; pauldronright = 'body'; pauldronleft = 'body';
                 legright = 'body'; legleft = 'body'; cape = 'body'; tail = 'body' }
$bonePivot = @{
    root          = @(0, 0, 0)
    body          = @(0, 40, 0)
    head          = @(0, 76, 0)
    hornright     = @(-6.5, 86, -1)
    hornleft      = @(6.5, 86, -1)
    armright      = @(-12, 70, 0)
    armleft       = @(12, 70, 0)
    pauldronright = @(-15, 70, 0)
    pauldronleft  = @(15, 70, 0)
    legright      = @(-4, 40, 0)
    legleft       = @(4, 40, 0)
    cape          = @(0, 72, 6)
    tail          = @(0, 36, 6)
}

$L = New-Object System.Collections.ArrayList
function Em([string]$s) { [void]$L.Add($s) }

Em '{'
Em '  "format_version": "1.12.0",'
Em '  "minecraft:geometry": ['
Em '    {'
Em '      "description": {'
Em '        "identifier": "geometry.tnc.dark_giant",'
Em ('        "texture_width": {0},' -f $TEXW)
Em ('        "texture_height": {0},' -f $TEXH)
Em '        "visible_bounds_width": 4.5,'
Em '        "visible_bounds_height": 5.5,'
Em '        "visible_bounds_offset": [0, 2.75, 0]'
Em '      },'
Em '      "bones": ['

$bi = 0
foreach ($bn in $boneOrder) {
    $bi++
    $boneCubes = @($placed | Where-Object { $_.cu.bone -eq $bn } | Sort-Object -Property order)
    Em '        {'
    $head = '          "name": "{0}"' -f $bn
    if ($boneParent[$bn]) { $head += (', "parent": "{0}"' -f $boneParent[$bn]) }
    $pv = $bonePivot[$bn]
    $head += (', "pivot": [{0}, {1}, {2}]' -f (JNum $pv[0]), (JNum $pv[1]), (JNum $pv[2]))
    if ($boneCubes.Count -eq 0) {
        # NOTE: the separator must be emitted here too -- an empty bone (root) followed by a
        # comma-less "{" makes the whole geo unparseable (the game then renders nothing at all).
        Em ($head + '}' + $(if ($bi -lt $boneOrder.Count) { ',' } else { '' }))
        continue
    }
    Em ($head + ', "cubes": [')
    $ci = 0
    foreach ($r in $boneCubes) {
        $ci++
        $cu = $r.cu
        $comma = if ($ci -lt $boneCubes.Count) { ',' } else { '' }
        $o = $cu.o; $s = $cu.s
        Em ('          {{ "origin": [{0}, {1}, {2}], "size": [{3}, {4}, {5}], "uv": [{6}, {7}] }}{8}' -f `
                (JNum $o[0]), (JNum $o[1]), (JNum $o[2]), (JNum $s[0]), (JNum $s[1]), (JNum $s[2]), `
                $r.u, $r.v, $comma)
    }
    Em ('        ]}' + $(if ($bi -lt $boneOrder.Count) { ',' } else { '' }))
}
Em '      ]'
Em '    }'
Em '  ]'
Em '}'

New-Item -ItemType Directory -Force -Path (Split-Path $GEO_OUT -Parent) | Out-Null
[System.IO.File]::WriteAllText($GEO_OUT, (($L -join "`r`n") + "`r`n"), (New-Object System.Text.UTF8Encoding $false))

# the animation file is empty on purpose (the renderer is fine with that)
$a = "{`r`n  `"format_version`": `"1.8.0`",`r`n  `"animations`": {}`r`n}`r`n"
New-Item -ItemType Directory -Force -Path (Split-Path $ANI_OUT -Parent) | Out-Null
[System.IO.File]::WriteAllText($ANI_OUT, $a, (New-Object System.Text.UTF8Encoding $false))

New-Item -ItemType Directory -Force -Path (Split-Path $TEX_OUT -Parent) | Out-Null
$script:BMP.Save($TEX_OUT, [System.Drawing.Imaging.ImageFormat]::Png)
$script:BMP.Dispose()

# ===========================================================================
#  9. SUMMARY
# ===========================================================================
$maxY = 0.0; $minY = 1e9; $maxX = 0.0; $minX = 1e9; $used = 0
foreach ($r in $placed) {
    $cu = $r.cu
    $top = [double]$cu.o[1] + [double]$cu.s[1]
    if ($top -gt $maxY) { $maxY = $top }
    if ([double]$cu.o[1] -lt $minY) { $minY = [double]$cu.o[1] }
    $rx = [double]$cu.o[0] + [double]$cu.s[0]
    if ($rx -gt $maxX) { $maxX = $rx }
    if ([double]$cu.o[0] -lt $minX) { $minX = [double]$cu.o[0] }
    $used += ($r.fw * $r.fh)
}
$byBone = @{}
foreach ($cu in $CUBES) {
    if (-not $byBone.ContainsKey($cu.bone)) { $byBone[$cu.bone] = 0 }
    $byBone[$cu.bone] = $byBone[$cu.bone] + 1
}

Write-Output "=== dark_giant assets generated ==="
Write-Output ("geo       : {0}  ({1} bytes)" -f $GEO_OUT, (Get-Item $GEO_OUT).Length)
Write-Output ("texture   : {0}  ({1} bytes)" -f $TEX_OUT, (Get-Item $TEX_OUT).Length)
Write-Output ("animation : {0}  ({1} bytes)" -f $ANI_OUT, (Get-Item $ANI_OUT).Length)
Write-Output ("cubes     : {0} in {1} bones   (packer: {2}, {3} of {4} rows)" -f `
        $CUBES.Count, $boneOrder.Count, $bestKey, $bestUsed, $TEXH)
Write-Output ("height    : {0} units = {1} blocks (y {2}..{3}, feet at 0)" -f `
        ($maxY - $minY), [math]::Round(($maxY - $minY) / 16.0, 3), $minY, $maxY)
Write-Output ("arm span  : {0} units = {1} blocks (x {2}..{3})" -f `
        ($maxX - $minX), [math]::Round(($maxX - $minX) / 16.0, 3), $minX, $maxX)
$occ = [math]::Round(100.0 * $used / ($TEXW * $TEXH), 1)
Write-Output ("uv atlas  : {0} of {1} px painted ({2} percent)" -f $used, ($TEXW * $TEXH), $occ)
Write-Output "cubes per bone:"
foreach ($bn in $boneOrder) {
    Write-Output ("   {0,-14} {1}" -f $bn, $(if ($byBone.ContainsKey($bn)) { $byBone[$bn] } else { 0 }))
}

<#
    IF THE AUTHOR WANTS MORE DETAIL
    Box UV costs about twice the model's surface area. A 4.5-block giant with spiked
    pauldrons, layered plates, a cape and a tail needs roughly 29,000 atlas pixels for
    everything; 128x128 offers 16,384, so this model is deliberately built to fit. Two ways
    to spend more:
      * run this script with -TexSize 256 and re-run tools\preview_dark_giant.ps1 -- the geo's
        texture_width/height follow the parameter, and the packer will place the cubes with
        room to spare. The PNG then must be re-exported at 256x256 (this script does that).
      * or raise the slab counts (the last argument of AddBox) back to 1 on chest/thigh/shin/
        boot/waist, which restores the deeper boxes at the cost of atlas area.
#>
