# gen_beam_icons.ps1
# Generates 5 spell icons (32x32 ARGB PNG) for the "light beam" magic chain:
#   light_beam / great_light_beam / giant_light_beam / holy_light_descent / radiant_barrage
#
# Writes each icon to BOTH locations the project uses:
#   1) <repo>\src\main\resources\assets\tnc\textures\spell\
#   2) <modpack>\kubejs\assets\tnc\textures\spell\
#
# Idempotent: recreates every file from scratch on each run.
# ASCII-only source on purpose (no non-ASCII literals) to survive encoding round-trips.
# The modpack folder names are non-ASCII, so they are reconstructed from code points.
#
# Usage:
#   pwsh -File D:\ModTest\tools\gen_beam_icons.ps1
#   pwsh -File D:\ModTest\tools\gen_beam_icons.ps1 -PackRoot 'E:\some\pack'
#   pwsh -File D:\ModTest\tools\gen_beam_icons.ps1 -RepoRoot 'D:\ModTest'

param(
    [string]$RepoRoot = 'D:\ModTest',
    [string]$PackRoot = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# ---------------------------------------------------------------- constants
$SIZE = 32          # final icon edge length, identical to the existing icons
$script:SS = 8      # supersampling factor
$BIG = $SIZE * $script:SS

$REL = 'src\main\resources\assets\tnc\textures\spell'
$REL_PACK = 'kubejs\assets\tnc\textures\spell'

# Dark outline used on every motif so it stays readable on the dark spellbook UI.
# Warm near-black amber (not pure black) keeps it in the light/gold family.
$OUTLINE = [System.Drawing.Color]::FromArgb(215, 46, 33, 10)
$OUTLINE_SOFT = [System.Drawing.Color]::FromArgb(120, 46, 33, 10)

# ---------------------------------------------------------------- pack path
function Get-DefaultPackRoot {
    # 'E:\download\' + U+6B63 U+5F0F U+7248 + ' 2.12.6.1' + '\.minecraft\versions\' +
    # U+5143 U+7D20 U+89C9 U+9192 + '1.4.3-' + U+9B54 U+6539 U+7248 + '-20260915'
    $cps1 = @(0x6B63, 0x5F0F, 0x7248)
    $cps2 = @(0x5143, 0x7D20, 0x89C9, 0x9192)
    $cps3 = @(0x9B54, 0x6539, 0x7248)
    $s1 = -join ($cps1 | ForEach-Object { [char]$_ })
    $s2 = -join ($cps2 | ForEach-Object { [char]$_ })
    $s3 = -join ($cps3 | ForEach-Object { [char]$_ })
    return ('E:\download\' + $s1 + ' 2.12.6.1\.minecraft\versions\' + $s2 + '1.4.3-' + $s3 + '-20260915')
}

if ([string]::IsNullOrWhiteSpace($PackRoot)) { $PackRoot = Get-DefaultPackRoot }

# ---------------------------------------------------------------- helpers
function New-Color {
    param([int]$r, [int]$g, [int]$b, [int]$a = 255)
    return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

function New-Canvas {
    $bmp = New-Object System.Drawing.Bitmap($BIG, $BIG, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb))
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    return @{ Bitmap = $bmp; G = $g }
}

function Save-Canvas {
    param($Canvas, [string]$Path)
    $small = New-Object System.Drawing.Bitmap($SIZE, $SIZE, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb))
    $sg = [System.Drawing.Graphics]::FromImage($small)
    $sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $sg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $sg.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $sg.DrawImage($Canvas.Bitmap, (New-Object System.Drawing.Rectangle 0, 0, $SIZE, $SIZE))
    $sg.Dispose()
    $Canvas.G.Dispose()
    $Canvas.Bitmap.Dispose()

    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    if (Test-Path $Path) { Remove-Item $Path -Force }
    $small.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $small.Dispose()
}

# Point in icon space -> supersampled PointF
function P {
    param([double]$x, [double]$y)
    return New-Object System.Drawing.PointF([float]($x * $script:SS), [float]($y * $script:SS))
}

function New-RayPath {
    param([double]$x1, [double]$y1, [double]$x2, [double]$y2, [double]$w)
    $dx = $x2 - $x1; $dy = $y2 - $y1
    $len = [Math]::Sqrt($dx * $dx + $dy * $dy)
    $ux = $dx / $len; $uy = $dy / $len
    $px = -$uy * $w / 2.0; $py = $ux * $w / 2.0
    $pts = [System.Drawing.PointF[]]@(
        (P ($x1 + $px) ($y1 + $py)),
        (P ($x2 + $px) ($y2 + $py)),
        (P ($x2 - $px) ($y2 - $py)),
        (P ($x1 - $px) ($y1 - $py))
    )
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddPolygon($pts)
    return $path
}

# Rainbow / multi-stop gradient brush running along a ray
function New-RayBrush {
    param([double]$x1, [double]$y1, [double]$x2, [double]$y2, $Colors, $Positions, [int]$Alpha = 255)
    $c1 = [System.Drawing.Color]::FromArgb($Alpha, $Colors[0].R, $Colors[0].G, $Colors[0].B)
    $cN = [System.Drawing.Color]::FromArgb($Alpha, $Colors[-1].R, $Colors[-1].G, $Colors[-1].B)
    $b = New-Object System.Drawing.Drawing2D.LinearGradientBrush((P $x1 $y1), (P $x2 $y2), $c1, $cN)
    $n = $Colors.Count
    $ca = New-Object 'System.Drawing.Color[]' $n
    for ($i = 0; $i -lt $n; $i++) {
        $ca[$i] = [System.Drawing.Color]::FromArgb($Alpha, $Colors[$i].R, $Colors[$i].G, $Colors[$i].B)
    }
    $cb = New-Object System.Drawing.Drawing2D.ColorBlend($n)
    $cb.Colors = $ca
    $cb.Positions = [single[]]$Positions
    if ($n -gt 2) { $b.InterpolationColors = $cb }
    $b.WrapMode = [System.Drawing.Drawing2D.WrapMode]::TileFlipXY
    return $b
}

function Add-Ray {
    param($G, [double]$x1, [double]$y1, [double]$x2, [double]$y2, [double]$w,
          $Brush, [double]$OutlineW = 1.0, $OutlineColor = $OUTLINE)
    $path = New-RayPath $x1 $y1 $x2 $y2 $w
    if ($OutlineW -gt 0) {
        $pen = New-Object System.Drawing.Pen($OutlineColor, [float](($w + 2.0 * $OutlineW) * $script:SS))
        $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
        $G.DrawPath($pen, $path)
        $pen.Dispose()
    }
    $G.FillPath($Brush, $path)
    $path.Dispose()
}

function Add-SoftRay {
    # wide, low-alpha halo drawn under the outline
    param($G, [double]$x1, [double]$y1, [double]$x2, [double]$y2, [double]$w, $Color)
    $path = New-RayPath $x1 $y1 $x2 $y2 $w
    $br = New-Object System.Drawing.SolidBrush($Color)
    $G.FillPath($br, $path)
    $br.Dispose()
    $path.Dispose()
}

function Add-Ring {
    param($G, [double]$cx, [double]$cy, [double]$r, [double]$w, $Color,
          [double]$OutlineW = 1.0, $OutlineColor = $OUTLINE)
    $rect = New-Object System.Drawing.RectangleF(
        [float](($cx - $r) * $script:SS), [float](($cy - $r) * $script:SS),
        [float](2 * $r * $script:SS), [float](2 * $r * $script:SS))
    if ($OutlineW -gt 0) {
        $pen = New-Object System.Drawing.Pen($OutlineColor, [float](($w + 2.0 * $OutlineW) * $script:SS))
        $G.DrawEllipse($pen, $rect)
        $pen.Dispose()
    }
    $pen2 = New-Object System.Drawing.Pen($Color, [float]($w * $script:SS))
    $G.DrawEllipse($pen2, $rect)
    $pen2.Dispose()
}

function Add-Orb {
    # soft radial glow blob (no outline)
    param($G, [double]$cx, [double]$cy, [double]$r, [int]$cr, [int]$cg, [int]$cb, [int]$ca)
    $rect = New-Object System.Drawing.RectangleF(
        [float](($cx - $r) * $script:SS), [float](($cy - $r) * $script:SS),
        [float](2 * $r * $script:SS), [float](2 * $r * $script:SS))
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($rect)
    try {
        $pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
        $pgb.CenterColor = [System.Drawing.Color]::FromArgb($ca, $cr, $cg, $cb)
        # a single surround colour is applied to all remaining path points
        $pgb.SurroundColors = [System.Drawing.Color[]]@([System.Drawing.Color]::FromArgb(0, $cr, $cg, $cb))
        $G.FillPath($pgb, $path)
        $pgb.Dispose()
    } catch {
        # fallback: concentric rings with falling alpha
        for ($i = 6; $i -ge 1; $i--) {
            $rr = $r * $i / 6.0
            $aa = [int]($ca * [Math]::Pow(1.0 - ($i - 1) / 6.0, 1.6))
            if ($aa -le 0) { continue }
            $br = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($aa, $cr, $cg, $cb))
            $G.FillEllipse($br, [float](($cx - $rr) * $script:SS), [float](($cy - $rr) * $script:SS),
                           [float](2 * $rr * $script:SS), [float](2 * $rr * $script:SS))
            $br.Dispose()
        }
    }
    $path.Dispose()
}

