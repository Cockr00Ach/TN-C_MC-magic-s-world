# TN-C spell icon generator
#   Draws 16x16 pixel-art spell icons from ASCII masks and saves them as PNG.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File spell_icon_gen.ps1
#
# Output:
#   <workpack>\config\openloader\resources\TN-C\assets\tnc\textures\spell\<name>.png
#   <preview>  an 8x magnified contact sheet, for eyeballing the result
#
# The modpack folder is auto-detected (same rule as modpack\sync.ps1: the only
# folder under D:\ModTest\modpack), so this file stays pure ASCII and does not
# need a UTF-8 BOM.
#
# Mask characters:
#   .  transparent      #  outline (dark)     +  mid tone
#   *  core (bright)    o  accent

param(
    [string]$WorkPack = '',
    [string]$Preview  = 'D:\ModTest\design\refs\icon-preview.png'
)

Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($WorkPack)) {
    $packDirs = @(Get-ChildItem 'D:\ModTest\modpack' -Directory | Where-Object { $_.Name -ne 'archive' })
    if ($packDirs.Count -ne 1) { Write-Host "ERROR: expected exactly 1 modpack folder under D:\ModTest\modpack, found $($packDirs.Count)"; exit 1 }
    $WorkPack = $packDirs[0].FullName
}

$icons = @(
    @{
        name = 'spark'
        palette = @{ outline = '#7A5B00'; mid = '#FFD84D'; core = '#FFFBE0'; accent = '#FFF176' }
        mask = @(
            '................',
            '................',
            '.........#*#....',
            '........#*#.....',
            '.......#*#......',
            '......#*#.......',
            '.....#*#........',
            '....#*#.........',
            '....#*##........',
            '......#*#.......',
            '.....#*#........',
            '....#*#.........',
            '...#*#..........',
            '...#*#..........',
            '....#...........',
            '................'
        )
    },
    @{
        name = 'lightning_field'
        palette = @{ outline = '#6B4E00'; mid = '#FFE066'; core = '#FFFFFF'; accent = '#9BE7FF' }
        mask = @(
            '................',
            '................',
            '.........#*#....',
            '........#*#.....',
            '.......#*#......',
            '......#*#.......',
            '.....#*#........',
            '....#*#.........',
            '....#*##........',
            '......#*#.......',
            '.....#*#........',
            '....#*#.........',
            '...#*#..........',
            '...#*#..........',
            '....#...........',
            '..o.o.o.o.o.o...'
        )
    },
    @{
        name = 'lightning_strike'
        palette = @{ outline = '#5A4A00'; mid = '#FFEB80'; core = '#FFFFFF'; accent = '#FFFFFF' }
        mask = @(
            '................',
            '................',
            '.........#*#....',
            '........#*#.....',
            '.......#*#......',
            '......#*#.......',
            '......#*#.......',
            '.....#*#........',
            '......#*#.......',
            '......#*#.......',
            '.....#*#........',
            '....#*#.........',
            '....#*#.........',
            '..o#*#o.........',
            '.oo*****oo......',
            '..ooooooo.......'
        )
    },
    @{
        name = 'lightning_storm'
        palette = @{ outline = '#2E2A6B'; mid = '#B9A7FF'; core = '#FFFFFF'; accent = '#E0D4FF' }
        mask = @(
            '................',
            '...##..###..##..',
            '..############..',
            '.##############.',
            '..############..',
            '......#*#.......',
            '.....#*#........',
            '....#*#.........',
            '....#*##........',
            '......#*#.......',
            '.......#*#......',
            '........#*#.....',
            '........#*#.....',
            '.........#*#....',
            '..........#.....',
            '................'
        )
    },
    @{
        name = 'heavenly_thunder'
        palette = @{ outline = '#6B3F00'; mid = '#FFC93C'; core = '#FFFFFF'; accent = '#FFF3B0' }
        mask = @(
            '................',
            '...##..###..##..',
            '..############..',
            '.##############.',
            '..############..',
            '.#*.#*.#*.#*.#*.',
            '.#*.#*.#*.#*.#*.',
            '.#*.#*.#*.#*.#*.',
            '....#*.#*.#*....',
            '....#*.#*.#*....',
            '.......#*.......',
            '.......#*.......',
            '..o..o..o..o..o.',
            '................',
            '................',
            '................'
        )
    }
)

function Get-Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

# ---------------------------------------------------------------- validate
$errors = 0
foreach ($icon in $icons) {
    if ($icon.mask.Count -ne 16) { Write-Host "ERROR [$($icon.name)] mask has $($icon.mask.Count) rows, expected 16"; $errors++ }
    for ($r = 0; $r -lt $icon.mask.Count; $r++) {
        if ($icon.mask[$r].Length -ne 16) {
            Write-Host "ERROR [$($icon.name)] row $r has $($icon.mask[$r].Length) chars, expected 16 : '$($icon.mask[$r])'"
            $errors++
        }
    }
}
if ($errors -gt 0) { Write-Host "$errors mask error(s), aborting."; exit 1 }

# ---------------------------------------------------------------- render
$outDir = Join-Path $WorkPack 'config\openloader\resources\TN-C\assets\tnc\textures\spell'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$scale = 8
$sheet = New-Object System.Drawing.Bitmap (($icons.Count * 16 + ($icons.Count + 1) * 2) * $scale), (16 * $scale + 4 * $scale)
$sheetG = [System.Drawing.Graphics]::FromImage($sheet)
$sheetG.Clear((Get-Color '#1B1B1F'))

foreach ($icon in $icons) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $cOutline = Get-Color $icon.palette.outline
    $cMid     = Get-Color $icon.palette.mid
    $cCore    = Get-Color $icon.palette.core
    $cAccent  = Get-Color $icon.palette.accent

    for ($y = 0; $y -lt 16; $y++) {
        $row = $icon.mask[$y]
        for ($x = 0; $x -lt 16; $x++) {
            $ch = $row[$x]
            $c = switch ($ch) {
                '#' { $cOutline }
                '+' { $cMid }
                '*' { $cCore }
                'o' { $cAccent }
                default { $null }
            }
            if ($null -ne $c) { $bmp.SetPixel($x, $y, $c) }
        }
    }

    $path = Join-Path $outDir "$($icon.name).png"
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("  {0,-18} -> {1} bytes" -f $icon.name, (Get-Item $path).Length)

    # contact sheet, nearest-neighbour magnified
    $idx = [array]::IndexOf($icons, $icon)
    $offsetX = (2 + $idx * 18) * $scale
    $sheetG.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $sheetG.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $sheetG.DrawImage($bmp, $offsetX, 2 * $scale, 16 * $scale, 16 * $scale)
    $bmp.Dispose()
}

New-Item -ItemType Directory -Force -Path (Split-Path $Preview) | Out-Null
$sheet.Save($Preview, [System.Drawing.Imaging.ImageFormat]::Png)
$sheetG.Dispose(); $sheet.Dispose()
Write-Host "workpack: $WorkPack"
Write-Host "preview : $Preview"
