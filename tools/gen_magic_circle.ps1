# gen_magic_circle.ps1 -- draw the ground magic-circle texture used by TN-C's
# legendary / god-tier thunder-orb spells (and, scaled up, by the shockwave ring).
#
# Drawn from scratch (no third-party art), 128x128 RGBA with a transparent
# background so it can be laid flat on the ground as a decal.
#
# Look: PURE YELLOW (author 2026-09-22: the lightning magic circle should be pure
# yellow) - a bright ring, a deeper-yellow inner ring and six-point star, and
# pale-yellow rune diamonds. Only the yellow hue is used, so it reads as one colour.
#
# NOTE: pure ASCII on purpose. Windows PowerShell 5.1 reads a BOM-less .ps1 as
# ANSI, so a CJK comment here turns into mojibake and can even swallow the next
# line (that happened once, and the script died halfway).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\gen_magic_circle.ps1
#   powershell -ExecutionPolicy Bypass -File tools\gen_magic_circle.ps1 -Preview

param(
    [string]$Out = '',
    [switch]$Preview
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not $Out) {
    $repo = Split-Path $PSScriptRoot -Parent
    $Out = Join-Path $repo 'src\main\resources\assets\tnc\textures\entity\magic_circle.png'
}

$size = 128
$bmp = New-Object System.Drawing.Bitmap -ArgumentList $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))

function Col([int]$r, [int]$gr, [int]$b, [int]$a) { [System.Drawing.Color]::FromArgb($a, $r, $gr, $b) }

# ---- palette: yellow only ----
$main = Col 255 233 128 235      # bright yellow: outer ring, ticks, centre
$dim  = Col 255 214  92 130      # dimmer yellow: soft glow / inner ring
$deep = Col 255 190  40 220      # deep yellow: six-point star
$pale = Col 255 248 200 210      # pale yellow: rune diamonds / centre dot

function Ring([double]$radius, [double]$width, $color) {
    $pen = New-Object System.Drawing.Pen -ArgumentList $color, $width
    $d = $radius * 2.0
    $g.DrawEllipse($pen, [single](($size - $d) / 2.0), [single](($size - $d) / 2.0), [single]$d, [single]$d)
    $pen.Dispose()
}

# ---- soft glow, then the crisp rings ----
foreach ($i in 1..6) {
    $alpha = [int](26 / $i)
    Ring (56 + $i * 1.6) 3.0 (Col 255 233 128 $alpha)
}
Ring 55 2.6 $dim              # outer
Ring 56 1.4 $main
Ring 40 1.2 $deep             # inner
Ring 26 1.0 $dim
Ring 10 1.6 $main

# ---- 12 rune ticks between the two main rings ----
for ($i = 0; $i -lt 12; $i++) {
    $a = $i * (2.0 * [Math]::PI / 12.0)
    $r1 = 45.0
    $r2 = 51.0
    $pen = New-Object System.Drawing.Pen -ArgumentList $main, 2.4
    $x1 = 64 + [Math]::Cos($a) * $r1
    $y1 = 64 + [Math]::Sin($a) * $r1
    $x2 = 64 + [Math]::Cos($a) * $r2
    $y2 = 64 + [Math]::Sin($a) * $r2
    $g.DrawLine($pen, [single]$x1, [single]$y1, [single]$x2, [single]$y2)
    $pen.Dispose()
    # small diamond at mid radius
    $rm = 36.0
    $xm = 64 + [Math]::Cos($a + 0.26) * $rm
    $ym = 64 + [Math]::Sin($a + 0.26) * $rm
    $pts = @(
        (New-Object System.Drawing.PointF -ArgumentList ([single]$xm), ([single]($ym - 3))),
        (New-Object System.Drawing.PointF -ArgumentList ([single]($xm + 3)), ([single]$ym)),
        (New-Object System.Drawing.PointF -ArgumentList ([single]$xm), ([single]($ym + 3))),
        (New-Object System.Drawing.PointF -ArgumentList ([single]($xm - 3)), ([single]$ym))
    )
    $g.FillPolygon((New-Object System.Drawing.SolidBrush -ArgumentList $pale), $pts)
}

# ---- six-point star (two triangles) ----
function Star([double]$radius, [double]$rot, $color, [double]$width) {
    $pen = New-Object System.Drawing.Pen -ArgumentList $color, $width
    foreach ($tri in 0..1) {
        $pts = @()
        for ($k = 0; $k -lt 3; $k++) {
            $a = $rot + $tri * [Math]::PI + $k * (2.0 * [Math]::PI / 3.0)
            $pts += New-Object System.Drawing.PointF -ArgumentList ([single](64 + [Math]::Cos($a) * $radius)), ([single](64 + [Math]::Sin($a) * $radius))
        }
        $g.DrawPolygon($pen, $pts)
    }
    $pen.Dispose()
}
Star 34 0.0 $deep 1.6
Star 22 0.52 $main 1.2

# ---- centre ----
$g.FillEllipse((New-Object System.Drawing.SolidBrush -ArgumentList $main), 60, 60, 8, 8)
$g.FillEllipse((New-Object System.Drawing.SolidBrush -ArgumentList $pale), 62, 62, 4, 4)

$g.Dispose()

$dir = Split-Path $Out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output ("saved: {0}  ({1} bytes)" -f $Out, (Get-Item $Out).Length)

if ($Preview) {
    $pv = New-Object System.Drawing.Bitmap -ArgumentList ($size * 3), ($size * 3)
    $pg = [System.Drawing.Graphics]::FromImage($pv)
    $pg.Clear([System.Drawing.Color]::FromArgb(20, 20, 26))
    $pg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $pg.DrawImage($bmp, 0, 0, $size * 3, $size * 3)
    $pg.Dispose()
    $pvOut = Join-Path $env:TEMP 'magic-circle-preview.png'
    $pv.Save($pvOut, [System.Drawing.Imaging.ImageFormat]::Png)
    $pv.Dispose()
    Write-Output ("preview: {0}" -f $pvOut)
}

$bmp.Dispose()