function Add-Disc {
    param($G, [double]$cx, [double]$cy, [double]$r, $Color, [double]$OutlineW = 1.0)
    $rect = New-Object System.Drawing.RectangleF(
        [float](($cx - $r) * $script:SS), [float](($cy - $r) * $script:SS),
        [float](2 * $r * $script:SS), [float](2 * $r * $script:SS))
    if ($OutlineW -gt 0) {
        $pen = New-Object System.Drawing.Pen($OUTLINE, [float](($r * 2 + 2.0 * $OutlineW) * $script:SS))
        $G.DrawEllipse($pen, $rect)
        $pen.Dispose()
    }
    $br = New-Object System.Drawing.SolidBrush($Color)
    $G.FillEllipse($br, $rect)
    $br.Dispose()
}

function Add-Dot {
    param($G, [double]$cx, [double]$cy, [double]$r, $Color)
    $br = New-Object System.Drawing.SolidBrush($Color)
    $G.FillEllipse($br, [float](($cx - $r) * $script:SS), [float](($cy - $r) * $script:SS),
                   [float](2 * $r * $script:SS), [float](2 * $r * $script:SS))
    $br.Dispose()
}

# ---------------------------------------------------------------- palette
$WHITE = New-Color 255 255 255
$WARM = New-Color 255 252 232
$GOLD = New-Color 255 222 120
$PALEGOLD = New-Color 255 240 190

