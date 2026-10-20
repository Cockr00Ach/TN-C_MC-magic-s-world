param([string]$Manifest = 'art/prompts/element_boss_blocks_20261018.json')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceManifest = Get-Content -LiteralPath (Join-Path $projectRoot $Manifest) -Raw -Encoding utf8 | ConvertFrom-Json
$textureDir = Join-Path $projectRoot 'src/main/resources/assets/tnc/textures/block'
$atlasDir = Join-Path $projectRoot 'art/generated/element_boss_blocks'
New-Item -ItemType Directory -Force -Path $textureDir, $atlasDir | Out-Null
foreach ($asset in $sourceManifest.assets) {
    $sourcePath = $asset.source
    $usingCompactSource = -not (Test-Path -LiteralPath $sourcePath)
    if ($usingCompactSource) { $sourcePath = Join-Path $projectRoot $asset.atlas }
    $sourceImage = [System.Drawing.Bitmap]::new($sourcePath)
    try {
        if ([Math]::Abs($sourceImage.Width / [double]$sourceImage.Height - 2) -gt 0.02) { throw "Atlas must be four columns by two rows: $($asset.id)" }
        for ($tile = 0; $tile -lt 9; $tile++) {
            if ($tile -eq 8 -and $usingCompactSource) { continue }
            # Tile 8 preserves a compact complete atlas; 0..7 import individual cube textures.
            $outputWidth = if ($tile -eq 8) { 1024 } else { 128 }
            $outputHeight = if ($tile -eq 8) { 512 } else { 128 }
            $bitmap = [System.Drawing.Bitmap]::new($outputWidth, $outputHeight, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
            try {
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
                $sx = if ($tile -eq 8) { 0.0 } else { ($tile % 4) * $sourceImage.Width / 4.0 }
                $sy = if ($tile -eq 8) { 0.0 } else { [Math]::Floor($tile / 4) * $sourceImage.Height / 2.0 }
                $sw = if ($tile -eq 8) { $sourceImage.Width } else { $sourceImage.Width / 4.0 }
                $sh = if ($tile -eq 8) { $sourceImage.Height } else { $sourceImage.Height / 2.0 }
                $graphics.DrawImage($sourceImage, [System.Drawing.Rectangle]::new(0, 0, $outputWidth, $outputHeight), [single]$sx, [single]$sy, [single]$sw, [single]$sh, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
                $fileName = if ($tile -eq 8) { Join-Path $atlasDir "$($asset.id)_atlas.png" } else { Join-Path $textureDir "$($asset.id)_boss_runestone_$($tile + 1).png" }
                $bitmap.Save($fileName, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally { $attributes.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
        }
    } finally { $sourceImage.Dispose() }
}
Write-Output 'Imported six atlases and 48 opaque 128x128 Minecraft textures.'
