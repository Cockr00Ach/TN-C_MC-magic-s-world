# ---------------------------------------------------------------------------
#  Generates a placeholder skin for TN-C's "huai" NPC (G1's squire, 熙永槐).
#
#  Same approach as gen_self_skin.ps1 / gen_cava_skin.ps1: drawn from scratch
#  (no third-party skin), 64x64 classic layout, PLACEHOLDER for real art.
#
#  Look: a young man in half-plate - bright steel cuirass with a blue tabard,
#        pauldrons at the shoulders, leather gloves, and G1's unfinished shield
#        colours (dark leather + iron boss) hinted on the arm.
#
#  NOTE: pure ASCII on purpose (no BOM needed on a GBK host).
# ---------------------------------------------------------------------------
param(
    [string]$Out = '',
    [switch]$Preview
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not $Out) {
    $repo = Split-Path $PSScriptRoot -Parent
    $Out = Join-Path $repo 'src\main\resources\assets\tnc\textures\entity\huai.png'
}

function C([int]$r, [int]$g, [int]$b, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }

# ------------------------------------------------------------------ palette
$skin     = C 226 182 148
$skinDark = C 196 148 114
$hair     = C  62  46  34
$hairLite = C  92  70  50
$eyeWhite = C 238 238 236
$eyeIris  = C  78 104 128
$steel    = C 176 182 190
$steelLt  = C 214 220 226
$steelDk  = C 126 132 142
$tabard   = C  46  86 150
$tabardDk = C  32  62 112
$leather  = C  92  70  46
$leatherD = C  66  48  30
$pants    = C  72  66  58
$boots    = C  54  44  34
$outline  = C  38  32  28

# ------------------------------------------------------------------ helpers
$bmp = New-Object System.Drawing.Bitmap 64, 64, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Rect([int]$x, [int]$y, [int]$w, [int]$h, $col) {
    for ($i = 0; $i -lt $w; $i++) { for ($j = 0; $j -lt $h; $j++) { $bmp.SetPixel($x + $i, $y + $j, $col) } }
}

function Part([int]$bx, [int]$by, [int]$w, [int]$h, [int]$d, $base, $shade, $trim) {
    Rect ($bx + $d)           ($by + $d) $w $h $base
    Rect ($bx + $d + $w)      ($by + $d) $d $h $shade
    Rect ($bx + $d + $w + $d) ($by + $d) $w $h $shade
    Rect ($bx)                ($by + $d) $d $h $base
    Rect ($bx + $d)           ($by)      $w $d $trim
    Rect ($bx + $d + $w)      ($by)      $w $d $shade
}

# ------------------------------------------------------------------ body map
Part  0  0 8  8  8 $skin    $skinDark $hair      # head
Part 16 16 8 12  4 $steel   $steelDk  $steelLt   # torso: cuirass
Part 40 16 4 12  4 $steel   $steelDk  $steelLt   # right arm: pauldron + plate
Part 32 48 4 12  4 $steel   $steelDk  $steelLt   # left arm
Rect  0 16 4 12    $pants
Rect  4 16 4 12    $pants
Rect  8 16 4 12    $pants
Rect 12 16 4 12    $pants
Rect 16 48 4 12    $pants
Rect 20 48 4 12    $pants
Rect 24 48 4 12    $pants
Rect 28 48 4 12    $pants

# ------------------------------------------------------------------ head (young, clean-shaven)
$fx = 8; $fy = 8
Rect $fx $fy 8 8 $skin
Rect ($fx + 0) ($fy + 0) 8 3 $hair          # short hair
Rect ($fx + 0) ($fy + 0) 1 4 $hair
Rect ($fx + 7) ($fy + 0) 1 4 $hair
Rect ($fx + 1) ($fy + 1) 2 1 $hairLite      # highlight
Rect ($fx + 1) ($fy + 4) 2 1 $eyeWhite
Rect ($fx + 5) ($fy + 4) 2 1 $eyeWhite
$bmp.SetPixel(($fx + 2), ($fy + 4), $eyeIris)
$bmp.SetPixel(($fx + 5), ($fy + 4), $eyeIris)
Rect ($fx + 1) ($fy + 3) 2 1 $skinDark      # brows
Rect ($fx + 5) ($fy + 3) 2 1 $skinDark
Rect ($fx + 3) ($fy + 5) 2 1 $skinDark      # nose
Rect ($fx + 3) ($fy + 6) 2 1 $skinDark      # mouth
Rect ($fx + 3) ($fy + 7) 2 1 $skin          # chin

# ------------------------------------------------------------------ tabard on the cuirass
$bx = 20; $by = 20
Rect $bx $by 8 12 $steel
Rect ($bx + 2) ($by + 1) 4 10 $tabard       # blue tabard down the middle
Rect ($bx + 2) ($by + 9) 4 2 $tabardDk      # tabard hem
Rect ($bx + 3) ($by + 3) 2 2 $steelLt       # emblem plate
Rect ($bx + 0) ($by + 0) 2 2 $steelLt       # shoulder plate highlight
Rect ($bx + 6) ($by + 0) 2 2 $steelLt
Rect ($bx + 0) ($by + 2) 1 10 $steelDk      # side shading
Rect ($bx + 7) ($by + 2) 1 10 $steelDk
# steel wraps the sides of the torso
Rect 16 20 4 12 $steel
Rect 28 20 4 12 $steel

# ------------------------------------------------------------------ arms: pauldron, plate, leather gloves
foreach ($ax in @(44, 36)) {
    $ay = if ($ax -eq 44) { 20 } else { 52 }
    Rect $ax ($ay + 0) 4 3 $steelLt      # pauldron
    Rect $ax ($ay + 3) 4 6 $steel        # upper plate
    Rect $ax ($ay + 4) 4 1 $steelDk      # band
    Rect $ax ($ay + 9) 4 3 $leather      # glove
    Rect ($ax - 4) ($ay) 4 12 $steel
}

# ------------------------------------------------------------------ boots + greaves
foreach ($lx in @(0, 16)) {
    $ly = if ($lx -eq 0) { 16 } else { 48 }
    Rect ($lx + 0) ($ly + 7) 16 2 $steel     # greave
    Rect ($lx + 0) ($ly + 9) 16 3 $boots
}

# ------------------------------------------------------------------ edges
Rect 20 20 8 1 $outline
Rect 20 31 8 1 $outline
Rect 16 20 1 12 $outline
Rect 27 20 1 12 $outline

# ------------------------------------------------------------------ save
$dir = Split-Path $Out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output ("saved: {0}  ({1} bytes)" -f $Out, (Get-Item $Out).Length)

if ($Preview) {
    $scale = 6
    $pv = New-Object System.Drawing.Bitmap (64 * $scale), (64 * $scale)
    $g = [System.Drawing.Graphics]::FromImage($pv)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($bmp, 0, 0, 64 * $scale, 64 * $scale)
    $g.Dispose()
    $pvOut = Join-Path $env:TEMP 'huai-skin-preview.png'
    $pv.Save($pvOut, [System.Drawing.Imaging.ImageFormat]::Png)
    $pv.Dispose()
    Write-Output ("preview: {0}" -f $pvOut)
}

$bmp.Dispose()
