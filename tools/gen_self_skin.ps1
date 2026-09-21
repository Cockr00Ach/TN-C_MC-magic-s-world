# ---------------------------------------------------------------------------
#  Generates a placeholder skin for TN-C's "Self" (tavern keeper) NPC.
#
#  Why generate instead of downloading one:
#    every skin on minecraftskins.com / skinsmc.org etc. is (c) its author by
#    default. This project has been careful about third-party assets, so this
#    one is drawn from scratch -> ours, no licence question. It is a PLACEHOLDER
#    (flat block colours), meant to be replaced by real art later.
#
#  Output: 64x64 PNG in Minecraft's classic (4px arm) skin layout.
#
#  NOTE: this file is deliberately PURE ASCII (no BOM needed on a GBK host).
# ---------------------------------------------------------------------------
param(
    [string]$Out = '',
    [switch]$Preview
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not $Out) {
    $repo = Split-Path $PSScriptRoot -Parent
    $Out = Join-Path $repo 'src\main\resources\assets\tnc\textures\entity\self.png'
}

# ------------------------------------------------------------------ palette
function C([int]$r, [int]$g, [int]$b, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }

$skin     = C 232 186 148
$skinDark = C 205 158 120
$skinLine = C 178 132  98
$hair     = C  74  52  36
$hairGray = C 150 148 142
$beard    = C  92  66  44
$eyeWhite = C 240 240 238
$eyeIris  = C  86 112 132
$shirt    = C 226 219 200
$shirtSh  = C 198 190 170
$vest     = C 106  74  46
$vestDark = C  78  52  32
$apron    = C 214 200 172
$apronSh  = C 186 170 142
$pants    = C  72  66  60
$boots    = C  52  38  28
$outline  = C  46  34  26

# ------------------------------------------------------------------ helpers
$bmp = New-Object System.Drawing.Bitmap 64, 64, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Rect([int]$x, [int]$y, [int]$w, [int]$h, $col) {
    for ($i = 0; $i -lt $w; $i++) { for ($j = 0; $j -lt $h; $j++) { $bmp.SetPixel($x + $i, $y + $j, $col) } }
}

# Draws the six faces of a box-shaped part.
#   bx,by : top-left of the part's unwrapped strip in the skin file
#   w,h,d : size in pixels (vanilla: head 8,8,8 / body 8,12,4 / arm 4,12,4 / leg 4,12,4)
# Order along the strip: front, right, back, left, top, bottom.
function Part([int]$bx, [int]$by, [int]$w, [int]$h, [int]$d, $base, $shade, $trim) {
    # faces: front/back/left/right all w x h ; top/bottom w x d
  Rect ($bx + $d)          ($by + $d) $w $h $base     # front
  Rect ($bx + $d + $w)     ($by + $d) $d $h $shade    # right side
  Rect ($bx + $d + $w + $d)($by + $d) $w $h $shade    # back
  Rect ($bx)               ($by + $d) $d $h $base     # left side
  Rect ($bx + $d)          ($by)      $w $d $trim     # top
  Rect ($bx + $d + $w)     ($by)      $w $d $shade    # bottom
}

# ------------------------------------------------------------------ body map
# Classic layout (1.8 skin format), coordinates verified against the vanilla map:
#   head (0,0)  body (16,16)  right arm (40,16)  left arm (32,48)
#   right leg (0,16)  left leg (16,48)
Part  0  0 8  8  8 $skin    $skinDark $hair      # head
Part 16 16 8 12  4 $shirt   $shirtSh  $shirt     # body
Part 40 16 4 12  4 $skin    $skinDark $skin      # right arm
Part 32 48 4 12  4 $skin    $skinDark $skin      # left arm
Rect  0 16 4 12    $pants                                 # right leg (flat colour)
Rect  4 16 4 12    $pants
Rect  8 16 4 12    $pants
Rect 12 16 4 12    $pants
Rect 16 48 4 12    $pants                                 # left leg
Rect 20 48 4 12    $pants
Rect 24 48 4 12    $pants
Rect 28 48 4 12    $pants