$RAINBOW = @(
    (New-Color 255 70 70), (New-Color 255 150 45), (New-Color 255 240 95),
    (New-Color 130 255 120), (New-Color 95 230 255), (New-Color 95 130 255),
    (New-Color 195 110 255)
)
$RAINBOW_POS = @(0.0, 0.16, 0.33, 0.50, 0.66, 0.83, 1.0)

# ================================================================ 1. light_beam
# Three thin coloured rays fanning to the upper-right from a bright bottom-left origin.
function New-LightBeam {
    $c = New-Canvas
    $g = $c.G
    $ox = 5.0; $oy = 27.0
    $len = 24.0

    # fan angles (degrees, screen space: negative = up-right)
    $specs = @(
        @{ a = -61.0; col = (New-Color 255 105 70);  w = 1.7 },   # red / orange
        @{ a = -45.0; col = (New-Color 210 255 110); w = 1.7 },   # yellow-green
        @{ a = -29.0; col = (New-Color 110 175 255); w = 1.7 }    # blue
    )

    foreach ($s in $specs) {
        $rad = $s.a * [Math]::PI / 180.0
        $ex = $ox + [Math]::Cos($rad) * $len
        $ey = $oy + [Math]::Sin($rad) * $len
        $col = $s.col
        # halo
        Add-SoftRay $g $ox $oy $ex $ey ($s.w * 2.4) (New-Color $col.R $col.G $col.B 55)
        # dark outline + bright core (tapered brush, brighter at the origin)
        $br = New-RayBrush $ox $oy $ex $ey @((New-Color 255 255 255), $col) @(0.0, 1.0) 255
        Add-Ray $g $ox $oy $ex $ey $s.w $br 1.0
        $br.Dispose()
    }

    # bright warm origin spark
    Add-Orb $g $ox $oy 4.6 255 240 170 190
    Add-Disc $g $ox $oy 2.5 $WHITE 1.0
    Add-Dot $g ($ox - 0.7) ($oy - 0.7) 1.1 $GOLD

    Save-Canvas $c (Join-Path $script:RepoSpell 'light_beam.png')
}

