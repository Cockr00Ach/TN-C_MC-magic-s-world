# Icons for the light WINGS chain (light_flight / light_swift_flight / light_wingspan).
#
#   > powershell -NoProfile -ExecutionPolicy Bypass -File tools/gen_wings_icons.ps1
#
# Writes 32x32 RGBA icons into BOTH trees (jar side + pack kubejs side). White wings with a hint
# of pale yellow and a transparent border; higher tiers get longer feathers and an extra halo so
# the three icons read differently at a glance (see docs for the chain itself).
#
# ASCII only on purpose (docs: tools/*.ps1 must stay pure ASCII) - the pack folder name has
# non-ASCII characters, so it is DISCOVERED instead of hardcoded.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$RepoRoot = Split-Path $PSScriptRoot -Parent
$Dirs = New-Object System.Collections.Generic.List[string]
$Dirs.Add((Join-Path $RepoRoot 'src\main\resources\assets\tnc\textures\spell'))
foreach ($pack in (Get-ChildItem (Join-Path $RepoRoot 'modpack') -Directory -ErrorAction SilentlyContinue)) {
    $candidate = Join-Path $pack.FullName 'kubejs\assets\tnc\textures\spell'
    if (Test-Path $candidate) { $Dirs.Add($candidate) }
}

$White = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
$Pale = [System.Drawing.Color]::FromArgb(255, 255, 249, 214)

function New-Pen($color, [int]$alpha, [double]$width) {
    $p = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb($alpha, $color.R, $color.G, $color.B), [float]$width)
    $p.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $p.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    return $p
}

function Draw-WingIcon([string]$id, [int]$tier) {
    $size = 32
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $cx = 16.0
    $cy = 19.0

    $span = 8.0 + $tier * 2.0
    $feathers = 2 + $tier
    for ($side = -1; $side -le 1; $side += 2) {
        for ($k = 0; $k -lt $feathers; $k++) {
            $alpha = 250 - $k * 30
            $pen = New-Pen $White $alpha (3.0 - $k * 0.4)
            $x0 = $cx + $side * 2.0
            $y0 = $cy - 3.0 + $k * 2.6
            $x1 = $cx + $side * ($span - $k * 1.2)
            $y1 = $cy - 8.0 - $tier * 0.8 + $k * 3.2
            $g.DrawLine($pen, [float]$x0, [float]$y0, [float]$x1, [float]$y1)
            $pen.Dispose()
        }
    }
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(235, $Pale.R, $Pale.G, $Pale.B))
    $g.FillEllipse($brush, [float]($cx - 2.5), [float]($cy - 6.0), 5, 5)
    $brush.Dispose()

    if ($tier -ge 2) {
        $pen = New-Pen $White 235 2.0
        $g.DrawEllipse($pen, [float]($cx - 7.0), 3.0, 14, 6)
        $pen.Dispose()
    }
    if ($tier -ge 3) {
        $pen = New-Pen $Pale 220 1.5
        $g.DrawEllipse($pen, [float]($cx - 10.0), 0.5, 20, 8)
        $pen.Dispose()
    }
    $g.Dispose()

    foreach ($dir in $Dirs) {
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        $bmp.Save((Join-Path $dir ($id + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
    }
    $bmp.Dispose()
    Write-Host ("{0,-24} 32x32 (t{1}) -> {2} dir(s)" -f ($id + '.png'), $tier, $Dirs.Count)
}

Draw-WingIcon 'light_flight' 1
Draw-WingIcon 'light_swift_flight' 2
Draw-WingIcon 'light_wingspan' 3
Write-Host 'wings icons generated'
exit 0