# ------------------------------------------------------------------ head detail
# front face of head == (8,8)..(15,15)
$fx = 8; $fy = 8
Rect $fx $fy 8 8 $skin                       # base face
Rect ($fx + 0) ($fy + 0) 8 3 $hair           # hairline / top of fringe
Rect ($fx + 0) ($fy + 0) 1 4 $hair           # sideburn left
Rect ($fx + 7) ($fy + 0) 1 4 $hair           # sideburn right
Rect ($fx + 1) ($fy + 4) 2 1 $eyeWhite       # left eye white
Rect ($fx + 5) ($fy + 4) 2 1 $eyeWhite       # right eye white
$bmp.SetPixel(($fx + 2), ($fy + 4), $eyeIris)  # left iris
$bmp.SetPixel(($fx + 5), ($fy + 4), $eyeIris)  # right iris
Rect ($fx + 1) ($fy + 3) 2 1 $skinLine       # brows
Rect ($fx + 5) ($fy + 3) 2 1 $skinLine
Rect ($fx + 2) ($fy + 5) 4 1 $beard          # moustache
Rect ($fx + 0) ($fy + 6) 8 2 $beard          # beard
Rect ($fx + 3) ($fy + 6) 2 1 $skin           # mouth notch
Rect ($fx + 3) ($fy + 7) 2 1 $skinLine       # chin crease
# grey at the temples (he is not young)
Rect ($fx + 0) ($fy + 1) 1 2 $hairGray
Rect ($fx + 7) ($fy + 1) 1 2 $hairGray

# ------------------------------------------------------------------ vest + apron
# body front == (20,20)..(27,31)
$vbx = 20; $vby = 20
Rect $vbx $vby 8 12 $shirt
Rect ($vbx + 0) ($vby + 0) 2 12 $vest      # vest left panel
Rect ($vbx + 6) ($vby + 0) 2 12 $vest      # vest right panel
Rect ($vbx + 2) ($vby + 0) 4 1  $vestDark  # collar
Rect ($vbx + 2) ($vby + 2) 4 10 $apron     # apron centre
Rect ($vbx + 2) ($vby + 9) 4 3  $apronSh   # apron lower shading
Rect ($vbx + 3) ($vby + 3) 2 1  $shirt     # shirt showing through
# apron also wraps onto the sides of the torso (= left/right faces)
Rect 16 20 4 12 $vest
Rect 28 20 4 12 $vest

# ------------------------------------------------------------------ hands + sleeves
# arm front faces: right (44,20), left (36,52)
foreach ($ax in @(44, 36)) {
    $ay = if ($ax -eq 44) { 20 } else { 52 }
    Rect $ax ($ay + 0) 4 4 $shirt        # sleeve
    Rect $ax ($ay + 4) 4 8 $skin         # bare forearm (sleeves rolled up)
    Rect $ax ($ay + 11) 4 1 $skinLine    # knuckle shading
    Rect ($ax - 4) ($ay) 4 12 $shirt     # inner side of arm keeps the sleeve colour
}

# ------------------------------------------------------------------ boots
foreach ($lx in @(0, 16)) {
    $ly = if ($lx -eq 0) { 16 } else { 48 }
    Rect ($lx + 0) ($ly + 9) 16 3 $boots   # bottom band of the whole leg strip = boots
}

# ------------------------------------------------------------------ outline-ish edges
Rect 20 20 8 1 $outline      # shoulder line
Rect 20 31 8 1 $outline      # waist line
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
    $pvOut = Join-Path $env:TEMP 'self-skin-preview.png'
    $pv.Save($pvOut, [System.Drawing.Imaging.ImageFormat]::Png)
    $pv.Dispose()
    Write-Output ("preview: {0}" -f $pvOut)
}

$bmp.Dispose()
