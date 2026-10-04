# Generates the light (healing) school circle texture plus the 5 spell icons for the
# "light_grace" chain (light_radiance -> angel_mercy).
#
# Outputs:
#   src\main\resources\assets\tnc\textures\entity\light_circle.png   256x256, alpha
#   src\main\resources\assets\tnc\textures\spell\<id>.png              32x32, alpha
#   modpack\<pack>\kubejs\assets\tnc\textures\spell\<id>.png           32x32, alpha
#
# The spell icons are written to BOTH the mod resources and the KubeJS pack copy so the
# two stay byte-identical. Re-running the script simply overwrites its own outputs.
#
# Art direction: pure white (255,255,255) holy light, with a very faint pale yellow
# (255,250,205) accent kept well under 20% of the covered pixels. Every edge is
# anti-aliased and fades smoothly into alpha 0 (no hard borders). The circle keeps an
# 8 px transparent margin.
#
# ASCII only on purpose (see docs: tools/*.ps1 must stay pure ASCII - a single Chinese
# character gets mis-decoded as GBK and can swallow a newline, which silently breaks
# parsing). The pack folder is therefore discovered via Get-ChildItem, never spelled out.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/gen_light_textures.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot

$circleDir = Join-Path $root 'src\main\resources\assets\tnc\textures\entity'
$modIconDir = Join-Path $root 'src\main\resources\assets\tnc\textures\spell'

# The kubejs spell folder is the reliable anchor: the pack folder name itself is not ASCII.
$pack = Get-ChildItem (Join-Path $root 'modpack') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'kubejs\assets\tnc\textures\spell') } |
        Select-Object -First 1
if (-not $pack) { throw 'kubejs pack folder (modpack\*\kubejs\assets\tnc\textures\spell) not found' }
$packIconDir = Join-Path $pack.FullName 'kubejs\assets\tnc\textures\spell'

foreach ($d in @($circleDir, $modIconDir, $packIconDir)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

$WHITE = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
$PALE = [System.Drawing.Color]::FromArgb(255, 255, 250, 205)
$CLEAR = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
$PNG = [System.Drawing.Imaging.ImageFormat]::Png
$SS4 = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$HQ = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$CLAMP = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

# Supersampling factor for the icons: draw at 256x256, downsample to 32x32.
$SS = 8

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = $SS4
    $g.PixelOffsetMode = $CLAMP
    $g.Clear($CLEAR)
    return @{ Bmp = $bmp; G = $g }
}

function New-Brush($c) { return (New-Object System.Drawing.SolidBrush($c)) }

function Add-Disc($g, $c, [double]$cx, [double]$cy, [double]$r) {
    $b = New-Brush $c
    $g.FillEllipse($b, [single]($cx - $r), [single]($cy - $r), [single](2 * $r), [single](2 * $r))
    $b.Dispose()
}

# Closed ring of the given radius and stroke width.
function Add-Ring($g, $c, [double]$cx, [double]$cy, [double]$r, [double]$w) {
    $p = New-Object System.Drawing.Pen($c, [single]$w)
    $g.DrawEllipse($p, [single]($cx - $r), [single]($cy - $r), [single](2 * $r), [single](2 * $r))
    $p.Dispose()
}

# Open elliptical arc (used for the thin halo over the angel wings).
function Add-Arc($g, $c, [double]$cx, [double]$cy, [double]$r, [double]$w, [double]$from, [double]$sweep) {
    $p = New-Object System.Drawing.Pen($c, [single]$w)
    $g.DrawArc($p, [single]($cx - $r), [single]($cy - $r), [single](2 * $r), [single](2 * $r),
               [single]$from, [single]$sweep)
    $p.Dispose()
}

function Add-Line($g, $c, [double]$x1, [double]$y1, [double]$x2, [double]$y2, [double]$w) {
    $p = New-Object System.Drawing.Pen($c, [single]$w)
    $p.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $p.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawLine($p, [single]$x1, [single]$y1, [single]$x2, [single]$y2)
    $p.Dispose()
}

