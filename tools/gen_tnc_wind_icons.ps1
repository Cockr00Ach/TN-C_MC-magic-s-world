# Wind chain 1 icons (16x16): a swirl, plus speed lines that grow with the tier.
# Tier 5 gets a halo (the "wind god" tier).
#
# ASCII ONLY. tools/*.ps1 gets read as GBK by Windows PowerShell: one Chinese
# character can swallow a newline and break the whole script. This cost two
# failed runs already - keep every tool script pure ASCII.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$pack = Get-ChildItem (Join-Path $root 'modpack') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'config\openloader\resources\TN-C') } |
        Select-Object -First 1
if (-not $pack) { throw 'TN-C resource pack folder not found' }
$iconDir = Join-Path $pack.FullName 'config\openloader\resources\TN-C\assets\tnc\textures\spell'
New-Item -ItemType Directory -Force -Path $iconDir | Out-Null

$DEEP = [System.Drawing.Color]::FromArgb(255, 0x2E, 0x6E, 0x8A)
$MID = [System.Drawing.Color]::FromArgb(255, 0x62, 0xB4, 0xD0)
$LITE = [System.Drawing.Color]::FromArgb(255, 0xD8, 0xF2, 0xFA)
$WHITE = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xFF, 0xFF)
$CLEAR = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)

function Draw-Icon($name, $tier) {
    $bmp = New-Object System.Drawing.Bitmap(16, 16)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear($CLEAR)
    function Px($x, $y, $c) {
        if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) {
            $g.FillRectangle((New-Object System.Drawing.SolidBrush $c), $x, $y, 1, 1)
        }
    }
    # swirl: a loose spiral around (8,8)
    for ($a = 0; $a -lt 720; $a += 12) {
        $rad = $a * [Math]::PI / 180.0
        $r = 1.2 + ($a / 720.0) * (3.0 + $tier * 0.4)
        $x = 8 + [int][Math]::Round($r * [Math]::Cos($rad))
        $y = 8 + [int][Math]::Round($r * [Math]::Sin($rad))
        $c = if ($r -lt 3) { $WHITE } elseif ($r -lt 4.5) { $LITE } else { $MID }
        Px $x $y $c
        Px ($x + 1) $y $c
    }
    # speed lines to the left, more of them at higher tiers
    $lines = @{ 1 = 0; 2 = 1; 3 = 2; 4 = 3; 5 = 3 }
    $n = $lines[[int]$tier]
    for ($i = 0; $i -lt $n; $i++) {
        $y = 3 + $i * 5
        $len = 5 - $i
        for ($x = 0; $x -lt $len; $x++) { Px $x $y $LITE }
        Px $len $y $MID
    }
    if ($tier -ge 5) {                       # halo for the wind god tier
        for ($x = 3; $x -le 12; $x++) { Px $x 1 $DEEP }
        Px 2 2 $DEEP; Px 13 2 $DEEP
    }
    $g.Dispose()
    $p = Join-Path $iconDir "$name.png"
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("  {0,-24} {1,5:N0} B" -f "$name.png", (Get-Item $p).Length)
}

Draw-Icon 'wind_field' 1
Draw-Icon 'wind_speed' 2
Draw-Icon 'greater_wind_speed' 3
Draw-Icon 'super_wind_speed' 4
Draw-Icon 'wind_god_descent' 5
Write-Output ''
Write-Output ('done -> ' + $iconDir)