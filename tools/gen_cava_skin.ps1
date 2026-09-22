# ---------------------------------------------------------------------------
#  Generates a placeholder skin for TN-C's "cava" NPC (blacksmith, G1's father).
#
#  Same approach as gen_self_skin.ps1: drawn from scratch (no third-party skin),
#  64x64 classic skin layout, meant as a PLACEHOLDER to be replaced by real art.
#
#  Look: soot-stained face, iron-grey hair and stubble, dark leather apron over a
#        bare/rolled-sleeve working shirt, heavy boots - a man who works the forge.
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
    $Out = Join-Path $repo 'src\main\resources\assets\tnc\textures\entity\cava.png'
}

function C([int]$r, [int]$g, [int]$b, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }

# ------------------------------------------------------------------ palette
$skin     = C 214 165 128
$skinDark = C 178 132  98
$soot     = C 120 105  95
$hair     = C 108 108 112
$hairDark = C  78  78  82
$beard    = C  92  92  96
$eyeWhite = C 236 236 234
$eyeIris  = C  74  92 104
$shirt    = C 158 142 118
$shirtSh  = C 128 114  94
$leather  = C  92  62  38
$leatherD = C  66  44  26
$apron    = C 118  84  54
$pants    = C  74  66  56
$boots    = C  48  40  32
$outline  = C  40  32  26

# ------------------------------------------------------------------ helpers
$bmp = New-Object System.Drawing.Bitmap 64, 64, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Rect([int]$x, [int]$y, [int]$w, [int]$h, $col) {
    for ($i = 0; $i -lt $w; $i++) { for ($j = 0; $j -lt $h; $j++) { $bmp.SetPixel($x + $i, $y + $j, $col) } }
}

# Draws the six faces of a box-shaped part (order: front, right, back, left, top, bottom)
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
Part 16 16 8 12  4 $shirt   $shirtSh  $shirt     # body
Part 40 16 4 12  4 $skin    $skinDark $skin      # right arm
Part 32 48 4 12  4 $skin    $skinDark $skin      # left arm
Rect  0 16 4 12    $pants
Rect  4 16 4 12    $pants
Rect  8 16 4 12    $pants
Rect 12 16 4 12    $pants
Rect 16 48 4 12    $pants
Rect 20 48 4 12    $pants
Rect 24 48 4 12    $pants
Rect 28 48 4 12    $pants

# ------------------------------------------------------------------ head
$fx = 8; $fy = 8
Rect $fx $fy 8 8 $skin
Rect ($fx + 0) ($fy + 0) 8 2 $hair          # short hair
Rect ($fx + 0) ($fy + 0) 1 3 $hairDark      # temples (greying)
Rect ($fx + 7) ($fy + 0) 1 3 $hairDark
Rect ($fx + 1) ($fy + 3) 2 1 $eyeWhite
Rect ($fx + 5) ($fy + 3) 2 1 $eyeWhite
$bmp.SetPixel(($fx + 2), ($fy + 3), $eyeIris)
$bmp.SetPixel(($fx + 5), ($fy + 3), $eyeIris)
Rect ($fx + 1) ($fy + 2) 2 1 $hairDark      # heavy brow
Rect ($fx + 5) ($fy + 2) 2 1 $hairDark
Rect ($fx + 2) ($fy + 4) 2 1 $skinDark      # nose shadow
Rect ($fx + 1) ($fy + 5) 6 1 $beard         # stubble
Rect ($fx + 0) ($fy + 5) 1 3 $beard
Rect ($fx + 7) ($fy + 5) 1 3 $beard
Rect ($fx + 3) ($fy + 5) 2 1 $skin          # mouth
Rect ($fx + 2) ($fy + 6) 1 1 $soot          # soot smear on the cheek
Rect ($fx + 5) ($fy + 6) 1 1 $soot

# ------------------------------------------------------------------ leather apron + straps
$bx = 20; $by = 20
Rect $bx $by 8 12 $shirt
Rect ($bx + 0) ($by + 0) 8 2 $leather       # chest strap band
Rect ($bx + 3) ($by + 0) 2 2 $leatherD      # buckle
Rect ($bx + 1) ($by + 2) 6 10 $apron        # apron body
Rect ($bx + 1) ($by + 9) 6 3 $leatherD      # apron lower hem (darker)
Rect ($bx + 2) ($by + 3) 1 6 $shirt         # shirt peeking at the edge
Rect ($bx + 6) ($by + 3) 1 6 $shirt
# apron wraps the sides of the torso
Rect 16 20 4 12 $leather
Rect 28 20 4 12 $leather

# ------------------------------------------------------------------ arms: rolled sleeves, bare forearms
foreach ($ax in @(44, 36)) {
    $ay = if ($ax -eq 44) { 20 } else { 52 }
    Rect $ax ($ay + 0) 4 3 $shirt        # rolled sleeve
    Rect $ax ($ay + 3) 4 1 $shirtSh      # fold
    Rect $ax ($ay + 4) 4 8 $skin         # bare forearm
    Rect $ax ($ay + 6) 4 1 $soot         # soot line
    Rect ($ax - 4) ($ay) 4 12 $shirt
}

# ------------------------------------------------------------------ boots
foreach ($lx in @(0, 16)) {
    $ly = if ($lx -eq 0) { 16 } else { 48 }
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
    $pvOut = Join-Path $env:TEMP 'cava-skin-preview.png'
    $pv.Save($pvOut, [System.Drawing.Imaging.ImageFormat]::Png)
    $pv.Dispose()
    Write-Output ("preview: {0}" -f $pvOut)
}

$bmp.Dispose()
