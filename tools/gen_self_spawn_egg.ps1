# ---------------------------------------------------------------------------
#  Generates the spawn-egg texture for TN-C's "Self" NPC.
#
#  Why: ForgeSpawnEggItem renders with vanilla's item/template_spawn_egg, which
#  needs a 16x16 egg-shaped texture and applies TWO tints (base + spots) at
#  runtime. Without this file the egg renders as the purple/black missing model
#  (that is exactly why the user could not find it in the creative tab).
#
#  Output: 16x16 PNG. The egg body is drawn in light grey so the runtime tint
#  colours read clearly; vanilla uses the same trick (white body + tints).
#
#  NOTE: pure ASCII on purpose (no BOM needed on a GBK host).
# ---------------------------------------------------------------------------
param(
    [string]$Out = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not $Out) {
    $repo = Split-Path $PSScriptRoot -Parent
    $Out = Join-Path $repo 'src\main\resources\assets\tnc\textures\item\self_spawn_egg.png'
}

function C([int]$v, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $v, $v, $v) }

$bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

# ASCII mask: '#' = egg shell (tinted base), '.' = spots (tinted spots),
#             ' ' = transparent, 'o' = dark outline
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
            '#' { $bmp.SetPixel($x, $y, (C 235)) }   # shell
            '.' { $bmp.SetPixel($x, $y, (C 140)) }   # spots
            'o' { $bmp.SetPixel($x, $y, (C 60)) }    # outline
            default { $bmp.SetPixel($x, $y, (C 255 0)) }
        }
    }
}

$dir = Split-Path $Out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ("saved: {0}  ({1} bytes)" -f $Out, (Get-Item $Out).Length)
