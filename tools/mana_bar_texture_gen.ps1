# Generate the TN-C mana bar textures (empty track + fill).
#
#   Reproducible art instead of a binary someone has to guess at: change the
#   palette below and re-run. Same idea as tools\wand_texture_gen.ps1.
#
#   ASCII-ONLY ON PURPOSE. Windows PowerShell 5.1 reads a .ps1 as ANSI unless it
#   has a UTF-8 BOM, so non-ASCII comments get mis-decoded and can break how the
#   rest of the file parses (symptoms: wrong line numbers in errors, variables
#   turning into arrays). Every tool script in this repo stays ASCII for that
#   reason - see PROJECT-STATE.md.
#
#   HOW THESE ARE USED (see MagicStoneHud):
#     * both files are the SAME size and the SAME silhouette, so the fill lines
#       up exactly on top of the track
#     * the track is drawn whole; the fill is drawn with a REDUCED WIDTH
#       (= barWidth * mana/maxMana), which crops the texture from the RIGHT
#     * => the fill art must be LEFT-ANCHORED (decorate the left cap, keep the
#       rest uniform), otherwise cropping slices the decoration.
#       (The status-bar mod already in this pack does exactly the same thing.)
#
#   Size: 1 texture pixel = 1 GUI pixel, like vanilla GUI art (vanilla's XP bar
#   texture is 182x5). Do NOT author at 2x: the blit overload used here ties
#   sampled texels to destination pixels 1:1.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File mana_bar_texture_gen.ps1
#
# Exit code 0 = ok.

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$outDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\tnc\textures\gui\mana_bar'
$outDir = [System.IO.Path]::GetFullPath($outDir)
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# ---------------- size (must match the constants in MagicStoneHud) ----------------
$W = 80
$H = 10

# ---------------- palette (same arcane purple as the HUD bar) ----------------
function C([int]$r, [int]$g, [int]$b) { return [System.Drawing.Color]::FromArgb(255, $r, $g, $b) }

$border     = C 185 167 255   # #B9A7FF
$trackDark  = C  42  33  84   # #2A2154
$trackMid   = C  58  47 110   # #3A2F6E
$fillBottom = C 108  79 216   # #6C4FD8
$fillTop    = C 169 140 255   # #A98CFF
$fillShine  = C 228 220 255   # #E4DCFF
$clear      = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)

# Returns a 2-element array: @(bitmap, graphics-for-that-bitmap)
function New-Bar {
    $bmp = New-Object System.Drawing.Bitmap($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gr = [System.Drawing.Graphics]::FromImage($bmp)
    $gr.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $gr.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    return , @($bmp, $gr)
}

# 1px border all round, with the 4 corner pixels punched out (at this size a
# missing corner reads as a rounded corner).
function Draw-Frame($bmp, $gr) {
    $brush = New-Object System.Drawing.SolidBrush($border)
    $gr.FillRectangle($brush, 0, 0, $W, $H)
    $brush.Dispose()
    $bmp.SetPixel(0, 0, $clear)
    $bmp.SetPixel($W - 1, 0, $clear)
    $bmp.SetPixel(0, $H - 1, $clear)
    $bmp.SetPixel($W - 1, $H - 1, $clear)
}

# ---------------- empty track ----------------
$pair = New-Bar
$empty = $pair[0]
$ge = $pair[1]
Draw-Frame $empty $ge
# interior: a slightly lighter band on top so the track reads as recessed
$b1 = New-Object System.Drawing.SolidBrush($trackMid)
$ge.FillRectangle($b1, 1, 1, $W - 2, 3)
$b1.Dispose()
$b2 = New-Object System.Drawing.SolidBrush($trackDark)
$ge.FillRectangle($b2, 1, 4, $W - 2, $H - 5)
$b2.Dispose()
$ge.Dispose()
$emptyPath = Join-Path $outDir 'mana_empty.png'
$empty.Save($emptyPath, [System.Drawing.Imaging.ImageFormat]::Png)
$empty.Dispose()

# ---------------- fill (left-anchored) ----------------
$pair2 = New-Bar
$fill = $pair2[0]
$gf = $pair2[1]
Draw-Frame $fill $gf
# vertical gradient, GDI+ does the interpolation
# NOTE: inside `New-Object Type(...)` every arithmetic argument MUST be wrapped
# in its own parentheses. PowerShell's comma operator binds TIGHTER than `-`, so
#     New-Object Foo(1, $W - 2)
# is parsed as (1, $W) - 2  =>  "Object[] does not contain op_Subtraction".
# Method calls like $g.FillRectangle($b, $W - 2) are unaffected.
$rect = New-Object System.Drawing.Rectangle(1, 1, ($W - 2), ($H - 2))
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $fillTop, $fillBottom, [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
$gf.FillRectangle($brush, $rect)
$brush.Dispose()
# a bright line on top so the bar looks like it has thickness
$shine = New-Object System.Drawing.SolidBrush($fillShine)
$gf.FillRectangle($shine, 1, 1, $W - 2, 1)
$shine.Dispose()
$gf.Dispose()
$fillPath = Join-Path $outDir 'mana_fill.png'
$fill.Save($fillPath, [System.Drawing.Imaging.ImageFormat]::Png)
$fill.Dispose()

Write-Host ("wrote {0}  ({1:N0} bytes)" -f $emptyPath, (Get-Item $emptyPath).Length)
Write-Host ("wrote {0}  ({1:N0} bytes)" -f $fillPath, (Get-Item $fillPath).Length)
Write-Host ("size: {0}x{1} px" -f $W, $H)