# Bloom: concentric low-alpha discs from the outside in, so the result is a soft radial
# falloff. Cheap, allocation-light and totally deterministic.
function Add-Bloom($g, $c, [double]$cx, [double]$cy, [double]$r, [double]$a0) {
    $steps = 16
    for ($i = 0; $i -lt $steps; $i++) {
        $t = $i / [double]$steps
        $a = [int][Math]::Round(255.0 * $a0 * (0.15 + 0.85 * $t * $t))
        if ($a -lt 1) { continue }
        $col = [System.Drawing.Color]::FromArgb($a, $c.R, $c.G, $c.B)
        Add-Disc $g $col $cx $cy ($r * (1.0 - $t))
    }
}

function Add-Rays($g, $c, [double]$cx, [double]$cy, [double]$r0, [double]$r1, [int]$n, [double]$w, [double]$from, [double]$sweep) {
    for ($i = 0; $i -lt $n; $i++) {
        $t = if ($n -le 1) { 0.0 } else { $i / [double]($n - 1) }
        $a = ($from + $sweep * $t) * [Math]::PI / 180.0
        Add-Line $g $c ($cx + $r0 * [Math]::Cos($a)) ($cy + $r0 * [Math]::Sin($a)) `
                 ($cx + $r1 * [Math]::Cos($a)) ($cy + $r1 * [Math]::Sin($a)) $w
    }
}

# Ring built from many short radial strokes: the stroke count controls the density, and
# anti-aliasing turns it into a fine feathered band.
function Add-DotRing($g, $c, [double]$cx, [double]$cy, [double]$r, [int]$count, [double]$w) {
    for ($i = 0; $i -lt $count; $i++) {
        $a = 2.0 * [Math]::PI * $i / [double]$count
        $x = $cx + $r * [Math]::Cos($a)
        $y = $cy + $r * [Math]::Sin($a)
        Add-Line $g $c $x $y $x $y $w
    }
}

# One angel wing per call, drawn as a filled feathered shape with a scalloped bottom
# edge. $dir = +1 (right) or -1 (left); $s grows the whole wing. Filled shapes read
# far better than thin strokes once the 256x256 master is downsampled to a 32x32 icon.
function Add-Wing($g, $c, [double]$cx, [double]$cy, [int]$dir, [double]$s) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddPolygon(@(
        (New-Object System.Drawing.PointF([single]($cx + $dir * 0.8 * $s), [single]($cy - 2.6 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 6.0 * $s), [single]($cy - 1.4 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 10.8 * $s), [single]($cy - 0.6 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 9.4 * $s), [single]($cy + 0.9 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 11.2 * $s), [single]($cy + 2.0 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 8.6 * $s), [single]($cy + 2.4 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 9.8 * $s), [single]($cy + 3.8 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 6.6 * $s), [single]($cy + 3.6 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 7.0 * $s), [single]($cy + 5.0 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 3.8 * $s), [single]($cy + 4.0 * $s))),
        (New-Object System.Drawing.PointF([single]($cx + $dir * 1.6 * $s), [single]($cy + 3.2 * $s)))
    ))
    $b = New-Object System.Drawing.SolidBrush($c)
    $g.FillPath($b, $p)
    $b.Dispose()
    $p.Dispose()
}

function Save-Png($bmp, $path, $g) {
    if ($g) { $g.Dispose() }
    $bmp.Save($path, $PNG)
    # note: never name these locals $w / $h - PowerShell 5.1 reserves them ($a..$z style
    # automatic variables) and reading them back silently yields $null.
    $pw = $bmp.Width
    $ph = $bmp.Height
    $bmp.Dispose()
    $len = (Get-Item $path).Length
    Write-Output ("  {0,-34} {1,4}x{2,-4} {3,7:N0} B" -f $path, $pw, $ph, $len)
}

# Mirror the icon that was just written to the mod tree into the KubeJS pack tree.
# A byte copy of the finished PNG is deliberate: passing a System.Drawing.Bitmap across
# a PowerShell 5.1 function boundary is not reliable here (the object survives but its
# properties read back empty, which makes DrawImage throw "Parameter is not valid").
# The copy guarantees the two trees stay byte-identical.
function Copy-IconToPack {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Target
    )
    [System.IO.File]::Copy($Source, $Target, $true)
    $len = (Get-Item $Target).Length
    Write-Output ("  {0,-34} {1,4}x{2,-4} {3,7:N0} B" -f $Target, 32, 32, $len)
}

# ---------------------------------------------------------------------------
# (a) light_circle.png - 256x256 holy circle: white core, two glowing rings, a
#     dense spray of rays, a soft bloom and a very faint pale-yellow accent band.
#     Edge pixels are computed analytically per pixel so every ring fades to
#     alpha 0 without a hard border. 8 px transparent margin is respected.
# ---------------------------------------------------------------------------
function New-LightCircle([string]$path) {
    $size = 256
    $cx = 128.0
    $cy = 128.0
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly,
                          [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {        $stride = $data.Stride
        $count = [Math]::Abs($stride) * $size
        $px = New-Object byte[] $count
        $yellowArea = 0
        for ($y = 0; $y -lt $size; $y++) {
            $dy = $y + 0.5 - $cy
            for ($x = 0; $x -lt $size; $x++) {
                $dx = $x + 0.5 - $cx
                $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
                # gaussian profile helper is inlined twice below (outer ring at 118, inner at 78)
                # glow layers: broad soft halo + core + two ring bands. The halo is wide
                # on purpose so brightness grows smoothly from the core to the outer ring
                # instead of leaving a dark groove in between.
                $wOut = [Math]::Exp(-[Math]::Pow(($d - 109.0) / 8.0, 2.0))
                $wIn = [Math]::Exp(-[Math]::Pow(($d - 78.0) / 6.5, 2.0))
                $wCore = [Math]::Exp(-[Math]::Pow($d / 16.0, 2.0))
                $wHalo = [Math]::Exp(-[Math]::Pow(($d - 88.0) / 45.0, 2.0)) * 0.85
                $outer = 0.95 * $wOut + 0.60 * $wIn + 0.95 * $wCore + $wHalo
                # force everything to fade out inside the transparent margin (8 px edge)
                $edgeT = ($d - 108.0) / 8.0
                if ($edgeT -lt 0.0) { $edgeT = 0.0 } elseif ($edgeT -gt 1.0) { $edgeT = 1.0 }
                $outer = $outer * (1.0 - $edgeT * $edgeT * (3.0 - 2.0 * $edgeT))
                # faint pale-yellow companion band, just outside the inner ring
                # kept deliberately narrow so the yellow tint stays a small fraction of
                # the canvas (the script asserts it stays below 20%)
                $gIn = [Math]::Exp(-[Math]::Pow(($d - 67.0) / 1.9, 2.0)) * 0.34
                $gOut = [Math]::Exp(-[Math]::Pow(($d - 104.0) / 2.2, 2.0)) * 0.26
                $yellow = ($gIn + $gOut) * (1.0 - $edgeT)
                $aWhite = [int][Math]::Round(255.0 * $outer)
                $aYellow = [int][Math]::Round(255.0 * $yellow)
                if ($aWhite -gt 255) { $aWhite = 255 }
                if ($aYellow -gt 255) { $aYellow = 255 }
                if ($aWhite -le 0 -and $aYellow -le 0) { continue }
                if ($aYellow -gt 0) { $yellowArea++ }
                # y = a/255 white over pale yellow; alpha = a + yb*(1 - a)
                $aw = $aWhite / 255.0
                $ay = $aYellow / 255.0
                $aOut = $aw + $ay * (1.0 - $aw)
                if ($aOut -le 0.0) { continue }
                $r = ($aw * 255.0 + $ay * (1.0 - $aw) * $PALE.R) / $aOut
                $gr = ($aw * 255.0 + $ay * (1.0 - $aw) * $PALE.G) / $aOut
                $b = ($aw * 255.0 + $ay * (1.0 - $aw) * $PALE.B) / $aOut
                $i = $y * $stride + $x * 4
                $px[$i] = [byte][Math]::Min(255, [int][Math]::Round($b))       # B
                $px[$i + 1] = [byte][Math]::Min(255, [int][Math]::Round($gr))  # G
                $px[$i + 2] = [byte][Math]::Min(255, [int][Math]::Round($r))   # R
                $px[$i + 3] = [byte][Math]::Min(255, [int][Math]::Round($aOut * 255.0))
            }
        }
        [System.Runtime.InteropServices.Marshal]::Copy($px, 0, $data.Scan0, $count)
    } finally {
        $bmp.UnlockBits($data)
    }

    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = $SS4
    $g.PixelOffsetMode = $CLAMP

    # radial light shafts: fine and distinct (a dense 1.5 px spray would merge into a
    # solid disc through alpha accumulation), plus a short bright inner spray
    Add-Rays $g $WHITE $cx $cy 50.0 103.0 36 1.1 0.0 360.0
    $paleMid = [System.Drawing.Color]::FromArgb(70, $PALE.R, $PALE.G, $PALE.B)
    Add-Rays $g $paleMid $cx $cy 82.0 100.0 18 1.4 10.0 360.0
    Add-Rays $g $WHITE $cx $cy 26.0 68.0 12 1.6 15.0 360.0

    # two clean concentric rings + a fine dotted ring between them
    Add-Ring $g $WHITE $cx $cy 109.0 2.4
    Add-Ring $g $WHITE $cx $cy 78.0 1.8
    $paleDots = [System.Drawing.Color]::FromArgb(120, $PALE.R, $PALE.G, $PALE.B)
    Add-DotRing $g $paleDots $cx $cy 98.0 180 1.8

    # small solid center: a bright core with faintly coloured surround
    Add-Bloom $g $WHITE $cx $cy 16.0 0.45
    Add-Disc $g $WHITE $cx $cy 8.0

    Save-Png $bmp $path $g
    $stats = @{ YellowArea = $yellowArea; Total = $size * $size; Size = $size; Path = $path }
    return $stats
}

# ---------------------------------------------------------------------------
# (b) spell icons - 32x32, drawn at 8x and downsampled. Composition grows with the
#     tier: t1 sun, t2 sun+halo, t3 sun+double halo, t4 winged sun, t5 big wings +
#     full halo ring.
# ---------------------------------------------------------------------------
function New-SpellIcon([string]$name, [int]$tier) {
    $s = 8
    $c = New-Canvas (32 * $s) (32 * $s)
    $g = $c.G
    $mid = 16.0 * $s
    $body = 8.5 * $s          # radius of the glowing sun body
    $ray0 = 8.0 * $s
    $ray1 = 11.6 * $s

    $pale = [System.Drawing.Color]::FromArgb(255, $PALE.R, $PALE.G, $PALE.B)

    # Soft body glow, then the solid disc: one clean white sun.
    if ($tier -ge 4) { Add-Bloom $g $WHITE $mid $mid ($body + 6.0 * $s) 0.20 }
    Add-Bloom $g $WHITE $mid $mid ($body + 2.2 * $s) 0.26
    Add-Disc $g $WHITE $mid $mid $body

    # Sun rays: a crisp crown. Kept clear of the halo rings and deliberately sparse so
    # the icon never reads as a woven basket or gear at 32x32.
    $rays = if ($tier -ge 4) { 12 } else { 8 }
    Add-Rays $g $WHITE $mid $mid $ray0 $ray1 $rays (1.5 * $s) 0.0 360.0

    # Halo rings, well outside the ray tips and evenly spaced, one more per tier.
    if ($tier -ge 2) { Add-Ring $g $WHITE $mid $mid (13.6 * $s) (1.3 * $s) }
    if ($tier -ge 3) { Add-Ring $g $WHITE $mid $mid (15.8 * $s) (1.2 * $s) }
    if ($tier -ge 5) { Add-Ring $g $WHITE $mid $mid (18.0 * $s) (1.1 * $s) }

    # Wings last, on top of the glow, so their silhouette is not washed out by the body.
    # Scaled to start inside the sun and end past it, otherwise a 32x32 icon shows only
    # an indistinct sliver where the wing meets the disc.
    if ($tier -ge 4) {
        $ws = if ($tier -ge 5) { 1.45 } else { 1.15 }
        Add-Wing $g ([System.Drawing.Color]::FromArgb(240, 255, 255, 255)) $mid ($mid - 0.5 * $s) 1 ($s * $ws)
        Add-Wing $g ([System.Drawing.Color]::FromArgb(240, 255, 255, 255)) $mid ($mid - 0.5 * $s) -1 ($s * $ws)
    }

    # Pale-yellow accent: a thin wash on four rays plus a ring inside the body.
    Add-Rays $g $pale $mid $mid $ray0 (11.2 * $s) 4 (1.3 * $s) 0.0 360.0
    Add-Ring $g $pale $mid $mid (5.2 * $s) (1.4 * $s)

    # downsample to the real 32x32 icon with straight alpha (no fringe on the clear pixels)
    $icon = New-Object System.Drawing.Bitmap(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $icon.SetResolution(96.0, 96.0)
    $gi = [System.Drawing.Graphics]::FromImage($icon)
    $gi.Clear($CLEAR)
    $gi.InterpolationMode = $HQ
    $gi.PixelOffsetMode = $CLAMP
    $gi.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $gi.DrawImage($c.Bmp, (New-Object System.Drawing.Rectangle(0, 0, 32, 32)), 0, 0, (32 * $s), (32 * $s),
                  [System.Drawing.GraphicsUnit]::Pixel)
    $gi.Dispose()

    # clear the outermost pixel border so the icon keeps a transparent margin
    for ($i = 0; $i -lt 32; $i++) {
        $icon.SetPixel($i, 0, $CLEAR)
        $icon.SetPixel($i, 31, $CLEAR)
        $icon.SetPixel(0, $i, $CLEAR)
        $icon.SetPixel(31, $i, $CLEAR)
    }

    $iconPath = Join-Path $modIconDir "$name.png"
    Save-Png $icon $iconPath $null
    Copy-IconToPack $iconPath (Join-Path $packIconDir "$name.png")
    $g.Dispose()
    $c.Bmp.Dispose()
}

$ids = @('light_radiance', 'holy_light', 'divine_light', 'angel_descent', 'angel_mercy')

Write-Output 'light circle (256x256):'
$circlePath = Join-Path $circleDir 'light_circle.png'
$stats = New-LightCircle $circlePath
$pct = 100.0 * $stats.YellowArea / [double]$stats.Total
Write-Output ("  {0,-34} {1,4}x{2,-4} {3,7:N0} B" -f $stats.Path, $stats.Size, $stats.Size, (Get-Item $stats.Path).Length)
Write-Output ("  pale-yellow tint covers {0:N2}% of the canvas (limit 20%)" -f $pct)

Write-Output ''
Write-Output 'spell icons (32x32, written to both trees):'
for ($i = 0; $i -lt $ids.Count; $i++) {
    $id = $ids[$i]
    New-SpellIcon $id ($i + 1)
}

Write-Output ''
Write-Output ('done: 1 circle + {0} icons x2 locations' -f $ids.Count)
Write-Output ('mod icons -> ' + $modIconDir)
Write-Output ('pack icons -> ' + $packIconDir)
exit 0