# ================================================================ 2. great_light_beam
# One clearly thicker rainbow ray, same direction, with a warm halo.
function New-GreatLightBeam {
    $c = New-Canvas
    $g = $c.G
    $x1 = 5.0; $y1 = 27.0; $x2 = 27.5; $y2 = 4.5
    $w = 3.6

    Add-SoftRay $g $x1 $y1 $x2 $y2 ($w * 2.2) (New-Color 255 235 170 70)
    $br = New-RayBrush $x1 $y1 $x2 $y2 $RAINBOW $RAINBOW_POS 255
    Add-Ray $g $x1 $y1 $x2 $y2 $w $br 1.0
    $br.Dispose()
    # white inner highlight along the middle of the ray
    $br2 = New-RayBrush $x1 $y1 $x2 $y2 @((New-Color 255 255 255), (New-Color 255 250 225)) @(0.0, 1.0) 200
    Add-Ray $g $x1 $y1 $x2 $y2 1.2 $br2 0
    $br2.Dispose()

    Add-Orb $g $x1 $y1 4.4 255 235 160 175
    Add-Disc $g $x1 $y1 2.3 $WHITE 1.0

    Save-Canvas $c (Join-Path $script:RepoSpell 'great_light_beam.png')
}

# ================================================================ 3. giant_light_beam
# One VERY thick ray (~2x #2): coloured glow + bright white core.
function New-GiantLightBeam {
    $c = New-Canvas
    $g = $c.G
    $x1 = 5.0; $y1 = 27.0; $x2 = 28.0; $y2 = 4.0
    $w = 7.2

    Add-SoftRay $g $x1 $y1 $x2 $y2 ($w * 1.9) (New-Color 255 225 150 75)
    # coloured glow body
    $br = New-RayBrush $x1 $y1 $x2 $y2 $RAINBOW $RAINBOW_POS 235
    Add-Ray $g $x1 $y1 $x2 $y2 $w $br 0.8
    $br.Dispose()
    # gold-white inner band
    $br2 = New-RayBrush $x1 $y1 $x2 $y2 @((New-Color 255 245 205), (New-Color 255 232 165)) @(0.0, 1.0) 255
    Add-Ray $g $x1 $y1 $x2 $y2 3.8 $br2 0
    $br2.Dispose()
    # bright white core
    $br3 = New-RayBrush $x1 $y1 $x2 $y2 @((New-Color 255 255 255), (New-Color 255 255 255)) @(0.0, 1.0) 255
    Add-Ray $g $x1 $y1 $x2 $y2 2.0 $br3 0
    $br3.Dispose()

    Add-Orb $g $x1 $y1 5.4 255 245 190 190
    Add-Disc $g $x1 $y1 2.8 $WHITE 1.0

    Save-Canvas $c (Join-Path $script:RepoSpell 'giant_light_beam.png')
}

