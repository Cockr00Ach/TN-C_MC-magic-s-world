# ---------------------------------------------------------------------------
#  Generates a spawn-egg texture for a TN-C NPC.
#
#  Why each NPC needs one: ForgeSpawnEggItem renders with vanilla's
#  item/template_spawn_egg, which needs a 16x16 egg texture; the two tint colours
#  come from the item registration. Without the texture the egg shows as the
#  purple/black missing model (that is exactly why "Self" could not be found in
#  the creative tab the first time).
#
#  Usage:  -Name cava      -> textures/item/cava_spawn_egg.png
#
#  NOTE: pure ASCII on purpose (no BOM needed on a GBK host).
# ---------------------------------------------------------------------------
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Out = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not $Out) {
    $repo = Split-Path $PSScriptRoot -Parent
    $Out = Join-Path $repo ("src\main\resources\assets\tnc\textures\item\{0}_spawn_egg.png" -f $Name)
}

function C([int]$v, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $v, $v, $v) }

$bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

# '#' shell (tinted base) / '.' spots (tinted spots) / 'o' outline / ' ' transparent
$mask = @(
    '                ',
    '      oooo      ',
    '    oo####oo    ',
    '   o########o   ',
    '  o##..####..o  ',
    '  o#....##....o ',
    ' o##...####...o ',
    ' o####..##..###o',
    ' o#############o',
    ' o##..####..###o',
    ' o#....##....##o',
    '  o##..####..##o',
    '  o###########o ',
    '   oo#######oo  ',
    '     ooooooo    ',
    '                '
)

for ($y = 0; $y -lt 16; $y++) {
    $row = $mask[$y]
    for ($x = 0; $x -lt 16; $x++) {
        $ch = if ($x -lt $row.Length) { $row[$x] } else { ' ' }
        switch ($ch) {
            '#' { $bmp.SetPixel($x, $y, (C 235)) }
            '.' { $bmp.SetPixel($x, $y, (C 140)) }
            'o' { $bmp.SetPixel($x, $y, (C 60)) }
            default { $bmp.SetPixel($x, $y, (C 255 0)) }
        }
    }
}

$dir = Split-Path $Out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ("saved: {0}  ({1} bytes)" -f $Out, (Get-Item $Out).Length)
