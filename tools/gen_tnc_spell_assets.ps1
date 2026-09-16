# Generates the 10 new TN-C spell icons (16x16), the thunder-orb projectile
# texture and its model. All ASCII only (tools/*.ps1 convention).
#
# Where things go:
#   icons  -> <pack>\config\openloader\resources\TN-C\assets\tnc\textures\spell\<id>.png
#             (the existing 5 TN-C icons already live there)
#   model  -> ...\assets\tnc\models\projectile\thunder_orb.json
#   texture-> ...\assets\tnc\textures\projectile\thunder_orb.png
#
# Style: same lightning bolt as spark.png (loaded and re-used as a mask) plus a
# per-spell decoration, so the two new chains read as "the same family".

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$pack = Get-ChildItem (Join-Path $root 'modpack') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'config\openloader\resources\TN-C') } |
        Select-Object -First 1
if (-not $pack) { throw 'TN-C resource pack folder not found' }
$assets = Join-Path $pack.FullName 'config\openloader\resources\TN-C\assets\tnc'
$iconDir = Join-Path $assets 'textures\spell'
$projTexDir = Join-Path $assets 'textures\projectile'
$projModelDir = Join-Path $assets 'models\projectile'
foreach ($d in @($iconDir, $projTexDir, $projModelDir)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

# ---- palette ---------------------------------------------------------------
$DARK  = [System.Drawing.Color]::FromArgb(255, 0x6B, 0x50, 0x0C)   # bolt outline
$GOLD  = [System.Drawing.Color]::FromArgb(255, 0xD9, 0xA6, 0x2B)   # bolt body
$LIGHT = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xF6, 0xD8)   # bolt highlight
$PUR   = [System.Drawing.Color]::FromArgb(255, 0x7C, 0x5C, 0xE0)   # lightning accent
$PULL  = [System.Drawing.Color]::FromArgb(255, 0xC9, 0xB8, 0xFF)   # accent light
$CLEAR = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)

# ---- the bolt mask, copied from the existing spark icon --------------------
$bolt = New-Object 'bool[,]' 16, 16
$sparkPath = Join-Path $iconDir 'spark.png'
if (Test-Path $sparkPath) {
    $spark = New-Object System.Drawing.Bitmap($sparkPath)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $bolt[$x, $y] = ($spark.GetPixel($x, $y).A -gt 100)
        }
    }
    $spark.Dispose()
    Write-Output '  bolt mask: loaded from spark.png'
} else {
    # fallback: a hand-made zigzag
    $zig = @(7, 6, 5, 4, 3, 4, 5, 6, 7, 8, 9, 10, 9, 10, 11, 12)
    for ($y = 0; $y -lt 16; $y++) {
        $x = $zig[$y]
        $bolt[$x, $y] = $true
        $bolt[$x + 1, $y] = $true
    }
    Write-Output '  bolt mask: fallback zigzag'
}

function New-Icon {
    $bmp = New-Object System.Drawing.Bitmap(16, 16)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear($CLEAR)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    return @($bmp, $g)
}

function Px($g, $x, $y, $c) {
    if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) {
        $g.FillRectangle((New-Object System.Drawing.SolidBrush $c), $x, $y, 1, 1)
    }
}

# draws the 16x16 bolt scaled by $f, anchored at ($ox,$oy)
function Draw-Bolt($g, $ox, $oy, $f) {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            if (-not $bolt[$x, $y]) { continue }
            $tx = [int][Math]::Floor($ox + $x * $f)
            $ty = [int][Math]::Floor($oy + $y * $f)
            $w = [Math]::Max(1, [int][Math]::Ceiling($f))
            $g.FillRectangle((New-Object System.Drawing.SolidBrush $GOLD), $tx, $ty, $w, $w)
        }
    }
}

# a filled circle (orb body) with a highlight
function Draw-Orb($g, $cx, $cy, $r) {
    for ($y = -$r; $y -le $r; $y++) {
        for ($x = -$r; $x -le $r; $x++) {
            if ($x * $x + $y * $y -gt $r * $r) { continue }
            $edge = ($x * $x + $y * $y -gt ($r - 1) * ($r - 1))
            $c = if ($edge) { $PUR } else { $PULL }
            Px $g ($cx + $x) ($cy + $y) $c
        }
    }
    Px $g ($cx - [int]($r / 2)) ($cy - [int]($r / 2)) $LIGHT
}

function Draw-Ring($g, $cx, $cy, $r, $c) {
    for ($a = 0; $a -lt 360; $a += 12) {
        $rad = $a * [Math]::PI / 180.0
        Px $g ($cx + [int][Math]::Round($r * [Math]::Cos($rad))) ($cy + [int][Math]::Round($r * [Math]::Sin($rad))) $c
    }
}

function Save-Icon($name, $g, $bmp) {
    $g.Dispose()
    $path = Join-Path $iconDir "$name.png"
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("  icon {0,-26} {1,5:N0} B" -f "$name.png", (Get-Item $path).Length)
}