# ================================================================ 4. holy_light_descent
# Line-based ("storm" style) magic circle at the top, very thick vertical beam
# falling straight down from it to the bottom edge.
function New-HolyLightDescent {
    $c = New-Canvas
    $g = $c.G
    $cx = 16.0; $cy = 8.0
    $rOut = 6.0
    $rIn = 3.9

    # ---- beam first (so the circle reads on top of it)
    $bx = 16.0
    $by1 = 12.0; $by2 = 32.0
    $bw = 7.4
    Add-SoftRay $g $bx $by1 $bx $by2 ($bw * 1.7) (New-Color 255 235 175 90)
    $br = New-RayBrush $bx $by1 $bx $by2 @((New-Color 255 226 140), (New-Color 255 245 205)) @(0.0, 1.0) 245
    Add-Ray $g $bx $by1 $bx $by2 $bw $br 0.9
    $br.Dispose()
    $br2 = New-RayBrush $bx $by1 $bx $by2 @($WARM, $WHITE) @(0.0, 1.0) 255
    Add-Ray $g $bx $by1 $bx $by2 3.0 $br2 0
    $br2.Dispose()

    # ---- LINE circle ("storm" style): thin rings + radial ticks, hollow centre
    Add-Ring $g $cx $cy $rOut 1.05 $PALEGOLD 0.8
    Add-Ring $g $cx $cy $rIn 0.75 $WARM 0.65
    for ($i = 0; $i -lt 8; $i++) {
        $a = $i * [Math]::PI / 4.0
        $sx = $cx + [Math]::Cos($a) * $rIn
        $sy = $cy + [Math]::Sin($a) * $rIn
        $ex = $cx + [Math]::Cos($a) * $rOut
        $ey = $cy + [Math]::Sin($a) * $rOut
        $col = if ($i % 2 -eq 0) { $GOLD } else { $WARM }
        $brt = New-RayBrush $sx $sy $ex $ey @($col, $col) @(0.0, 1.0) 255
        Add-Ray $g $sx $sy $ex $ey 0.55 $brt 0.3 $OUTLINE_SOFT
        $brt.Dispose()
    }
    # tiny focus spark only - the disc itself stays open (line circle, not filled)
    Add-Dot $g $cx $cy 0.75 $WHITE

    Save-Canvas $c (Join-Path $script:RepoSpell 'holy_light_descent.png')
}

# ================================================================ 5. radiant_barrage
# Five small line-circles across the upper part, each raining a short thick beam.
function New-RadiantBarrage {
    $c = New-Canvas
    $g = $c.G

    $r = 2.7
    $xs = @(5.0, 10.5, 16.0, 21.5, 27.0)
    $ys = @(7.4, 5.6, 4.8, 5.6, 7.4)
    $lens = @(7.0, 10.0, 12.5, 10.0, 7.0)
    $tints = @(
        (New-Color 255 245 200), (New-Color 255 226 140), (New-Color 205 255 215),
        (New-Color 195 225 255), (New-Color 255 245 200)
    )

    for ($i = 0; $i -lt 5; $i++) {
        $cx = $xs[$i]; $cy = $ys[$i]; $col = $tints[$i]
        $by1 = $cy + $r * 0.6
        $by2 = [Math]::Min(31.8, $cy + $r + $lens[$i])
        $bw = 2.1

        # falling beam
        Add-SoftRay $g $cx $by1 $cx $by2 ($bw * 2.3) (New-Color $col.R $col.G $col.B 65)
        $br = New-RayBrush $cx $by1 $cx $by2 @($WHITE, $col) @(0.0, 1.0) 255
        Add-Ray $g $cx $by1 $cx $by2 $bw $br 0.85
        $br.Dispose()

        # line circle (ring only - keeps it readable as a LINES motif at 32px)
        Add-Ring $g $cx $cy $r 0.8 $col 0.6
        Add-Dot $g $cx $cy 0.55 $WHITE
    }

    Save-Canvas $c (Join-Path $script:RepoSpell 'radiant_barrage.png')
}

