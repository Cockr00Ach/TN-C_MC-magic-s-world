# Generate the TN-C magic wand item texture (16x16).
#
#   Standard "item/handheld" orientation: the handle sits in the bottom-left
#   corner and the business end points up-right at 45 degrees.
#
#   Kept as a script (not a binary blob checked in blindly) so the texture is
#   reproducible and easy to tweak: change the colours below and re-run.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File wand_texture_gen.ps1
#
# Exit code 0 = ok.

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$outDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\tnc\textures\item'
$outDir = [System.IO.Path]::GetFullPath($outDir)
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outFile = Join-Path $outDir 'magic_wand.png'

$size = 16
$bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Set-Px($bmp, $x, $y, $color) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge 16 -or $y -ge 16) { return }
    $bmp.SetPixel($x, $y, $color)
}

# --- palette ---
$woodDark  = [System.Drawing.Color]::FromArgb(255, 74, 48, 26)
$woodMid   = [System.Drawing.Color]::FromArgb(255, 122, 82, 44)
$woodLight = [System.Drawing.Color]::FromArgb(255, 160, 112, 62)
$gripDark  = [System.Drawing.Color]::FromArgb(255, 52, 34, 20)
$goldDark  = [System.Drawing.Color]::FromArgb(255, 140, 104, 28)
$goldLight = [System.Drawing.Color]::FromArgb(255, 226, 186, 74)
$gemCore   = [System.Drawing.Color]::FromArgb(255, 190, 240, 255)
$gemMid    = [System.Drawing.Color]::FromArgb(255, 92, 190, 240)
$gemEdge   = [System.Drawing.Color]::FromArgb(255, 40, 110, 190)

# transparent background
for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) { Set-Px $bmp $x $y ([System.Drawing.Color]::FromArgb(0, 0, 0, 0)) }
}

# --- shaft: diagonal from bottom-left (3,14) up to (10,5) ---
for ($i = 0; $i -lt 9; $i++) {
    $x = 3 + $i
    $y = 14 - $i
    Set-Px $bmp $x $y $woodMid
    Set-Px $bmp ($x + 1) $y $woodDark      # shaded right edge
    Set-Px $bmp $x ($y - 1) $woodLight     # lit top edge
}

# --- grip: darker section at the bottom of the shaft ---
foreach ($p in @(@(3,14), @(4,13), @(5,12), @(4,14), @(5,13), @(6,12))) {
    Set-Px $bmp $p[0] $p[1] $gripDark
}

# --- collar: a small gold band near the top of the shaft ---
foreach ($p in @(@(9,6), @(10,6), @(9,5), @(10,5))) {
    Set-Px $bmp $p[0] $p[1] $goldDark
}
Set-Px $bmp 9 6 $goldLight

# --- gem: cluster at the tip (up-right) ---
Set-Px $bmp 11 4 $gemEdge
Set-Px $bmp 12 4 $gemMid
Set-Px $bmp 11 3 $gemMid
Set-Px $bmp 12 3 $gemCore
Set-Px $bmp 13 3 $gemMid
Set-Px $bmp 12 2 $gemMid
Set-Px $bmp 13 2 $gemEdge
Set-Px $bmp 11 2 $gemEdge
Set-Px $bmp 12 1 $gemEdge
Set-Px $bmp 13 4 $gemEdge

# --- a couple of sparkles so it reads as "magic" at 16px ---
Set-Px $bmp 14 5 ([System.Drawing.Color]::FromArgb(220, 220, 250, 255))
Set-Px $bmp 10 1 ([System.Drawing.Color]::FromArgb(200, 220, 250, 255))

$bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Host "wrote $outFile"
Write-Host ("size: {0:N0} bytes" -f (Get-Item $outFile).Length)