# ---------------------------------------------------------------------------
#  orb chain: the orb grows, then gets a ring, then spikes, then a rain arrow
# ---------------------------------------------------------------------------
$orbTiers = @(
    @{ n = 'thunder_orb';           r = 4; ring = $false; spikes = $false; rain = $false },
    @{ n = 'great_thunder_orb';     r = 5; ring = $false; spikes = $false; rain = $false },
    @{ n = 'orbiting_thunder_orb';  r = 4; ring = $true;  spikes = $false; rain = $false },
    @{ n = 'explosive_thunder_orb'; r = 4; ring = $false; spikes = $true;  rain = $false },
    @{ n = 'cataclysm_thunder_orb'; r = 5; ring = $true;  spikes = $true;  rain = $true }
)
foreach ($t in $orbTiers) {
    $pair = New-Icon
    $bmp = $pair[0]; $g = $pair[1]
    if ($t.ring) { Draw-Ring $g 8 8 ($t.r + 3) $PULL }
    if ($t.spikes) {
        foreach ($d in @(@(0, -1), @(0, 1), @(-1, 0), @(1, 0), @(-1, -1), @(1, -1), @(-1, 1), @(1, 1))) {
            $sx = 8 + $d[0] * ($t.r + 2)
            $sy = 8 + $d[1] * ($t.r + 2)
            Px $g $sx $sy $GOLD
            Px $g (8 + $d[0] * ($t.r + 3)) (8 + $d[1] * ($t.r + 3)) $DARK
        }
    }
    Draw-Orb $g 8 8 $t.r
    # a small gold bolt inside the orb, so the icon still reads as "lightning"
    Draw-Bolt $g 4 4 0.5
    if ($t.rain) {
        # three chevrons above = "falling from the sky"
        for ($i = 0; $i -lt 3; $i++) {
            Px $g (6 + $i) (1) $LIGHT
            Px $g (5 + $i) (2) $GOLD
            Px $g (7 + $i) (2) $GOLD
        }
    }
    Save-Icon $t.n $g $bmp
}

# ---------------------------------------------------------------------------
#  speed chain: bolt plus a speed/teleport/wind/recharge/crown motif
# ---------------------------------------------------------------------------
function Speed-Icon($name, $decor) {
    $pair = New-Icon
    $bmp = $pair[0]; $g = $pair[1]
    switch ($decor) {
        'chevrons' {
            foreach ($row in @(4, 7, 10)) {
                Px $g 11 $row $PULL; Px $g 12 ($row - 1) $PULL; Px $g 12 ($row + 1) $PULL
            }
        }
        'blink' {
            for ($i = 0; $i -lt 6; $i++) { Px $g (1 + $i) (14 - $i) $PULL }
            Px $g 13 3 $LIGHT; Px $g 14 4 $LIGHT
        }
        'wind' {
            foreach ($row in @(3, 6, 9, 12)) {
                for ($i = 0; $i -lt 4; $i++) { Px $g (11 + $i) $row $PULL }
            }
        }
        'recharge' {
            Draw-Ring $g 8 8 7 $PULL
            Px $g 8 1 $LIGHT; Px $g 7 2 $LIGHT; Px $g 9 2 $LIGHT
        }
        'crown' {
            foreach ($x in @(4, 6, 8, 10, 12)) { Px $g $x 2 $GOLD }
            Px $g 5 3 $GOLD; Px $g 7 3 $GOLD; Px $g 9 3 $GOLD; Px $g 11 3 $GOLD
            Px $g 8 0 $LIGHT
        }
    }
    Draw-Bolt $g 0 0 1.0
    Save-Icon $name $g $bmp
}
Speed-Icon 'lightning_haste'    'chevrons'
Speed-Icon 'lightning_blink'    'blink'
Speed-Icon 'lightning_wind'     'wind'
Speed-Icon 'lightning_recharge' 'recharge'
Speed-Icon 'lightning_ascension' 'crown'

# ---------------------------------------------------------------------------
#  thunder orb projectile: texture + model
# ---------------------------------------------------------------------------
$tex = New-Object System.Drawing.Bitmap(16, 16)
$tg = [System.Drawing.Graphics]::FromImage($tex)
$tg.Clear($CLEAR)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $dx = $x - 7.5; $dy = $y - 7.5
        $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
        if ($d -lt 3.0) { Px $tg $x $y $LIGHT }
        elseif ($d -lt 5.5) { Px $tg $x $y $PULL }
        elseif ($d -lt 7.0) { Px $tg $x $y $PUR }
    }
}
$tg.Dispose()
$texPath = Join-Path $projTexDir 'thunder_orb.png'
$tex.Save($texPath, [System.Drawing.Imaging.ImageFormat]::Png)
$tex.Dispose()
Write-Output ("  texture {0,-24} {1,5:N0} B" -f 'projectile/thunder_orb.png', (Get-Item $texPath).Length)

# 6x6x6 cube, all faces sampling the middle of the texture
$faces = @{}
foreach ($f in @('north', 'east', 'south', 'west', 'up', 'down')) {
    $uv = if ($f -eq 'up' -or $f -eq 'down') { '[4, 4, 12, 12]' } else { '[4, 4, 12, 12]' }
    $faces[$f] = "{ `"uv`": $uv, `"texture`": `"#0`" }"
}
$model = @"
{
  "credit": "TN-C",
  "texture_size": [16, 16],
  "textures": { "0": "tnc:projectile/thunder_orb" },
  "elements": [
    {
      "name": "orb",
      "from": [5, 5, 5],
      "to": [11, 11, 11],
      "faces": {
        "north": $(($faces['north'])),
        "east": $(($faces['east'])),
        "south": $(($faces['south'])),
        "west": $(($faces['west'])),
        "up": $(($faces['up'])),
        "down": $(($faces['down']))
      }
    }
  ],
  "display": {}
}
"@
$modelPath = Join-Path $projModelDir 'thunder_orb.json'
[System.IO.File]::WriteAllText($modelPath, $model, (New-Object System.Text.UTF8Encoding($false)))
$null = ConvertFrom-Json $model
Write-Output ("  model   {0,-24} {1,5:N0} B" -f 'projectile/thunder_orb.json', (Get-Item $modelPath).Length)

Write-Output ''
Write-Output ('done -> ' + $iconDir)