# ---------------------------------------------------------------- run
$script:RepoSpell = Join-Path $RepoRoot $REL
$packSpell = Join-Path $PackRoot $REL_PACK

Write-Host ''
Write-Host '=== light beam icon generator ==='
Write-Host ("repo spell dir : {0}" -f $script:RepoSpell)
Write-Host ("pack spell dir : {0}" -f $packSpell)
Write-Host ''

if (-not (Test-Path $script:RepoSpell)) { New-Item -ItemType Directory -Force -Path $script:RepoSpell | Out-Null }

$names = @(
    'light_beam.png',
    'great_light_beam.png',
    'giant_light_beam.png',
    'holy_light_descent.png',
    'radiant_barrage.png'
)

New-LightBeam
New-GreatLightBeam
New-GiantLightBeam
New-HolyLightDescent
New-RadiantBarrage

# ---- copy into the modpack override dir
$packOk = $true
if (-not (Test-Path $packSpell)) {
    try {
        New-Item -ItemType Directory -Force -Path $packSpell | Out-Null
    } catch {
        $packOk = $false
        Write-Warning ("could not create pack dir: {0}" -f $_.Exception.Message)
    }
}
if ($packOk) {
    if (-not (Test-Path $packSpell)) { $packOk = $false }
}
if ($packOk) {
    foreach ($n in $names) {
        Copy-Item -LiteralPath (Join-Path $script:RepoSpell $n) -Destination (Join-Path $packSpell $n) -Force
    }
}

# ---------------------------------------------------------------- verification
Write-Host '--- verification ---'
$fmt = '{0,-26} {1,-10} {2,8} {3,10} {4,8} {5,7} {6,7}'
Write-Host ($fmt -f 'file', 'location', 'bytes', 'size', 'opaque', 'minA', 'maxA')

$fail = 0
foreach ($n in $names) {
    foreach ($loc in @(@{ tag = 'repo'; dir = $script:RepoSpell }, @{ tag = 'pack'; dir = $packSpell })) {
        $p = Join-Path $loc.dir $n
        if (-not (Test-Path $p)) {
            Write-Host ($fmt -f $n, $loc.tag, 'MISSING', '-', '-', '-', '-')
            $fail++
            continue
        }
        $fi = Get-Item -LiteralPath $p
        $bmp = New-Object System.Drawing.Bitmap($p)
        $w = $bmp.Width; $h = $bmp.Height
        $opaque = 0; $minA = 255; $maxA = 0
        for ($y = 0; $y -lt $h; $y++) {
            for ($x = 0; $x -lt $w; $x++) {
                $a = $bmp.GetPixel($x, $y).A
                if ($a -gt 0) { $opaque++ }
                if ($a -lt $minA) { $minA = $a }
                if ($a -gt $maxA) { $maxA = $a }
            }
        }
        $bmp.Dispose()
        $okSize = ($w -eq $SIZE -and $h -eq $SIZE)
        $okBytes = ($fi.Length -gt 0)
        $okPix = ($opaque -gt 0)
        if (-not ($okSize -and $okBytes -and $okPix)) { $fail++ }
        Write-Host ($fmt -f $n, $loc.tag, $fi.Length, ("{0}x{1}" -f $w, $h), $opaque, $minA, $maxA)
    }
}

Write-Host ''
Write-Host ("written: {0} files into repo dir" -f $names.Count)
if ($packOk) {
    Write-Host ("written: {0} files into pack dir" -f $names.Count)
} else {
    Write-Host 'written: 0 files into pack dir (pack dir unavailable)'
    $fail++
}
Write-Host ''
if ($fail -eq 0) {
    Write-Host 'RESULT: OK - all 10 files verified (32x32, non-empty, non-transparent pixels present).'
} else {
    Write-Host ("RESULT: FAIL - {0} verification problem(s)." -f $fail)
    exit 1
}
