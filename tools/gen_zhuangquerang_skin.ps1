# ---------------------------------------------------------------------------
#  Generates a placeholder skin for TN-C's "zhuangquerang" NPC (G5's retainer,
#  庄鹊让 - the character of scroll five, "代").
#
#  Same approach as gen_self_skin.ps1 / gen_cava_skin.ps1 / gen_huai_skin.ps1:
#  drawn from scratch (no third-party skin), 64x64 classic layout, PLACEHOLDER
#  for real art.
#
#  Look: a maid in the blue-and-white palette - silver hair with a white
#        headdress, navy dress with a white apron, white cuffs and socks,
#        dark shoes. This matches the blue tone the author asked for when she
#        was picked out of the Touhou Little Maid packs (see docs/当前状态.md
#        section 4).
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
    $Out = Join-Path $repo 'src\main\resources\assets\tnc\textures\entity\zhuangquerang.png'
}

function C([int]$r, [int]$g, [int]$b, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }

# ------------------------------------------------------------------ palette
$skin      = C 232 196 168
$skinDark  = C 206 166 136
$hair      = C 214 221 232      # silver
$hairShade = C 172 181 196
$hairLite  = C 240 244 250
$dress     = C  46  74 140      # navy blue
$dressDk   = C  30  52 104
$dressLt   = C  70 106 182
$apron     = C 240 242 246      # white
$apronSh   = C 206 212 222
$eyeWhite  = C 238 242 246
$eyeIris   = C  90 124 164      # blue-grey
$shoe      = C  60  52  58
$sock      = C 232 236 242
$outline   = C  34  30  36

# ------------------------------------------------------------------ helpers
$bmp = New-Object System.Drawing.Bitmap 64, 64, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Rect([int]$x, [int]$y, [int]$w, [int]$h, $col) {
    for ($i = 0; $i -lt $w; $i++) { for ($j = 0; $j -lt $h; $j++) { $bmp.SetPixel($x + $i, $y + $j, $col) } }
}

function Part([int]$bx, [int]$by, [int]$w, [int]$h, [int]$d, $base, $shade, $trim) {
    Rect ($bx + $d)           ($by + $d) $w $h $base    # front
    Rect ($bx + $d + $w)      ($by + $d) $d $h $shade   # left side
    Rect ($bx + $d + $w + $d) ($by + $d) $w $h $shade   # back
    Rect ($bx)                ($by + $d) $d $h $base    # right side
    Rect ($bx + $d)           ($by)      $w $d $trim    # top
    Rect ($bx + $d + $w)      ($by)      $w $d $shade   # bottom
}

# ------------------------------------------------------------------ body map
Part  0  0 8  8  8 $skin  $skinDark $hair     # head (hair on top)
Part 32  0 8  8  8 $hair  $hairShade $hairLite  # head outer layer = hair volume
Part 16 16 8 12  4 $dress $dressDk $apron     # torso: navy dress
Part 40 16 4 12  4 $dress $dressDk $apron     # right arm
Part 32 48 4 12  4 $dress $dressDk $apron     # left arm
Part  0 16 4 12  4 $dress $dressDk $sock      # right leg
Part 16 48 4 12  4 $dress $dressDk $sock      # left leg

# ------------------------------------------------------------------ face
$fx = 8; $fy = 8
Rect $fx $fy 8 8 $skin
Rect ($fx + 0) ($fy + 0) 8 2 $hair            # fringe
Rect ($fx + 0) ($fy + 0) 1 5 $hair            # side locks
Rect ($fx + 7) ($fy + 0) 1 5 $hair
Rect ($fx + 1) ($fy + 1) 2 1 $hairLite        # fringe highlight
Rect ($fx + 1) ($fy + 4) 2 1 $eyeWhite
Rect ($fx + 5) ($fy + 4) 2 1 $eyeWhite
$bmp.SetPixel(($fx + 2), ($fy + 4), $eyeIris)
$bmp.SetPixel(($fx + 5), ($fy + 4), $eyeIris)
Rect ($fx + 1) ($fy + 3) 2 1 $hairShade       # brows
Rect ($fx + 5) ($fy + 3) 2 1 $hairShade
Rect ($fx + 3) ($fy + 5) 2 1 $skinDark        # nose
Rect ($fx + 3) ($fy + 6) 2 1 $skinDark        # mouth
Rect ($fx + 3) ($fy + 7) 2 1 $skin            # chin

# ------------------------------------------------------------------ white headdress on the outer layer
# the classic maid band, sitting on the crown and down the back of the head
Rect 40 0 8 2 $apron
Rect 48 0 8 2 $apron
Rect 56 0 8 2 $apron
Rect 40 8 8 2 $apron
Rect 56 8 8 3 $apron

# ------------------------------------------------------------------ apron on the dress
$bx = 20; $by = 20
Rect $bx $by 8 12 $dress                       # redo the front face cleanly
Rect ($bx + 2) ($by + 0) 4 11 $apron           # apron panel
Rect ($bx + 2) ($by + 10) 4 1 $apronSh         # hem
Rect ($bx + 3) ($by + 1) 2 1 $apronSh          # bib seam
Rect ($bx + 0) ($by + 0) 2 12 $dressLt         # dress showing at the sides
Rect ($bx + 6) ($by + 0) 2 12 $dressLt
Rect ($bx + 0) ($by + 0) 1 12 $dressDk         # side shading
Rect ($bx + 7) ($by + 0) 1 12 $dressDk

# ------------------------------------------------------------------ sleeves, cuffs, hands
foreach ($ax in @(44, 36)) {
    $ay = if ($ax -eq 44) { 20 } else { 52 }
    Rect $ax ($ay + 0) 4 12 $dress             # sleeve
    Rect ($ax + 0) ($ay + 0) 1 12 $dressDk     # shading
    Rect $ax ($ay + 0) 4 1 $dressLt            # shoulder highlight
    Rect $ax ($ay + 8) 4 1 $apron              # white cuff
    Rect $ax ($ay + 9) 4 3 $skin               # hand
    Rect ($ax - 4) ($ay) 4 12 $dress           # inner face of the arm
}

# ------------------------------------------------------------------ skirt, socks, shoes
foreach ($lx in @(0, 16)) {
    $ly = if ($lx -eq 0) { 16 } else { 48 }
    Rect ($lx + 0) ($ly + 0) 16 5 $dress       # skirt covers the thigh
    Rect ($lx + 0) ($ly + 0) 1 5 $dressDk
    Rect ($lx + 0) ($ly + 5) 16 5 $sock        # socks
    Rect ($lx + 0) ($ly + 10) 16 2 $shoe       # shoes
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
    $pvOut = Join-Path $env:TEMP 'zhuangquerang-skin-preview.png'
    $pv.Save($pvOut, [System.Drawing.Imaging.ImageFormat]::Png)
    $pv.Dispose()
    Write-Output ("preview: {0}" -f $pvOut)
}

$bmp.Dispose()
